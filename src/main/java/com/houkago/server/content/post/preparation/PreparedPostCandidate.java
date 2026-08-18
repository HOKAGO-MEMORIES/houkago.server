package com.houkago.server.content.post.preparation;

import java.util.Objects;

import com.houkago.server.content.post.metadata.PostMetadataMapping;

public record PreparedPostCandidate(
		PostMetadataMapping metadata,
		String rawBody,
		String sourcePath,
		String checksum) {

	public PreparedPostCandidate {
		Objects.requireNonNull(metadata, "metadata is required");
		Objects.requireNonNull(rawBody, "rawBody is required");
		requireText("sourcePath", sourcePath);
		requireText("checksum", checksum);
	}

	private static void requireText(String field, String value) {
		if (value == null || value.isBlank()) {
			throw new IllegalArgumentException(field + " is required");
		}
	}
}
