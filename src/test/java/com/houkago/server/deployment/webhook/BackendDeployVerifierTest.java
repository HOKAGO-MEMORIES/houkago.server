package com.houkago.server.deployment.webhook;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

class BackendDeployVerifierTest {

	private static final String SECRET = "synthetic-deploy-secret";
	private static final String REVISION = "0123456789abcdef0123456789abcdef01234567";
	private static final String DIGEST = "sha256:" + "a".repeat(64);
	private static final String IMAGE_REPOSITORY = "ghcr.io/example/houkago.server";

	private BackendDeployVerifier verifier;

	@BeforeEach
	void setUp() {
		BackendDeployWebhookProperties properties = new BackendDeployWebhookProperties();
		properties.setSecret(SECRET);
		properties.setImageRepository(IMAGE_REPOSITORY);
		verifier = new BackendDeployVerifier(new ObjectMapper(), properties);
	}

	@Test
	void acceptsCanonicalDigestDeployment() {
		BackendDeployRequest request = verifier.verify("Bearer " + SECRET, payload(
				"123e4567-e89b-42d3-a456-426614174000",
				REVISION,
				IMAGE_REPOSITORY + "@" + DIGEST));

		assertThat(request.deliveryId()).isEqualTo("123e4567-e89b-42d3-a456-426614174000");
		assertThat(request.revision()).isEqualTo(REVISION);
		assertThat(request.image()).isEqualTo(IMAGE_REPOSITORY + "@" + DIGEST);
	}

	@Test
	void rejectsMissingAndWrongBearerAuthentication() {
		assertThatThrownBy(() -> verifier.verify(null, payload(
				"123e4567-e89b-42d3-a456-426614174001", REVISION, IMAGE_REPOSITORY + "@" + DIGEST)))
				.isInstanceOf(BackendDeployAuthenticationException.class);
		assertThatThrownBy(() -> verifier.verify("Bearer wrong", payload(
				"123e4567-e89b-42d3-a456-426614174001", REVISION, IMAGE_REPOSITORY + "@" + DIGEST)))
				.isInstanceOf(BackendDeployAuthenticationException.class);
	}

	@Test
	void rejectsMutableTagWrongRepositoryAndMalformedRevision() {
		assertThatThrownBy(() -> verifier.verify("Bearer " + SECRET, payload(
				"123e4567-e89b-42d3-a456-426614174002", REVISION, IMAGE_REPOSITORY + ":latest")))
				.isInstanceOf(BackendDeployValidationException.class);
		assertThatThrownBy(() -> verifier.verify("Bearer " + SECRET, payload(
				"123e4567-e89b-42d3-a456-426614174002",
				REVISION,
				"ghcr.io/other/houkago.server@" + DIGEST)))
				.isInstanceOf(BackendDeployValidationException.class);
		assertThatThrownBy(() -> verifier.verify("Bearer " + SECRET, payload(
				"123e4567-e89b-42d3-a456-426614174002", "short", IMAGE_REPOSITORY + "@" + DIGEST)))
				.isInstanceOf(BackendDeployValidationException.class);
	}

	@Test
	void rejectsUnknownPayloadFields() {
		byte[] body = ("""
				{
				  "deliveryId": "123e4567-e89b-42d3-a456-426614174003",
				  "revision": "%s",
				  "image": "%s@%s",
				  "command": "ignored"
				}
				""").formatted(REVISION, IMAGE_REPOSITORY, DIGEST).getBytes(StandardCharsets.UTF_8);

		assertThatThrownBy(() -> verifier.verify("Bearer " + SECRET, body))
				.isInstanceOf(BackendDeployValidationException.class);
	}

	@Test
	void rejectsOversizedPayloadWithoutParsingIt() {
		byte[] body = new byte[4_097];

		assertThatThrownBy(() -> verifier.verify("Bearer " + SECRET, body))
				.isInstanceOf(BackendDeployValidationException.class)
				.hasMessageContaining("too large");
	}

	private static byte[] payload(String deliveryId, String revision, String image) {
		return ("""
				{"deliveryId":"%s","revision":"%s","image":"%s"}
				""").formatted(deliveryId, revision, image).getBytes(StandardCharsets.UTF_8);
	}
}
