package com.houkago.server.content.post.readmodel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.houkago.server.content.post.metadata.PostMetadataMapping;
import com.houkago.server.content.post.policy.PostSourceStatus;
import com.houkago.server.content.post.policy.PostSyncStatus;
import com.houkago.server.content.post.policy.PostVisibility;
import com.houkago.server.content.post.preparation.PreparedPostCandidate;

class PostReadModelCandidateProcessorTest {

	private static final Instant SYNCED_AT = Instant.parse("2026-07-03T00:00:00Z");

	private final PostReadModelCandidateProcessor processor = new PostReadModelCandidateProcessor(
			new PostReadModelAssembler());

	@Test
	void createsPostReadModelFromPreparedCandidate() {
		PreparedPostCandidate candidate = publishedCandidate();

		PostReadModel post = processor.create(candidate, "commit-a", SYNCED_AT);

		assertThat(post.getSlug()).isEqualTo("my-post");
		assertThat(post.getTitle()).isEqualTo("A post");
		assertThat(post.getRawBody()).isEqualTo("## Body");
		assertThat(post.getSourcePath()).isEqualTo("blog/my-post/index.md");
		assertThat(post.getCommitHash()).isEqualTo("commit-a");
		assertThat(post.getChecksum()).isEqualTo("checksum-a");
		assertThat(post.getSyncedAt()).isEqualTo(SYNCED_AT);
	}

	@Test
	void updatePreservesExistingEntityAndAppliesPreparedCandidate() {
		PostReadModel existing = processor.create(publishedCandidate(), "commit-a", SYNCED_AT);
		PreparedPostCandidate candidate = draftCandidate();
		Instant nextSyncedAt = Instant.parse("2026-07-03T01:00:00Z");

		PostReadModel updated = processor.update(existing, candidate, "commit-b", nextSyncedAt);

		assertThat(updated).isSameAs(existing);
		assertThat(existing.getTitle()).isEqualTo("Draft post");
		assertThat(existing.getSourceStatus()).isEqualTo(PostSourceStatus.DRAFT);
		assertThat(existing.getVisibility()).isEqualTo(PostVisibility.PRIVATE);
		assertThat(existing.getRawBody()).isEqualTo("draft body");
		assertThat(existing.getSourcePath()).isEqualTo("blog/draft-post/index.md");
		assertThat(existing.getCommitHash()).isEqualTo("commit-b");
		assertThat(existing.getChecksum()).isEqualTo("checksum-b");
		assertThat(existing.getSyncedAt()).isEqualTo(nextSyncedAt);
	}

	@Test
	void touchPreservesContentAndUpdatesSyncMetadata() {
		PostReadModel existing = processor.create(publishedCandidate(), "commit-a", SYNCED_AT);
		Instant nextSyncedAt = Instant.parse("2026-07-03T02:00:00Z");

		PostReadModel touched = processor.touch(existing, "commit-b", nextSyncedAt);

		assertThat(touched).isSameAs(existing);
		assertThat(existing.getRawBody()).isEqualTo("## Body");
		assertThat(existing.getChecksum()).isEqualTo("checksum-a");
		assertThat(existing.getCommitHash()).isEqualTo("commit-b");
		assertThat(existing.getSyncedAt()).isEqualTo(nextSyncedAt);
	}

	@Test
	void nullPreparedCandidateIsRejected() {
		assertThatThrownBy(() -> processor.create(null, "commit-a", SYNCED_AT))
				.isInstanceOf(NullPointerException.class)
				.hasMessageContaining("prepared candidate is required");
	}

	@Test
	void nullExistingIsRejectedForUpdate() {
		assertThatThrownBy(() -> processor.update(null, publishedCandidate(), "commit-a", SYNCED_AT))
				.isInstanceOf(NullPointerException.class)
				.hasMessageContaining("existing post read model is required");
	}

	@Test
	void blankCommitHashIsRejected() {
		assertThatThrownBy(() -> processor.create(publishedCandidate(), "   ", SYNCED_AT))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("commitHash is required");
	}

	@Test
	void nullSyncedAtIsRejected() {
		assertThatThrownBy(() -> processor.create(publishedCandidate(), "commit-a", null))
				.isInstanceOf(NullPointerException.class)
				.hasMessageContaining("syncedAt is required");
	}

	private static PreparedPostCandidate publishedCandidate() {
		return preparedCandidate(
				"A post",
				"my-post",
				PostSourceStatus.PUBLISHED,
				PostVisibility.PUBLIC,
				"## Body",
				"blog/my-post/index.md",
				"checksum-a");
	}

	private static PreparedPostCandidate draftCandidate() {
		return preparedCandidate(
				"Draft post",
				"draft-post",
				PostSourceStatus.DRAFT,
				PostVisibility.PRIVATE,
				"draft body",
				"blog/draft-post/index.md",
				"checksum-b");
	}

	private static PreparedPostCandidate preparedCandidate(
			String title,
			String slug,
			PostSourceStatus sourceStatus,
			PostVisibility visibility,
			String rawBody,
			String sourcePath,
			String checksum) {
		return new PreparedPostCandidate(
				new PostMetadataMapping(
						title,
						slug,
						LocalDate.of(2026, 7, 3),
						"A useful post.",
						"blog",
						sourceStatus,
						PostSyncStatus.ACTIVE,
						visibility,
						List.of("java", "spring"),
						LocalDate.of(2026, 7, 4),
						"./assets/thumbnail.png",
						"Houkago",
						true,
						"boj",
						"1000"),
				rawBody,
				sourcePath,
				checksum);
	}
}
