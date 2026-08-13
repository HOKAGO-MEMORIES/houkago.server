package com.houkago.server.deployment.webhook;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

class BackendDeploySpoolTest {

	private static final String DELIVERY_ID = "123e4567-e89b-42d3-a456-426614174010";
	private static final String REVISION = "0123456789abcdef0123456789abcdef01234567";
	private static final String IMAGE = "ghcr.io/example/houkago.server@sha256:" + "a".repeat(64);
	private static final Instant RECEIVED_AT = Instant.parse("2026-08-13T00:00:00Z");

	@TempDir
	Path spoolRoot;

	private ObjectMapper objectMapper;
	private BackendDeploySpool spool;

	@BeforeEach
	void setUp() {
		objectMapper = new ObjectMapper();
		BackendDeployWebhookProperties properties = new BackendDeployWebhookProperties();
		properties.setSpoolRoot(spoolRoot.toString());
		properties.setWorkerGracePeriod(Duration.ofSeconds(7));
		spool = new BackendDeploySpool(
				objectMapper,
				Clock.fixed(RECEIVED_AT, ZoneOffset.UTC),
				properties);
	}

	@Test
	void publishesMinimalAtomicJobWithResponseGraceBoundary() throws Exception {
		BackendDeployResult result = spool.publish(request());

		assertThat(result.status()).isEqualTo(BackendDeployResult.Status.ACCEPTED);
		Path jobPath = spoolRoot.resolve("incoming").resolve(DELIVERY_ID + ".json");
		JsonNode job = objectMapper.readTree(jobPath.toFile());
		assertThat(job.path("deliveryId").asText()).isEqualTo(DELIVERY_ID);
		assertThat(job.path("revision").asText()).isEqualTo(REVISION);
		assertThat(job.path("image").asText()).isEqualTo(IMAGE);
		assertThat(job.path("receivedAt").asText()).isEqualTo(RECEIVED_AT.toString());
		assertThat(job.path("notBefore").asText()).isEqualTo(RECEIVED_AT.plusSeconds(7).toString());
		assertThat(job.size()).isEqualTo(5);
		assertThat(Files.getPosixFilePermissions(jobPath)).containsExactlyInAnyOrder(
				PosixFilePermission.OWNER_READ,
				PosixFilePermission.OWNER_WRITE,
				PosixFilePermission.GROUP_READ);
		try (var temporaryFiles = Files.list(spoolRoot.resolve(".tmp"))) {
			assertThat(temporaryFiles).isEmpty();
		}
	}

	@Test
	void deliveryInAnyWorkerStateIsDuplicate() throws Exception {
		for (String state : List.of("incoming", "processing", "succeeded", "failed")) {
			Path stateDirectory = spoolRoot.resolve(state);
			Files.createDirectories(stateDirectory);
			Path existing = stateDirectory.resolve(DELIVERY_ID + ".json");
			Files.writeString(existing, "existing");

			BackendDeployResult result = spool.publish(request());

			assertThat(result.status()).isEqualTo(BackendDeployResult.Status.DUPLICATE);
			assertThat(Files.readString(existing)).isEqualTo("existing");
			Files.delete(existing);
		}
	}

	private static BackendDeployRequest request() {
		return new BackendDeployRequest(DELIVERY_ID, REVISION, IMAGE);
	}
}
