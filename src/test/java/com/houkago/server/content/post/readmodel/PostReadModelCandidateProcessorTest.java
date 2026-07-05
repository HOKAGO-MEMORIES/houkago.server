package com.houkago.server.content.post.readmodel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.houkago.server.content.post.checksum.PostChecksumCalculator;
import com.houkago.server.content.post.checksum.PostChecksumInput;
import com.houkago.server.content.post.metadata.InvalidPostMetadataException;
import com.houkago.server.content.post.metadata.PostMetadataInput;
import com.houkago.server.content.post.metadata.PostMetadataMapper;
import com.houkago.server.content.post.metadata.PostMetadataMapping;
import com.houkago.server.content.post.policy.PostSourceStatus;
import com.houkago.server.content.post.policy.PostSyncStatus;
import com.houkago.server.content.post.policy.PostVisibility;
import com.houkago.server.content.post.source.ParsedPostCandidate;

class PostReadModelCandidateProcessorTest {

	private static final Instant SYNCED_AT = Instant.parse("2026-07-03T00:00:00Z");

	private final PostMetadataMapper metadataMapper = new PostMetadataMapper();
	private final PostChecksumCalculator checksumCalculator = new PostChecksumCalculator();
	private final PostReadModelAssembler assembler = new PostReadModelAssembler();
	private final PostReadModelCandidateProcessor processor = new PostReadModelCandidateProcessor(
			metadataMapper,
			checksumCalculator,
			assembler);

	@Test
	void createsPostReadModelFromValidCandidate() {
		PostReadModel post = processor.create(publishedCandidate(), "commit-a", SYNCED_AT);

		assertThat(post.getSlug()).isEqualTo("my-post");
		assertThat(post.getRawBody()).isEqualTo("## Body");
		assertThat(post.getSourcePath()).isEqualTo("blog/my-post/index.md");
	}

	@Test
	void appliesMetadataMappingToEntity() {
		PostReadModel post = processor.create(publishedCandidate(), "commit-a", SYNCED_AT);

		assertThat(post.getTitle()).isEqualTo("A post");
		assertThat(post.getDescription()).isEqualTo("A useful post.");
		assertThat(post.getCategory()).isEqualTo("blog");
		assertThat(post.getPostDate()).isEqualTo(LocalDate.of(2026, 7, 3));
		assertThat(post.getPostUpdatedDate()).isEqualTo(LocalDate.of(2026, 7, 4));
		assertThat(post.getThumbnail()).isEqualTo("./assets/thumbnail.png");
		assertThat(post.getSeries()).isEqualTo("Houkago");
		assertThat(post.isFeatured()).isTrue();
		assertThat(post.getPlatform()).isEqualTo("boj");
		assertThat(post.getProblemId()).isEqualTo("1000");
		assertThat(post.getSourceStatus()).isEqualTo(PostSourceStatus.PUBLISHED);
		assertThat(post.getSyncStatus()).isEqualTo(PostSyncStatus.ACTIVE);
		assertThat(post.getVisibility()).isEqualTo(PostVisibility.PUBLIC);
	}

	@Test
	void appliesRawBodySourcePathCommitHashChecksumAndSyncedAt() {
		ParsedPostCandidate candidate = publishedCandidate();

		PostReadModel post = processor.create(candidate, "commit-a", SYNCED_AT);

		assertThat(post.getRawBody()).isEqualTo(candidate.rawBody());
		assertThat(post.getSourcePath()).isEqualTo(candidate.sourcePath());
		assertThat(post.getCommitHash()).isEqualTo("commit-a");
		assertThat(post.getChecksum()).isEqualTo(expectedChecksum(candidate));
		assertThat(post.getSyncedAt()).isEqualTo(SYNCED_AT);
	}

	@Test
	void checksumIsCalculatedWithPostChecksumCalculator() {
		ParsedPostCandidate first = publishedCandidate("## First body");
		ParsedPostCandidate second = publishedCandidate("## Second body");

		PostReadModel firstPost = processor.create(first, "commit-a", SYNCED_AT);
		PostReadModel secondPost = processor.create(second, "commit-a", SYNCED_AT);

		assertThat(firstPost.getChecksum()).isEqualTo(expectedChecksum(first));
		assertThat(secondPost.getChecksum()).isEqualTo(expectedChecksum(second));
		assertThat(firstPost.getChecksum()).isNotEqualTo(secondPost.getChecksum());
	}

	@Test
	void updatePreservesExistingEntityObject() {
		PostReadModel existing = processor.create(publishedCandidate(), "commit-a", SYNCED_AT);

		PostReadModel updated = processor.update(
				existing,
				draftCandidate(),
				"commit-b",
				Instant.parse("2026-07-03T01:00:00Z"));

		assertThat(updated).isSameAs(existing);
	}

	@Test
	void updateChangesMetadataRawBodyChecksumAndSyncedAt() {
		PostReadModel existing = processor.create(publishedCandidate(), "commit-a", SYNCED_AT);
		ParsedPostCandidate candidate = draftCandidate();
		Instant nextSyncedAt = Instant.parse("2026-07-03T01:00:00Z");

		processor.update(existing, candidate, "commit-b", nextSyncedAt);

		assertThat(existing.getTitle()).isEqualTo("Draft post");
		assertThat(existing.getSourceStatus()).isEqualTo(PostSourceStatus.DRAFT);
		assertThat(existing.getVisibility()).isEqualTo(PostVisibility.PRIVATE);
		assertThat(existing.getRawBody()).isEqualTo("draft body");
		assertThat(existing.getSourcePath()).isEqualTo("blog/draft-post/index.md");
		assertThat(existing.getCommitHash()).isEqualTo("commit-b");
		assertThat(existing.getChecksum()).isEqualTo(expectedChecksum(candidate));
		assertThat(existing.getSyncedAt()).isEqualTo(nextSyncedAt);
	}

	@Test
	void nullCandidateRejected() {
		assertThatThrownBy(() -> processor.create(null, "commit-a", SYNCED_AT))
				.isInstanceOf(NullPointerException.class)
				.hasMessageContaining("candidate is required");
	}

	@Test
	void nullExistingRejectedForUpdate() {
		assertThatThrownBy(() -> processor.update(null, publishedCandidate(), "commit-a", SYNCED_AT))
				.isInstanceOf(NullPointerException.class)
				.hasMessageContaining("existing post read model is required");
	}

	@Test
	void blankCommitHashRejected() {
		assertThatThrownBy(() -> processor.create(publishedCandidate(), "   ", SYNCED_AT))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("commitHash is required");
	}

	@Test
	void nullSyncedAtRejected() {
		assertThatThrownBy(() -> processor.create(publishedCandidate(), "commit-a", null))
				.isInstanceOf(NullPointerException.class)
				.hasMessageContaining("syncedAt is required");
	}

	@Test
	void invalidMetadataExceptionPropagates() {
		ParsedPostCandidate candidate = new ParsedPostCandidate(
				"blog/my-post/index.md",
				new PostMetadataInput(
						null,
						"my-post",
						"2026-07-03",
						"A useful post.",
						"blog",
						"published",
						List.of("java"),
						null,
						null,
						null,
						null,
						null,
						null),
				"body");

		assertThatThrownBy(() -> processor.create(candidate, "commit-a", SYNCED_AT))
				.isInstanceOf(InvalidPostMetadataException.class)
				.hasMessageContaining("title is required");
	}

	private String expectedChecksum(ParsedPostCandidate candidate) {
		PostMetadataMapping metadata = metadataMapper.map(candidate.metadataInput());
		return checksumCalculator.calculate(PostChecksumInput.from(metadata, candidate.rawBody()));
	}

	private static ParsedPostCandidate publishedCandidate() {
		return publishedCandidate("## Body");
	}

	private static ParsedPostCandidate publishedCandidate(String rawBody) {
		return new ParsedPostCandidate(
				"blog/my-post/index.md",
				new PostMetadataInput(
						"A post",
						"my-post",
						"2026-07-03",
						"A useful post.",
						"blog",
						"published",
						List.of("java", "spring"),
						"2026-07-04",
						"./assets/thumbnail.png",
						"Houkago",
						true,
						"boj",
						"1000"),
				rawBody);
	}

	private static ParsedPostCandidate draftCandidate() {
		return new ParsedPostCandidate(
				"blog/draft-post/index.md",
				new PostMetadataInput(
						"Draft post",
						"draft-post",
						"2026-07-05",
						"A draft post.",
						"blog",
						"draft",
						List.of("draft"),
						null,
						null,
						null,
						false,
						null,
						null),
				"draft body");
	}
}
