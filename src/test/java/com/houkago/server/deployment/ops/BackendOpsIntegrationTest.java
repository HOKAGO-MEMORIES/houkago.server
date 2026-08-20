package com.houkago.server.deployment.ops;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;

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

@WebMvcTest(controllers = BackendOpsController.class, properties = {
		"houkago.ops.webhook.enabled=true",
		"houkago.ops.webhook.secret=synthetic-integration-secret",
		"houkago.ops.webhook.worker-grace-period=3s"
})
@ExtendWith(OutputCaptureExtension.class)
@Import(BackendOpsWebhookConfiguration.class)
class BackendOpsIntegrationTest {

	private static final String SECRET = "synthetic-integration-secret";
	private static final String REVISION = "0123456789abcdef0123456789abcdef01234567";

	@TempDir
	static Path spoolRoot;

	@DynamicPropertySource
	static void spoolProperties(DynamicPropertyRegistry registry) {
		registry.add("houkago.ops.webhook.spool-root", spoolRoot::toString);
	}

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@Test
	void validRequestReturnsAcceptedBeforeWorkerBoundary(CapturedOutput output) throws Exception {
		String deliveryId = "123e4567-e89b-42d3-a456-426614174120";

		mockMvc.perform(request(deliveryId, SECRET)).andExpect(status().isAccepted());

		JsonNode job = objectMapper.readTree(
				spoolRoot.resolve("incoming").resolve(deliveryId + ".json").toFile());
		assertThat(Duration.between(
				Instant.parse(job.path("receivedAt").asText()),
				Instant.parse(job.path("notBefore").asText())))
				.isEqualTo(Duration.ofSeconds(3));
		assertThat(output).contains("status=ACCEPTED", deliveryId, REVISION)
				.doesNotContain(SECRET, "Authorization");
	}

	@Test
	void duplicateReturnsOkAndAuthenticationFailureDoesNotPublish() throws Exception {
		String acceptedId = "123e4567-e89b-42d3-a456-426614174121";
		mockMvc.perform(request(acceptedId, SECRET)).andExpect(status().isAccepted());
		mockMvc.perform(request(acceptedId, SECRET)).andExpect(status().isOk());

		String rejectedId = "123e4567-e89b-42d3-a456-426614174122";
		mockMvc.perform(request(rejectedId, "wrong")).andExpect(status().isUnauthorized());
		assertThat(spoolRoot.resolve("incoming").resolve(rejectedId + ".json")).doesNotExist();
	}

	private static org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request(
			String deliveryId,
			String secret) {
		return post("/internal/deployments/ops")
				.contentType(MediaType.APPLICATION_JSON)
				.header("Authorization", "Bearer " + secret)
				.content(payload(deliveryId));
	}

	private static byte[] payload(String deliveryId) {
		return ("""
				{"deliveryId":"%s","revision":"%s"}
				""").formatted(deliveryId, REVISION).getBytes(StandardCharsets.UTF_8);
	}
}
