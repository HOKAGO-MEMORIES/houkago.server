package com.houkago.server.content.post.readmodel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

import com.houkago.server.content.post.checksum.PostChecksumCalculator;
import com.houkago.server.content.post.metadata.InvalidPostMetadataException;
import com.houkago.server.content.post.metadata.PostMetadataInput;
import com.houkago.server.content.post.metadata.PostMetadataMapper;
import com.houkago.server.content.post.policy.PostSourceStatus;
import com.houkago.server.content.post.policy.PostSyncStatus;
import com.houkago.server.content.post.policy.PostVisibility;
import com.houkago.server.content.post.source.ParsedPostCandidate;

@Testcontainers
@DataJpaTest(properties = {
		"spring.jpa.hibernate.ddl-auto=validate",
		"spring.flyway.enabled=true"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({
		PostMetadataMapper.class,
		PostChecksumCalculator.class,
		PostReadModelAssembler.class,
		PostReadModelCandidateProcessor.class,
		PostReadModelUpsertConfiguration.class
})
class PostReadModelUpsertServiceIntegrationTest {

	private static final Instant SYNCED_AT = Instant.parse("2026-07-04T00:00:00Z");

	@Container
	@ServiceConnection
	static final MySQLContainer mysql = new MySQLContainer("mysql:8.4.0");

	@Autowired
	private PostReadModelRepository repository;

	@Autowired
	private PostReadModelUpsertService upsertService;

	@Test
	void createsAndSavesWhenExistingRowDoesNotExist() {
		ParsedPostCandidate candidate = candidate("create-post", "blog/create-post/index.md", "body");

		PostReadModelUpsertResult result = upsertService.upsert(candidate, "commit-a", SYNCED_AT);

		assertThat(result.status()).isEqualTo(PostReadModelUpsertStatus.CREATED);
		assertThat(result.post().getId()).isNotNull();
		assertThat(repository.findBySlug("create-post")).hasValueSatisfying(post -> {
			assertThat(post.getSourcePath()).isEqualTo("blog/create-post/index.md");
			assertThat(post.getCommitHash()).isEqualTo("commit-a");
			assertThat(post.getRawBody()).isEqualTo("body");
			assertThat(post.getSyncedAt()).isEqualTo(SYNCED_AT);
		});
	}

	@Test
	void updatesWhenExistingRowIsFoundBySourcePath() {
		PostReadModel existing = repository.save(samplePost("old-slug", "blog/source-path-post/index.md"));
		ParsedPostCandidate candidate = candidate("new-slug", "blog/source-path-post/index.md", "new body");

		PostReadModelUpsertResult result = upsertService.upsert(candidate, "commit-b",
				Instant.parse("2026-07-04T01:00:00Z"));

		assertThat(result.status()).isEqualTo(PostReadModelUpsertStatus.UPDATED);
		assertThat(result.post().getId()).isEqualTo(existing.getId());
		assertThat(repository.findBySourcePath("blog/source-path-post/index.md")).hasValueSatisfying(post -> {
			assertThat(post.getSlug()).isEqualTo("new-slug");
			assertThat(post.getRawBody()).isEqualTo("new body");
			assertThat(post.getCommitHash()).isEqualTo("commit-b");
		});
	}

	@Test
	void updatesWhenExistingRowIsFoundBySlugAndSourcePathMoved() {
		PostReadModel existing = repository.save(samplePost("moved-post", "blog/old-path/index.md"));
		ParsedPostCandidate candidate = candidate("moved-post", "blog/new-path/index.md", "moved body");

		PostReadModelUpsertResult result = upsertService.upsert(candidate, "commit-c", SYNCED_AT);

		assertThat(result.status()).isEqualTo(PostReadModelUpsertStatus.UPDATED);
		assertThat(result.post().getId()).isEqualTo(existing.getId());
		assertThat(repository.findBySourcePath("blog/old-path/index.md")).isEmpty();
		assertThat(repository.findBySourcePath("blog/new-path/index.md")).hasValueSatisfying(post -> {
			assertThat(post.getSlug()).isEqualTo("moved-post");
			assertThat(post.getRawBody()).isEqualTo("moved body");
		});
	}

	@Test
	void updatesWhenSourcePathAndSlugPointToSameRowId() {
		PostReadModel existing = repository.save(samplePost("same-row-post", "blog/same-row-post/index.md"));
		ParsedPostCandidate candidate = candidate("same-row-post", "blog/same-row-post/index.md", "same row body");

		PostReadModelUpsertResult result = upsertService.upsert(candidate, "commit-d", SYNCED_AT);

		assertThat(result.status()).isEqualTo(PostReadModelUpsertStatus.UPDATED);
		assertThat(result.post().getId()).isEqualTo(existing.getId());
		assertThat(result.post().getRawBody()).isEqualTo("same row body");
	}

	@Test
	void touchesWhenExistingChecksumMatchesCandidateChecksum() {
		ParsedPostCandidate candidate = candidate("touched-post", "blog/touched-post/index.md", "fresh body");
		String checksum = expectedChecksum(candidate);
		PostReadModel existing = samplePost("touched-post", "blog/touched-post/index.md");
		existing.setTitle("Existing title should remain");
		existing.setDescription("Existing description should remain.");
		existing.setCategory("blog");
		existing.setTagsJson("[\"old-tag\"]");
		existing.setRawBody("old body should remain");
		existing.setChecksum(checksum);
		existing.setCommitHash("old-commit");
		existing.setSyncedAt(Instant.parse("2026-07-01T00:00:00Z"));
		repository.save(existing);
		Instant nextSyncedAt = Instant.parse("2026-07-04T02:00:00Z");

		PostReadModelUpsertResult result = upsertService.upsert(candidate, "commit-touch", nextSyncedAt);

		assertThat(result.status()).isEqualTo(PostReadModelUpsertStatus.TOUCHED);
		assertThat(repository.findBySlug("touched-post")).hasValueSatisfying(post -> {
			assertThat(post.getCommitHash()).isEqualTo("commit-touch");
			assertThat(post.getSyncedAt()).isEqualTo(nextSyncedAt);
			assertThat(post.getTitle()).isEqualTo("Existing title should remain");
			assertThat(post.getDescription()).isEqualTo("Existing description should remain.");
			assertThat(post.getTagsJson()).isEqualTo("[\"old-tag\"]");
			assertThat(post.getRawBody()).isEqualTo("old body should remain");
			assertThat(post.getChecksum()).isEqualTo(checksum);
			assertThat(post.getSourcePath()).isEqualTo("blog/touched-post/index.md");
			assertThat(post.getSourceStatus()).isEqualTo(PostSourceStatus.PUBLISHED);
			assertThat(post.getSyncStatus()).isEqualTo(PostSyncStatus.ACTIVE);
			assertThat(post.getVisibility()).isEqualTo(PostVisibility.PUBLIC);
		});
	}

	@Test
	void conflictWhenSourcePathAndSlugPointToDifferentRows() {
		PostReadModel sourcePathRow = repository.save(samplePost("source-path-row", "blog/conflict-source/index.md"));
		PostReadModel slugRow = repository.save(samplePost("conflict-slug", "blog/conflict-slug/index.md"));
		ParsedPostCandidate candidate = candidate("conflict-slug", "blog/conflict-source/index.md", "body");

		assertThatThrownBy(() -> upsertService.upsert(candidate, "commit-e", SYNCED_AT))
				.isInstanceOf(PostReadModelUpsertConflictException.class)
				.hasMessageContaining("blog/conflict-source/index.md")
				.hasMessageContaining("conflict-slug")
				.hasMessageContaining(String.valueOf(sourcePathRow.getId()))
				.hasMessageContaining(String.valueOf(slugRow.getId()));
	}

	@Test
	void invalidMetadataExceptionPropagates() {
		ParsedPostCandidate candidate = new ParsedPostCandidate(
				"blog/invalid-post/index.md",
				new PostMetadataInput(
						null,
						"invalid-post",
						"2026-07-04",
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

		assertThatThrownBy(() -> upsertService.upsert(candidate, "commit-f", SYNCED_AT))
				.isInstanceOf(InvalidPostMetadataException.class)
				.hasMessageContaining("title is required");
	}

	@Test
	void blankCommitHashRejected() {
		assertThatThrownBy(() -> upsertService.upsert(candidate("blank-commit", "blog/blank-commit/index.md", "body"),
				"   ", SYNCED_AT))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("commitHash is required");
	}

	@Test
	void nullSyncedAtRejected() {
		assertThatThrownBy(() -> upsertService.upsert(candidate("null-synced", "blog/null-synced/index.md", "body"),
				"commit-g", null))
				.isInstanceOf(NullPointerException.class)
				.hasMessageContaining("syncedAt is required");
	}

	@Test
	void savePersistsUpdatedEntity() {
		repository.save(samplePost("save-called", "blog/save-called/index.md"));
		ParsedPostCandidate candidate = candidate("save-called", "blog/save-called/index.md", "saved body");

		upsertService.upsert(candidate, "commit-h", SYNCED_AT);

		assertThat(repository.findBySlug("save-called")).hasValueSatisfying(post -> {
			assertThat(post.getRawBody()).isEqualTo("saved body");
			assertThat(post.getCommitHash()).isEqualTo("commit-h");
		});
	}

	private static ParsedPostCandidate candidate(String slug, String sourcePath, String rawBody) {
		return new ParsedPostCandidate(
				sourcePath,
				new PostMetadataInput(
						"Post " + slug,
						slug,
						"2026-07-04",
						"A useful post.",
						"blog",
						"published",
						List.of("java", "spring"),
						null,
						null,
						null,
						false,
						null,
						null),
				rawBody);
	}

	private static String expectedChecksum(ParsedPostCandidate candidate) {
		PostMetadataMapper metadataMapper = new PostMetadataMapper();
		PostChecksumCalculator checksumCalculator = new PostChecksumCalculator();
		return checksumCalculator.calculate(
				com.houkago.server.content.post.checksum.PostChecksumInput.from(
						metadataMapper.map(candidate.metadataInput()),
						candidate.rawBody()));
	}

	private static PostReadModel samplePost(String slug, String sourcePath) {
		PostReadModel post = new PostReadModel();
		post.setSlug(slug);
		post.setTitle("Post " + slug);
		post.setDescription("Existing post.");
		post.setCategory("blog");
		post.setTagsJson("[\"java\"]");
		post.setPostDate(LocalDate.of(2026, 7, 1));
		post.setPostUpdatedDate(null);
		post.setThumbnail(null);
		post.setSeries(null);
		post.setFeatured(false);
		post.setPlatform(null);
		post.setProblemId(null);
		post.setSourceRepository("houkago.posts");
		post.setSourcePath(sourcePath);
		post.setSourceUrl(null);
		post.setRawBody("old body");
		post.setCommitHash("old-commit");
		post.setChecksum("checksum-" + slug);
		post.setSourceStatus(PostSourceStatus.PUBLISHED);
		post.setSyncStatus(PostSyncStatus.ACTIVE);
		post.setVisibility(PostVisibility.PUBLIC);
		post.setSyncedAt(Instant.parse("2026-07-01T00:00:00Z"));
		return post;
	}
}
