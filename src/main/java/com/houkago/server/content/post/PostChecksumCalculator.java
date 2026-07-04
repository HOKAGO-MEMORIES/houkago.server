package com.houkago.server.content.post;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.List;

import org.springframework.stereotype.Component;

@Component
public class PostChecksumCalculator {

	public String calculate(PostChecksumInput input) {
		if (input == null) {
			throw new IllegalArgumentException("checksum input is required");
		}

		byte[] digest = sha256(canonicalString(input));
		return HexFormat.of().formatHex(digest);
	}

	String canonicalString(PostChecksumInput input) {
		StringBuilder builder = new StringBuilder();
		appendField(builder, "rawBody", normalizeLineEndings(input.rawBody()));
		appendField(builder, "title", input.title());
		appendField(builder, "slug", input.slug());
		appendField(builder, "date", formatDate(input.date()));
		appendField(builder, "description", input.description());
		appendField(builder, "category", input.category());
		appendField(builder, "sourceStatus", enumName(input.sourceStatus()));
		appendField(builder, "tags", canonicalTags(input.tags()));
		appendField(builder, "updated", formatDate(input.updated()));
		appendField(builder, "thumbnail", input.thumbnail());
		appendField(builder, "series", input.series());
		appendField(builder, "featured", Boolean.toString(input.featured()));
		appendField(builder, "platform", input.platform());
		appendField(builder, "problemId", input.problemId());
		return builder.toString();
	}

	private static void appendField(StringBuilder builder, String fieldName, String value) {
		String normalizedValue = value == null ? "" : value;
		String marker = value == null ? "N" : "S";
		builder.append(fieldName.length())
				.append(':')
				.append(fieldName)
				.append('=')
				.append(marker)
				.append(':')
				.append(normalizedValue.length())
				.append(':')
				.append(normalizedValue)
				.append('\n');
	}

	private static String canonicalTags(List<String> tags) {
		if (tags == null || tags.isEmpty()) {
			return "";
		}
		return tags.stream()
				.map(PostChecksumCalculator::normalizeNullableString)
				.sorted()
				.toList()
				.toString();
	}

	private static String normalizeNullableString(String value) {
		return value == null ? "" : value;
	}

	private static String normalizeLineEndings(String value) {
		if (value == null) {
			return null;
		}
		return value
				.replace("\r\n", "\n")
				.replace('\r', '\n');
	}

	private static String formatDate(LocalDate date) {
		return date == null ? "" : date.toString();
	}

	private static String enumName(Enum<?> value) {
		return value == null ? "" : value.name();
	}

	private static byte[] sha256(String value) {
		try {
			return MessageDigest.getInstance("SHA-256")
					.digest(value.getBytes(StandardCharsets.UTF_8));
		} catch (NoSuchAlgorithmException exception) {
			throw new IllegalStateException("SHA-256 algorithm is not available", exception);
		}
	}
}
