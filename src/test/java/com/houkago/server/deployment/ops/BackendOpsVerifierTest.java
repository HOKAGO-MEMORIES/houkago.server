package com.houkago.server.deployment.ops;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

class BackendOpsVerifierTest {

	private static final String SECRET = "synthetic-ops-secret";
	private static final String REVISION = "0123456789abcdef0123456789abcdef01234567";

	private BackendOpsVerifier verifier;

	@BeforeEach
	void setUp() {
		BackendOpsWebhookProperties properties = new BackendOpsWebhookProperties();
		properties.setSecret(SECRET);
		verifier = new BackendOpsVerifier(new ObjectMapper(), properties);
	}

	@Test
	void acceptsCanonicalOperationsRequest() {
		BackendOpsRequest request = verifier.verify(
				"Bearer " + SECRET,
				payload("123e4567-e89b-42d3-a456-426614174100", REVISION));

		assertThat(request.deliveryId()).isEqualTo("123e4567-e89b-42d3-a456-426614174100");
		assertThat(request.revision()).isEqualTo(REVISION);
	}

	@Test
	void rejectsMissingAndWrongBearerAuthentication() {
		byte[] body = payload("123e4567-e89b-42d3-a456-426614174101", REVISION);

		assertThatThrownBy(() -> verifier.verify(null, body))
				.isInstanceOf(BackendOpsAuthenticationException.class);
		assertThatThrownBy(() -> verifier.verify("Bearer wrong", body))
				.isInstanceOf(BackendOpsAuthenticationException.class);
	}

	@Test
	void rejectsMalformedAndZeroRevision() {
		assertThatThrownBy(() -> verifier.verify(
				"Bearer " + SECRET,
				payload("123e4567-e89b-42d3-a456-426614174102", "short")))
				.isInstanceOf(BackendOpsValidationException.class);
		assertThatThrownBy(() -> verifier.verify(
				"Bearer " + SECRET,
				payload("123e4567-e89b-42d3-a456-426614174102", "0".repeat(40))))
				.isInstanceOf(BackendOpsValidationException.class);
	}

	@Test
	void rejectsUnknownCommandOrFileListFields() {
		byte[] body = ("""
				{"deliveryId":"123e4567-e89b-42d3-a456-426614174103","revision":"%s","files":[]}
				""").formatted(REVISION).getBytes(StandardCharsets.UTF_8);

		assertThatThrownBy(() -> verifier.verify("Bearer " + SECRET, body))
				.isInstanceOf(BackendOpsValidationException.class);
	}

	@Test
	void rejectsOversizedPayloadWithoutParsingIt() {
		assertThatThrownBy(() -> verifier.verify("Bearer " + SECRET, new byte[2_049]))
				.isInstanceOf(BackendOpsValidationException.class)
				.hasMessageContaining("too large");
	}

	private static byte[] payload(String deliveryId, String revision) {
		return ("""
				{"deliveryId":"%s","revision":"%s"}
				""").formatted(deliveryId, revision).getBytes(StandardCharsets.UTF_8);
	}
}
