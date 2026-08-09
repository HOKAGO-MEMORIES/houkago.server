package com.houkago.server.content.post.webhook;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import com.fasterxml.jackson.databind.ObjectMapper;

public class PostGitHubWebhookVerifier {

	private static final String SIGNATURE_PREFIX = "sha256=";
	private static final Pattern SIGNATURE_PATTERN = Pattern.compile("sha256=[0-9a-fA-F]{64}");
	private static final Pattern DELIVERY_ID_PATTERN = Pattern.compile(
			"[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[1-5][0-9a-fA-F]{3}-[89aAbB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}");
	private static final Pattern COMMIT_SHA_PATTERN = Pattern.compile("(?:[0-9a-fA-F]{40}|[0-9a-fA-F]{64})");
	private static final Pattern ZERO_SHA_PATTERN = Pattern.compile("0+");

	private final ObjectMapper objectMapper;
	private final byte[] secret;
	private final String repositoryFullName;
	private final String allowedRef;

	public PostGitHubWebhookVerifier(ObjectMapper objectMapper, PostGitHubWebhookProperties properties) {
		this.objectMapper = objectMapper;
		this.secret = requireText(properties.getSecret(), "webhook secret").getBytes(StandardCharsets.UTF_8);
		this.repositoryFullName = requireText(properties.getRepositoryFullName(), "repository full name");
		this.allowedRef = requireText(properties.getRef(), "allowed ref");
	}

	public PostGitHubWebhookRequest verify(
			String signature,
			String deliveryId,
			String event,
			byte[] rawBody) {
		verifySignature(signature, rawBody);

		if (!"push".equals(event)) {
			throw new PostGitHubWebhookValidationException("GitHub event must be push");
		}

		String normalizedDeliveryId = normalizeDeliveryId(deliveryId);
		PostGitHubPushPayload payload = parsePayload(rawBody);
		validatePayload(payload);

		return new PostGitHubWebhookRequest(
				normalizedDeliveryId,
				payload.after().toLowerCase(Locale.ROOT));
	}

	private void verifySignature(String signature, byte[] rawBody) {
		if (signature == null || !SIGNATURE_PATTERN.matcher(signature).matches()) {
			throw new PostGitHubWebhookAuthenticationException("Missing or malformed webhook signature");
		}
		if (rawBody == null) {
			throw new PostGitHubWebhookAuthenticationException("Webhook body is required");
		}

		byte[] suppliedSignature;
		try {
			suppliedSignature = HexFormat.of().parseHex(signature.substring(SIGNATURE_PREFIX.length()));
		}
		catch (IllegalArgumentException exception) {
			throw new PostGitHubWebhookAuthenticationException("Malformed webhook signature");
		}

		byte[] expectedSignature = calculateHmac(rawBody);
		if (!MessageDigest.isEqual(expectedSignature, suppliedSignature)) {
			throw new PostGitHubWebhookAuthenticationException("Webhook signature mismatch");
		}
	}

	private byte[] calculateHmac(byte[] rawBody) {
		try {
			Mac mac = Mac.getInstance("HmacSHA256");
			mac.init(new SecretKeySpec(secret, "HmacSHA256"));
			return mac.doFinal(rawBody);
		}
		catch (NoSuchAlgorithmException | InvalidKeyException exception) {
			throw new IllegalStateException("HmacSHA256 is not available", exception);
		}
	}

	private String normalizeDeliveryId(String deliveryId) {
		if (deliveryId == null || !DELIVERY_ID_PATTERN.matcher(deliveryId).matches()) {
			throw new PostGitHubWebhookValidationException("GitHub delivery ID must be a UUID");
		}
		return UUID.fromString(deliveryId).toString();
	}

	private PostGitHubPushPayload parsePayload(byte[] rawBody) {
		try {
			return objectMapper.readValue(rawBody, PostGitHubPushPayload.class);
		}
		catch (IOException exception) {
			throw new PostGitHubWebhookValidationException("GitHub webhook payload is not valid JSON", exception);
		}
	}

	private void validatePayload(PostGitHubPushPayload payload) {
		if (payload == null) {
			throw new PostGitHubWebhookValidationException("GitHub webhook payload must be an object");
		}
		if (payload.repository() == null || !repositoryFullName.equals(payload.repository().fullName())) {
			throw new PostGitHubWebhookValidationException("GitHub repository is not allowed");
		}
		if (!allowedRef.equals(payload.ref())) {
			throw new PostGitHubWebhookValidationException("GitHub ref is not allowed");
		}
		if (payload.forced() == null || payload.forced()) {
			throw new PostGitHubWebhookValidationException("Forced or unspecified pushes are not allowed");
		}
		if (payload.after() == null
				|| !COMMIT_SHA_PATTERN.matcher(payload.after()).matches()
				|| ZERO_SHA_PATTERN.matcher(payload.after()).matches()) {
			throw new PostGitHubWebhookValidationException("GitHub after value must be a commit SHA");
		}
	}

	private static String requireText(String value, String field) {
		if (value == null || value.isBlank()) {
			throw new IllegalArgumentException(field + " must not be blank when the webhook is enabled");
		}
		return value;
	}
}
