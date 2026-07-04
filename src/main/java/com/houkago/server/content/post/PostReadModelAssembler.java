package com.houkago.server.content.post;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

import org.springframework.stereotype.Component;

@Component
public class PostReadModelAssembler {

	public PostReadModel create(
			PostMetadataMapping metadata,
			String rawBody,
			String sourcePath,
			String commitHash,
			String checksum,
			Instant syncedAt) {
		PostReadModel post = new PostReadModel();
		update(post, metadata, rawBody, sourcePath, commitHash, checksum, syncedAt);
		return post;
	}

	public PostReadModel update(
			PostReadModel post,
			PostMetadataMapping metadata,
			String rawBody,
			String sourcePath,
			String commitHash,
			String checksum,
			Instant syncedAt) {
		Objects.requireNonNull(post, "post read model is required");
		Objects.requireNonNull(metadata, "metadata mapping is required");
		Objects.requireNonNull(rawBody, "raw body is required");
		Objects.requireNonNull(syncedAt, "syncedAt is required");

		post.setSlug(metadata.slug());
		post.setTitle(metadata.title());
		post.setDescription(metadata.description());
		post.setCategory(metadata.category());
		post.setTagsJson(toTagsJson(metadata.tags()));
		post.setPostDate(metadata.date());
		post.setPostUpdatedDate(metadata.updated());
		post.setThumbnail(metadata.thumbnail());
		post.setSeries(metadata.series());
		post.setFeatured(metadata.featured());
		post.setPlatform(metadata.platform());
		post.setProblemId(metadata.problemId());
		post.setSourcePath(requireText("sourcePath", sourcePath));
		post.setRawBody(rawBody);
		post.setCommitHash(commitHash);
		post.setChecksum(requireText("checksum", checksum));
		post.setSourceStatus(metadata.sourceStatus());
		post.setSyncStatus(metadata.syncStatus());
		post.setVisibility(metadata.visibility());
		post.setSyncedAt(syncedAt);
		return post;
	}

	private static String requireText(String field, String value) {
		if (value == null || value.isBlank()) {
			throw new IllegalArgumentException(field + " is required");
		}
		return value;
	}

	private static String toTagsJson(List<String> tags) {
		if (tags == null || tags.isEmpty()) {
			return "[]";
		}
		return tags.stream()
				.map(PostReadModelAssembler::toJsonString)
				.toList()
				.toString();
	}

	private static String toJsonString(String value) {
		if (value == null) {
			return "null";
		}

		StringBuilder builder = new StringBuilder("\"");
		for (int index = 0; index < value.length(); index++) {
			char character = value.charAt(index);
			switch (character) {
				case '"' -> builder.append("\\\"");
				case '\\' -> builder.append("\\\\");
				case '\b' -> builder.append("\\b");
				case '\f' -> builder.append("\\f");
				case '\n' -> builder.append("\\n");
				case '\r' -> builder.append("\\r");
				case '\t' -> builder.append("\\t");
				default -> {
					if (character < 0x20) {
						builder.append(String.format("\\u%04x", (int) character));
					} else {
						builder.append(character);
					}
				}
			}
		}
		return builder.append('"').toString();
	}
}
