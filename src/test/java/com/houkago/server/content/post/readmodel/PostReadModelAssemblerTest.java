package com.houkago.server.content.post.readmodel;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.houkago.server.content.post.metadata.PostMetadataMapping;
import com.houkago.server.content.post.policy.PostSourceStatus;
import com.houkago.server.content.post.policy.PostSyncStatus;
import com.houkago.server.content.post.policy.PostVisibility;
import com.houkago.server.content.post.policy.PostVisibilityPolicy;

class PostReadModelAssemblerTest {

	private static final Instant SYNCED_AT = Instant.parse("2026-07-03T00:00:00Z");

	private final PostReadModelAssembler assembler = new PostReadModelAssembler();

	@Test
	void createsPostReadModelFromMetadataMapping() {
		PostReadModel post = assembler.create(
				publishedMetadata(),
				"## raw body",
				"blog/a-post/index.md",
				"0123456789abcdef0123456789abcdef01234567",
				"checksum-a",
				SYNCED_AT);

		assertThat(post.getSlug()).isEqualTo("a-post");
		assertThat(post.getTitle()).isEqualTo("A post");
		assertThat(post.getDescription()).isEqualTo("A useful post.");
		assertThat(post.getCategory()).isEqualTo("blog");
		assertThat(post.getPostDate()).isEqualTo(LocalDate.of(2026, 7, 3));
		assertThat(post.getPostUpdatedDate()).isEqualTo(LocalDate.of(2026, 7, 4));
		assertThat(post.isFeatured()).isTrue();
		assertThat(post.getPlatform()).isEqualTo("boj");
		assertThat(post.getProblemId()).isEqualTo("1002");
	}

	@Test
	void appliesRawBodySourcePathCommitHashChecksumAndSyncedAt() {
		PostReadModel post = assembler.create(
				publishedMetadata(),
				"## raw body",
				"blog/a-post/index.md",
				"commit-a",
				"checksum-a",
				SYNCED_AT);

		assertThat(post.getRawBody()).isEqualTo("## raw body");
		assertThat(post.getSourcePath()).isEqualTo("blog/a-post/index.md");
		assertThat(post.getCommitHash()).isEqualTo("commit-a");
		assertThat(post.getChecksum()).isEqualTo("checksum-a");
		assertThat(post.getSyncedAt()).isEqualTo(SYNCED_AT);
	}

	@Test
	void usesInjectedSyncedAtWithoutCreatingCurrentTime() {
		Instant fixedSyncedAt = Instant.parse("2000-01-01T00:00:00Z");

		PostReadModel post = assembler.create(
				publishedMetadata(),
				"body",
				"blog/a-post/index.md",
				"commit-a",
				"checksum-a",
				fixedSyncedAt);

		assertThat(post.getSyncedAt()).isEqualTo(fixedSyncedAt);
	}

	@Test
	void publishedMappingCreatesPublicVisibleEntityState() {
		PostReadModel post = assembler.create(
				publishedMetadata(),
				"body",
				"blog/a-post/index.md",
				"commit-a",
				"checksum-a",
				SYNCED_AT);

		assertThat(PostVisibilityPolicy.isPubliclyVisible(
				post.getSourceStatus(),
				post.getSyncStatus(),
				post.getVisibility())).isTrue();
	}

	@Test
	void draftMappingCreatesNonPublicEntityState() {
		PostReadModel post = assembler.create(
				draftMetadata(),
				"body",
				"blog/a-post/index.md",
				"commit-a",
				"checksum-a",
				SYNCED_AT);

		assertThat(PostVisibilityPolicy.isPubliclyVisible(
				post.getSourceStatus(),
				post.getSyncStatus(),
				post.getVisibility())).isFalse();
	}

	@Test
	void updatePreservesExistingObjectIdentity() {
		PostReadModel existing = assembler.create(
				publishedMetadata(),
				"old body",
				"blog/a-post/index.md",
				"commit-a",
				"checksum-a",
				SYNCED_AT);

		PostReadModel updated = assembler.update(
				existing,
				draftMetadata(),
				"new body",
				"blog/a-post/index.md",
				"commit-b",
				"checksum-b",
				Instant.parse("2026-07-03T01:00:00Z"));

		assertThat(updated).isSameAs(existing);
		assertThat(updated.getId()).isEqualTo(existing.getId());
	}

	@Test
	void updateMutatesSameEntityInstance() {
		PostReadModel existing = assembler.create(
				publishedMetadata(),
				"old body",
				"blog/a-post/index.md",
				"commit-a",
				"checksum-a",
				SYNCED_AT);

		assembler.update(
				existing,
				draftMetadata(),
				"new body",
				"blog/a-post-v2/index.md",
				"commit-b",
				"checksum-b",
				Instant.parse("2026-07-03T01:00:00Z"));

		assertThat(existing.getTitle()).isEqualTo("Draft post");
		assertThat(existing.getRawBody()).isEqualTo("new body");
		assertThat(existing.getSourceStatus()).isEqualTo(PostSourceStatus.DRAFT);
	}

	@Test
	void updateChangesChecksum() {
		PostReadModel existing = assembler.create(
				publishedMetadata(),
				"body",
				"blog/a-post/index.md",
				"commit-a",
				"checksum-a",
				SYNCED_AT);

		assembler.update(existing, publishedMetadata(), "body", "blog/a-post/index.md", "commit-a",
				"checksum-b", SYNCED_AT);

		assertThat(existing.getChecksum()).isEqualTo("checksum-b");
	}

	@Test
	void updateChangesSourcePath() {
		PostReadModel existing = assembler.create(
				publishedMetadata(),
				"body",
				"blog/a-post/index.md",
				"commit-a",
				"checksum-a",
				SYNCED_AT);

		assembler.update(existing, publishedMetadata(), "body", "blog/a-post-v2/index.md", "commit-a",
				"checksum-a", SYNCED_AT);

		assertThat(existing.getSourcePath()).isEqualTo("blog/a-post-v2/index.md");
	}

	@Test
	void updateChangesCommitHash() {
		PostReadModel existing = assembler.create(
				publishedMetadata(),
				"body",
				"blog/a-post/index.md",
				"commit-a",
				"checksum-a",
				SYNCED_AT);

		assembler.update(existing, publishedMetadata(), "body", "blog/a-post/index.md", "commit-b",
				"checksum-a", SYNCED_AT);

		assertThat(existing.getCommitHash()).isEqualTo("commit-b");
	}

	@Test
	void serializesTagsAsJsonArrayString() {
		PostMetadataMapping metadata = new PostMetadataMapping(
				"A post",
				"a-post",
				LocalDate.of(2026, 7, 3),
				"A useful post.",
				"blog",
				PostSourceStatus.PUBLISHED,
				PostSyncStatus.ACTIVE,
				PostVisibility.PUBLIC,
				List.of("java", "spring\"boot", "line\nbreak"),
				null,
				null,
				null,
				false,
				null,
				null);

		PostReadModel post = assembler.create(metadata, "body", "blog/a-post/index.md", "commit-a",
				"checksum-a", SYNCED_AT);

		assertThat(post.getTagsJson()).isEqualTo("[\"java\", \"spring\\\"boot\", \"line\\nbreak\"]");
	}

	private static PostMetadataMapping publishedMetadata() {
		return new PostMetadataMapping(
				"A post",
				"a-post",
				LocalDate.of(2026, 7, 3),
				"A useful post.",
				"blog",
				PostSourceStatus.PUBLISHED,
				PostSyncStatus.ACTIVE,
				PostVisibility.PUBLIC,
				List.of("java", "spring"),
				LocalDate.of(2026, 7, 4),
				"./assets/thumbnail.png",
				"Houkago",
				true,
				"boj",
				"1002");
	}

	private static PostMetadataMapping draftMetadata() {
		return new PostMetadataMapping(
				"Draft post",
				"a-post",
				LocalDate.of(2026, 7, 3),
				"A draft post.",
				"blog",
				PostSourceStatus.DRAFT,
				PostSyncStatus.ACTIVE,
				PostVisibility.PRIVATE,
				List.of("draft"),
				null,
				null,
				null,
				false,
				null,
				null);
	}
}
