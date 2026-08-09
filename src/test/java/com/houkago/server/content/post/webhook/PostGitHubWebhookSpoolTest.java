package com.houkago.server.content.post.webhook;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

class PostGitHubWebhookSpoolTest {

	private static final String DELIVERY_ID = "123e4567-e89b-42d3-a456-426614174000";
	private static final String COMMIT_SHA = "0123456789abcdef0123456789abcdef01234567";
	private static final Instant RECEIVED_AT = Instant.parse("2026-08-09T00:00:00Z");

	@TempDir
	Path spoolRoot;

	private ObjectMapper objectMapper;
	private PostGitHubWebhookSpool spool;

	@BeforeEach
	void setUp() {
		objectMapper = new ObjectMapper();
		PostGitHubWebhookProperties properties = new PostGitHubWebhookProperties();
		properties.setSpoolRoot(spoolRoot.toString());
		spool = new PostGitHubWebhookSpool(
				objectMapper,
				Clock.fixed(RECEIVED_AT, ZoneOffset.UTC),
				properties);
	}

	@Test
	void publishesMinimalJobJsonIntoIncomingDirectory() throws Exception {
		PostGitHubWebhookResult result = spool.publish(request());

		assertThat(result.status()).isEqualTo(PostGitHubWebhookResult.Status.ACCEPTED);
		Path jobPath = spoolRoot.resolve("incoming").resolve(DELIVERY_ID + ".json");
		assertThat(jobPath).isRegularFile();

		JsonNode job = objectMapper.readTree(jobPath.toFile());
		assertThat(job.path("deliveryId").asText()).isEqualTo(DELIVERY_ID);
		assertThat(job.path("commitSha").asText()).isEqualTo(COMMIT_SHA);
		assertThat(job.path("receivedAt").asText()).isEqualTo(RECEIVED_AT.toString());
		assertThat(job.size()).isEqualTo(3);
	}

	@Test
	void duplicateDeliveryDoesNotOverwriteOrCreateAnotherJob() throws Exception {
		PostGitHubWebhookResult first = spool.publish(request());
		Path jobPath = spoolRoot.resolve("incoming").resolve(DELIVERY_ID + ".json");
		byte[] originalJob = Files.readAllBytes(jobPath);

		PostGitHubWebhookResult duplicate = spool.publish(request());

		assertThat(first.status()).isEqualTo(PostGitHubWebhookResult.Status.ACCEPTED);
		assertThat(duplicate.status()).isEqualTo(PostGitHubWebhookResult.Status.DUPLICATE);
		assertThat(Files.readAllBytes(jobPath)).isEqualTo(originalJob);
		assertThat(listIncomingFiles()).containsExactly(jobPath);
	}

	@Test
	void deliveryInAnyWorkerStateIsDuplicate() throws Exception {
		for (String state : List.of("processing", "succeeded", "failed")) {
			Path stateDirectory = spoolRoot.resolve(state);
			Files.createDirectories(stateDirectory);
			Path existingJob = stateDirectory.resolve(DELIVERY_ID + ".json");
			Files.writeString(existingJob, "existing " + state);

			PostGitHubWebhookResult result = spool.publish(request());

			assertThat(result.status()).isEqualTo(PostGitHubWebhookResult.Status.DUPLICATE);
			assertThat(Files.readString(existingJob)).isEqualTo("existing " + state);
			assertThat(spoolRoot.resolve("incoming").resolve(DELIVERY_ID + ".json")).doesNotExist();
			Files.delete(existingJob);
		}
	}

	@Test
	void successfulPublishLeavesNoTemporaryFile() throws Exception {
		spool.publish(request());

		assertThat(listIncomingFiles())
				.extracting(path -> path.getFileName().toString())
				.containsExactly(DELIVERY_ID + ".json");
		try (Stream<Path> temporaryFiles = Files.list(spoolRoot.resolve(".tmp"))) {
			assertThat(temporaryFiles).isEmpty();
		}
	}

	private PostGitHubWebhookRequest request() {
		return new PostGitHubWebhookRequest(DELIVERY_ID, COMMIT_SHA);
	}

	private List<Path> listIncomingFiles() throws IOException {
		try (Stream<Path> files = Files.list(spoolRoot.resolve("incoming"))) {
			return files.sorted().toList();
		}
	}
}
