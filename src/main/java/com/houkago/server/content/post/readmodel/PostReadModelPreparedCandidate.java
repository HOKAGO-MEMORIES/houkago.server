package com.houkago.server.content.post.readmodel;

import java.util.Objects;

import com.houkago.server.content.post.metadata.PostMetadataMapping;

public record PostReadModelPreparedCandidate(
		PostMetadataMapping metadata,
		String rawBody,
		String sourcePath,
		String checksum) {

	public PostReadModelPreparedCandidate {
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
