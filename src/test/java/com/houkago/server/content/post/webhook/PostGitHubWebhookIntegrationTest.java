package com.houkago.server.content.post.webhook;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@WebMvcTest(controllers = PostGitHubWebhookController.class, properties = {
		"houkago.webhook.github.posts.enabled=true",
		"houkago.webhook.github.posts.secret=synthetic-integration-secret",
		"houkago.webhook.github.posts.repository-full-name=example/houkago.posts",
		"houkago.webhook.github.posts.ref=refs/heads/main"
})
@ExtendWith(OutputCaptureExtension.class)
@Import(PostGitHubWebhookConfiguration.class)
class PostGitHubWebhookIntegrationTest {

	private static final String SECRET = "synthetic-integration-secret";
	private static final String COMMIT_SHA = "0123456789abcdef0123456789abcdef01234567";

	@TempDir
	static Path spoolRoot;

	@DynamicPropertySource
	static void spoolProperties(DynamicPropertyRegistry registry) {
		registry.add("houkago.webhook.github.posts.spool-root", spoolRoot::toString);
	}

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@Test
	void signedPushReturnsAcceptedAndPublishesJob(CapturedOutput output) throws Exception {
		String deliveryId = "123e4567-e89b-42d3-a456-426614174001";
		byte[] body = payload("raw-body-sentinel");

		mockMvc.perform(post("/internal/webhooks/github/posts")
				.contentType(MediaType.APPLICATION_JSON)
				.header("X-Hub-Signature-256", signature(body))
				.header("X-GitHub-Delivery", deliveryId)
				.header("X-GitHub-Event", "push")
				.content(body))
				.andExpect(status().isAccepted());

		Path jobPath = spoolRoot.resolve("incoming").resolve(deliveryId + ".json");
		JsonNode job = objectMapper.readTree(jobPath.toFile());
		assertThat(job.path("deliveryId").asText()).isEqualTo(deliveryId);
		assertThat(job.path("commitSha").asText()).isEqualTo(COMMIT_SHA);
		assertThat(job.path("receivedAt").asText()).isNotBlank();
		assertThat(output).contains("status=ACCEPTED", deliveryId, COMMIT_SHA)
				.doesNotContain(SECRET, "raw-body-sentinel", "X-Hub-Signature-256");
	}

	@Test
	void duplicateDeliveryReturnsSuccessAndKeepsSingleJob() throws Exception {
		String deliveryId = "123e4567-e89b-42d3-a456-426614174002";
		byte[] body = payload("duplicate-sentinel");

		mockMvc.perform(request(deliveryId, body)).andExpect(status().isAccepted());
		mockMvc.perform(request(deliveryId, body)).andExpect(status().isOk());

		Path jobPath = spoolRoot.resolve("incoming").resolve(deliveryId + ".json");
		assertThat(jobPath).isRegularFile();
		try (var files = Files.list(jobPath.getParent())) {
			assertThat(files.filter(path -> path.getFileName().toString().startsWith(deliveryId))).hasSize(1);
		}
	}

	@Test
	void invalidSignatureReturnsUnauthorizedAndDoesNotPublishJob() throws Exception {
		String deliveryId = "123e4567-e89b-42d3-a456-426614174003";
		byte[] body = payload("invalid-signature-sentinel");

		mockMvc.perform(post("/internal/webhooks/github/posts")
				.contentType(MediaType.APPLICATION_JSON)
				.header("X-Hub-Signature-256", "sha256=" + "0".repeat(64))
				.header("X-GitHub-Delivery", deliveryId)
				.header("X-GitHub-Event", "push")
				.content(body))
				.andExpect(status().isUnauthorized());

		assertThat(spoolRoot.resolve("incoming").resolve(deliveryId + ".json")).doesNotExist();
	}

	private static org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request(
			String deliveryId,
			byte[] body) {
		return post("/internal/webhooks/github/posts")
				.contentType(MediaType.APPLICATION_JSON)
				.header("X-Hub-Signature-256", signature(body))
				.header("X-GitHub-Delivery", deliveryId)
				.header("X-GitHub-Event", "push")
				.content(body);
	}

	private static byte[] payload(String ignoredRawField) {
		return ("""
				{
				  "repository": {"full_name": "example/houkago.posts"},
				  "ref": "refs/heads/main",
				  "forced": false,
				  "after": "%s",
				  "sender": {"login": "%s"}
				}
				""").formatted(COMMIT_SHA, ignoredRawField).getBytes(StandardCharsets.UTF_8);
	}

	private static String signature(byte[] body) {
		try {
			Mac mac = Mac.getInstance("HmacSHA256");
			mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
			return "sha256=" + HexFormat.of().formatHex(mac.doFinal(body));
		}
		catch (NoSuchAlgorithmException | InvalidKeyException exception) {
			throw new IllegalStateException(exception);
		}
	}
}
