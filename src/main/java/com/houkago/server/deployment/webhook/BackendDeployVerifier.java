package com.houkago.server.deployment.webhook;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public class BackendDeployVerifier {

	private static final String BEARER_PREFIX = "Bearer ";
	private static final int MAX_AUTHORIZATION_LENGTH = 512;
	private static final int MAX_BODY_LENGTH = 4_096;
	private static final Pattern DELIVERY_ID_PATTERN = Pattern.compile(
			"[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[1-5][0-9a-fA-F]{3}-[89aAbB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}");
	private static final Pattern REVISION_PATTERN = Pattern.compile("[0-9a-fA-F]{40}");
	private static final Pattern ZERO_REVISION_PATTERN = Pattern.compile("0+");
	private static final Pattern DIGEST_PATTERN = Pattern.compile("sha256:[0-9a-f]{64}");
	private static final Set<String> PAYLOAD_FIELDS = Set.of("deliveryId", "revision", "image");

	private final ObjectMapper objectMapper;
	private final byte[] secretDigest;
	private final String imageRepository;

	public BackendDeployVerifier(ObjectMapper objectMapper, BackendDeployWebhookProperties properties) {
		this.objectMapper = objectMapper;
		this.secretDigest = digest(requireText(properties.getSecret(), "deploy secret"));
		this.imageRepository = normalizeImageRepository(
				requireText(properties.getImageRepository(), "image repository"));
	}

	public BackendDeployRequest verify(String authorization, byte[] rawBody) {
		verifyAuthorization(authorization);
		BackendDeployPayload payload = parsePayload(rawBody);
		String deliveryId = normalizeDeliveryId(payload.deliveryId());
		String revision = normalizeRevision(payload.revision());
		String image = validateImage(payload.image());
		return new BackendDeployRequest(deliveryId, revision, image);
	}

	private void verifyAuthorization(String authorization) {
		if (authorization == null
				|| authorization.length() > MAX_AUTHORIZATION_LENGTH
				|| !authorization.startsWith(BEARER_PREFIX)) {
			throw new BackendDeployAuthenticationException("Missing or malformed deployment authorization");
		}
		String suppliedSecret = authorization.substring(BEARER_PREFIX.length());
		if (suppliedSecret.isEmpty()
				|| !MessageDigest.isEqual(secretDigest, digest(suppliedSecret))) {
			throw new BackendDeployAuthenticationException("Deployment authorization mismatch");
		}
	}

	private BackendDeployPayload parsePayload(byte[] rawBody) {
		if (rawBody == null || rawBody.length == 0) {
			throw new BackendDeployValidationException("Deployment body is required");
		}
		if (rawBody.length > MAX_BODY_LENGTH) {
			throw new BackendDeployValidationException("Deployment body is too large");
		}
		try {
			JsonNode root = objectMapper.readTree(rawBody);
			if (root == null || !root.isObject()) {
				throw new BackendDeployValidationException("Deployment payload must be an object");
			}
			Set<String> fields = new HashSet<>();
			root.fieldNames().forEachRemaining(fields::add);
			if (!PAYLOAD_FIELDS.equals(fields)) {
				throw new BackendDeployValidationException("Deployment payload fields are invalid");
			}
			return objectMapper.treeToValue(root, BackendDeployPayload.class);
		}
		catch (BackendDeployValidationException exception) {
			throw exception;
		}
		catch (IOException exception) {
			throw new BackendDeployValidationException("Deployment payload is not valid JSON", exception);
		}
	}

	private String normalizeDeliveryId(String deliveryId) {
		if (deliveryId == null || !DELIVERY_ID_PATTERN.matcher(deliveryId).matches()) {
			throw new BackendDeployValidationException("Deployment delivery ID must be a UUID");
		}
		return UUID.fromString(deliveryId).toString();
	}

	private String normalizeRevision(String revision) {
		if (revision == null
				|| !REVISION_PATTERN.matcher(revision).matches()
				|| ZERO_REVISION_PATTERN.matcher(revision).matches()) {
			throw new BackendDeployValidationException("Deployment revision must be a full Git SHA");
		}
		return revision.toLowerCase(Locale.ROOT);
	}

	private String validateImage(String image) {
		if (image == null) {
			throw new BackendDeployValidationException("Deployment image is required");
		}
		String prefix = imageRepository + "@";
		if (!image.startsWith(prefix)) {
			throw new BackendDeployValidationException("Deployment image repository is not allowed");
		}
		String digest = image.substring(prefix.length());
		if (!DIGEST_PATTERN.matcher(digest).matches()) {
			throw new BackendDeployValidationException("Deployment image must use an immutable SHA-256 digest");
		}
		return image;
	}

	private static byte[] digest(String value) {
		try {
			return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
		}
		catch (NoSuchAlgorithmException exception) {
			throw new IllegalStateException("SHA-256 is not available", exception);
		}
	}

	private static String normalizeImageRepository(String imageRepository) {
		String normalized = imageRepository.toLowerCase(Locale.ROOT);
		if (!normalized.matches("ghcr\\.io/[a-z0-9._-]+/[a-z0-9._-]+")) {
			throw new IllegalArgumentException("image repository must be a canonical GHCR repository");
		}
		return normalized;
	}

	private static String requireText(String value, String field) {
		if (value == null || value.isBlank()) {
			throw new IllegalArgumentException(field + " must not be blank when deployment is enabled");
		}
		return value;
	}
}
