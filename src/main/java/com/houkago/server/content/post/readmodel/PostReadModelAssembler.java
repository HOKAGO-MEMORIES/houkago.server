package com.houkago.server.content.post.readmodel;

import java.time.Instant;
import java.util.Objects;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import com.houkago.server.content.post.metadata.PostMetadataMapping;
import com.houkago.server.content.post.policy.PostSyncStatus;
import com.houkago.server.content.post.policy.PostVisibility;
import com.houkago.server.content.post.preparation.PreparedPostCandidate;

@Component
@Profile("!asset-sync")
public class PostReadModelAssembler {

	private final PostTagsJsonCodec tagsJsonCodec;

	public PostReadModelAssembler(PostTagsJsonCodec tagsJsonCodec) {
		this.tagsJsonCodec = Objects.requireNonNull(tagsJsonCodec, "tagsJsonCodec is required");
	}

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

	public PostReadModel create(
			PreparedPostCandidate candidate,
			String commitHash,
			Instant syncedAt) {
		Objects.requireNonNull(candidate, "prepared candidate is required");
		return create(
				candidate.metadata(),
				candidate.rawBody(),
				candidate.sourcePath(),
				commitHash,
				candidate.checksum(),
				syncedAt);
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
		post.setTagsJson(tagsJsonCodec.encode(metadata.tags()));
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

	public PostReadModel update(
			PostReadModel post,
			PreparedPostCandidate candidate,
			String commitHash,
			Instant syncedAt) {
		Objects.requireNonNull(candidate, "prepared candidate is required");
		return update(
				post,
				candidate.metadata(),
				candidate.rawBody(),
				candidate.sourcePath(),
				commitHash,
				candidate.checksum(),
				syncedAt);
	}

	public PostReadModel touch(PostReadModel post, String commitHash, Instant syncedAt) {
		Objects.requireNonNull(post, "post read model is required");
		Objects.requireNonNull(syncedAt, "syncedAt is required");

		post.setCommitHash(requireText("commitHash", commitHash));
		post.setSyncedAt(syncedAt);
		return post;
	}

	public PostReadModel markDeleted(PostReadModel post, String commitHash, Instant syncedAt) {
		Objects.requireNonNull(post, "post read model is required");
		Objects.requireNonNull(syncedAt, "syncedAt is required");

		post.setSyncStatus(PostSyncStatus.DELETED);
		post.setVisibility(PostVisibility.PRIVATE);
		post.setCommitHash(requireText("commitHash", commitHash));
		post.setSyncedAt(syncedAt);
		return post;
	}

	private static String requireText(String field, String value) {
		if (value == null || value.isBlank()) {
			throw new IllegalArgumentException(field + " is required");
		}
		return value;
	}

}
