package com.houkago.server.content.post.webhook;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

class PostGitHubWebhookVerifierTest {

	private static final String SECRET = "synthetic-test-secret";
	private static final String REPOSITORY = "example/houkago.posts";
	private static final String DELIVERY_ID = "123e4567-e89b-42d3-a456-426614174000";
	private static final String COMMIT_SHA = "0123456789abcdef0123456789abcdef01234567";

	private PostGitHubWebhookVerifier verifier;

	@BeforeEach
	void setUp() {
		PostGitHubWebhookProperties properties = new PostGitHubWebhookProperties();
		properties.setSecret(SECRET);
		properties.setRepositoryFullName(REPOSITORY);
		properties.setRef("refs/heads/main");
		verifier = new PostGitHubWebhookVerifier(new ObjectMapper(), properties);
	}

	@Test
	void validSignedPushReturnsMinimalVerifiedRequest() {
		byte[] body = payload(REPOSITORY, "refs/heads/main", false, COMMIT_SHA);

		PostGitHubWebhookRequest request = verifier.verify(signature(body), DELIVERY_ID, "push", body);

		assertThat(request.deliveryId()).isEqualTo(DELIVERY_ID);
		assertThat(request.commitSha()).isEqualTo(COMMIT_SHA);
	}

	@Test
	void missingSignatureIsRejected() {
		byte[] body = payload(REPOSITORY, "refs/heads/main", false, COMMIT_SHA);

		assertThatThrownBy(() -> verifier.verify(null, DELIVERY_ID, "push", body))
				.isInstanceOf(PostGitHubWebhookAuthenticationException.class);
	}

	@Test
	void invalidSignatureIsRejectedBeforePayloadProcessing() {
		byte[] body = payload(REPOSITORY, "refs/heads/main", false, COMMIT_SHA);

		assertThatThrownBy(() -> verifier.verify("sha256=" + "0".repeat(64), DELIVERY_ID, "push", body))
				.isInstanceOf(PostGitHubWebhookAuthenticationException.class);
	}

	@Test
	void signatureIsCalculatedFromExactRawBodyBytes() {
		byte[] body = payload(REPOSITORY, "refs/heads/main", false, COMMIT_SHA);
		String signature = signature(body);
		byte[] changedBody = (new String(body, StandardCharsets.UTF_8) + "\n").getBytes(StandardCharsets.UTF_8);

		assertThatThrownBy(() -> verifier.verify(signature, DELIVERY_ID, "push", changedBody))
				.isInstanceOf(PostGitHubWebhookAuthenticationException.class);
	}

	@Test
	void nonPushEventIsRejected() {
		byte[] body = payload(REPOSITORY, "refs/heads/main", false, COMMIT_SHA);

		assertThatThrownBy(() -> verifier.verify(signature(body), DELIVERY_ID, "ping", body))
				.isInstanceOf(PostGitHubWebhookValidationException.class);
	}

	@Test
	void wrongRepositoryIsRejected() {
		byte[] body = payload("example/other", "refs/heads/main", false, COMMIT_SHA);

		assertThatThrownBy(() -> verifier.verify(signature(body), DELIVERY_ID, "push", body))
				.isInstanceOf(PostGitHubWebhookValidationException.class);
	}

	@Test
	void wrongBranchIsRejected() {
		byte[] body = payload(REPOSITORY, "refs/heads/feature", false, COMMIT_SHA);

		assertThatThrownBy(() -> verifier.verify(signature(body), DELIVERY_ID, "push", body))
				.isInstanceOf(PostGitHubWebhookValidationException.class);
	}

	@Test
	void forcedPushIsRejected() {
		byte[] body = payload(REPOSITORY, "refs/heads/main", true, COMMIT_SHA);

		assertThatThrownBy(() -> verifier.verify(signature(body), DELIVERY_ID, "push", body))
				.isInstanceOf(PostGitHubWebhookValidationException.class);
	}

	@Test
	void invalidCommitShaIsRejected() {
		byte[] body = payload(REPOSITORY, "refs/heads/main", false, "not-a-sha");

		assertThatThrownBy(() -> verifier.verify(signature(body), DELIVERY_ID, "push", body))
				.isInstanceOf(PostGitHubWebhookValidationException.class);
	}

	@Test
	void zeroCommitShaIsRejected() {
		byte[] body = payload(REPOSITORY, "refs/heads/main", false, "0".repeat(40));

		assertThatThrownBy(() -> verifier.verify(signature(body), DELIVERY_ID, "push", body))
				.isInstanceOf(PostGitHubWebhookValidationException.class);
	}

	@Test
	void invalidDeliveryIdIsRejected() {
		byte[] body = payload(REPOSITORY, "refs/heads/main", false, COMMIT_SHA);

		assertThatThrownBy(() -> verifier.verify(signature(body), "../../job", "push", body))
				.isInstanceOf(PostGitHubWebhookValidationException.class);
	}

	@Test
	void invalidJsonIsRejectedAfterSignatureVerification() {
		byte[] body = "{".getBytes(StandardCharsets.UTF_8);

		assertThatThrownBy(() -> verifier.verify(signature(body), DELIVERY_ID, "push", body))
				.isInstanceOf(PostGitHubWebhookValidationException.class);
	}

	private static byte[] payload(String repository, String ref, boolean forced, String after) {
		return ("""
				{
				  "repository": {"full_name": "%s"},
				  "ref": "%s",
				  "forced": %s,
				  "after": "%s"
				}
				""").formatted(repository, ref, forced, after).getBytes(StandardCharsets.UTF_8);
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
