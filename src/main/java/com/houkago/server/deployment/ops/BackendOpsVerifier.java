package com.houkago.server.deployment.ops;

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

public class BackendOpsVerifier {

	private static final String BEARER_PREFIX = "Bearer ";
	private static final int MAX_AUTHORIZATION_LENGTH = 512;
	private static final int MAX_BODY_LENGTH = 2_048;
	private static final Pattern DELIVERY_ID_PATTERN = Pattern.compile(
			"[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[1-5][0-9a-fA-F]{3}-[89aAbB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}");
	private static final Pattern REVISION_PATTERN = Pattern.compile("[0-9a-fA-F]{40}");
	private static final Pattern ZERO_REVISION_PATTERN = Pattern.compile("0+");
	private static final Set<String> PAYLOAD_FIELDS = Set.of("deliveryId", "revision");

	private final ObjectMapper objectMapper;
	private final byte[] secretDigest;

	public BackendOpsVerifier(ObjectMapper objectMapper, BackendOpsWebhookProperties properties) {
		this.objectMapper = objectMapper;
		this.secretDigest = digest(requireText(properties.getSecret(), "ops reconcile secret"));
	}

	public BackendOpsRequest verify(String authorization, byte[] rawBody) {
		verifyAuthorization(authorization);
		BackendOpsPayload payload = parsePayload(rawBody);
		return new BackendOpsRequest(
				normalizeDeliveryId(payload.deliveryId()),
				normalizeRevision(payload.revision()));
	}

	private void verifyAuthorization(String authorization) {
		if (authorization == null
				|| authorization.length() > MAX_AUTHORIZATION_LENGTH
				|| !authorization.startsWith(BEARER_PREFIX)) {
			throw new BackendOpsAuthenticationException("Missing or malformed operations authorization");
		}
		String suppliedSecret = authorization.substring(BEARER_PREFIX.length());
		if (suppliedSecret.isEmpty() || !MessageDigest.isEqual(secretDigest, digest(suppliedSecret))) {
			throw new BackendOpsAuthenticationException("Operations authorization mismatch");
		}
	}

	private BackendOpsPayload parsePayload(byte[] rawBody) {
		if (rawBody == null || rawBody.length == 0) {
			throw new BackendOpsValidationException("Operations reconcile body is required");
		}
		if (rawBody.length > MAX_BODY_LENGTH) {
			throw new BackendOpsValidationException("Operations reconcile body is too large");
		}
		try {
			JsonNode root = objectMapper.readTree(rawBody);
			if (root == null || !root.isObject()) {
				throw new BackendOpsValidationException("Operations reconcile payload must be an object");
			}
			Set<String> fields = new HashSet<>();
			root.fieldNames().forEachRemaining(fields::add);
			if (!PAYLOAD_FIELDS.equals(fields)) {
				throw new BackendOpsValidationException("Operations reconcile payload fields are invalid");
			}
			return objectMapper.treeToValue(root, BackendOpsPayload.class);
		}
		catch (BackendOpsValidationException exception) {
			throw exception;
		}
		catch (IOException exception) {
			throw new BackendOpsValidationException("Operations reconcile payload is not valid JSON", exception);
		}
	}

	private String normalizeDeliveryId(String deliveryId) {
		if (deliveryId == null || !DELIVERY_ID_PATTERN.matcher(deliveryId).matches()) {
			throw new BackendOpsValidationException("Operations delivery ID must be a UUID");
		}
		return UUID.fromString(deliveryId).toString();
	}

	private String normalizeRevision(String revision) {
		if (revision == null
				|| !REVISION_PATTERN.matcher(revision).matches()
				|| ZERO_REVISION_PATTERN.matcher(revision).matches()) {
			throw new BackendOpsValidationException("Operations revision must be a full Git SHA");
		}
		return revision.toLowerCase(Locale.ROOT);
	}

	private static byte[] digest(String value) {
		try {
			return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
		}
		catch (NoSuchAlgorithmException exception) {
			throw new IllegalStateException("SHA-256 is not available", exception);
		}
	}

	private static String requireText(String value, String field) {
		if (value == null || value.isBlank()) {
			throw new IllegalArgumentException(field + " must not be blank when operations reconcile is enabled");
		}
		return value;
	}
}
