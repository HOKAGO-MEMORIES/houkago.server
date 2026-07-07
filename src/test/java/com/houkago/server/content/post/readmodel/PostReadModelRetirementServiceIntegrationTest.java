package com.houkago.server.content.post.readmodel;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

import com.houkago.server.content.post.policy.PostSourceStatus;
import com.houkago.server.content.post.policy.PostSyncStatus;
import com.houkago.server.content.post.policy.PostVisibility;

@Testcontainers
@DataJpaTest(properties = {
		"spring.jpa.hibernate.ddl-auto=validate",
		"spring.flyway.enabled=true"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({
		PostReadModelAssembler.class,
		PostReadModelRetirementService.class
})
class PostReadModelRetirementServiceIntegrationTest {

	private static final String COMMIT_HASH = "commit-delete";
	private static final Instant SYNCED_AT = Instant.parse("2026-07-04T00:00:00Z");

	@Container
	@ServiceConnection
	static final MySQLContainer mysql = new MySQLContainer("mysql:8.4.0");

	@Autowired
	private PostReadModelRepository repository;

	@Autowired
	private PostReadModelRetirementService retirementService;

	@Test
	void retiresActiveRowsMissingFromCurrentSourcePaths() {
		repository.save(samplePost("kept-post", "blog/kept-post/index.md", PostSyncStatus.ACTIVE));
		repository.save(samplePost("missing-post", "blog/missing-post/index.md", PostSyncStatus.ACTIVE));

		int deletedCount = retirementService.retireMissingSources(
				Set.of("blog/kept-post/index.md"),
				COMMIT_HASH,
				SYNCED_AT);

		assertThat(deletedCount).isEqualTo(1);
		assertThat(repository.findBySlug("kept-post")).hasValueSatisfying(post -> {
			assertThat(post.getSyncStatus()).isEqualTo(PostSyncStatus.ACTIVE);
			assertThat(post.getVisibility()).isEqualTo(PostVisibility.PUBLIC);
		});
		assertThat(repository.findBySlug("missing-post")).hasValueSatisfying(post -> {
			assertThat(post.getSyncStatus()).isEqualTo(PostSyncStatus.DELETED);
			assertThat(post.getVisibility()).isEqualTo(PostVisibility.PRIVATE);
			assertThat(post.getCommitHash()).isEqualTo(COMMIT_HASH);
			assertThat(post.getSyncedAt()).isEqualTo(SYNCED_AT);
		});
	}

	@Test
	void alreadyDeletedRowsAreNotRetiredAgain() {
		PostReadModel deleted = samplePost("already-deleted", "blog/already-deleted/index.md", PostSyncStatus.DELETED);
		deleted.setVisibility(PostVisibility.PRIVATE);
		deleted.setCommitHash("old-commit");
		deleted.setSyncedAt(Instant.parse("2026-07-01T00:00:00Z"));
		repository.save(deleted);

		int deletedCount = retirementService.retireMissingSources(
				Set.of("blog/current-post/index.md"),
				COMMIT_HASH,
				SYNCED_AT);

		assertThat(deletedCount).isZero();
		assertThat(repository.findBySlug("already-deleted")).hasValueSatisfying(post -> {
			assertThat(post.getSyncStatus()).isEqualTo(PostSyncStatus.DELETED);
			assertThat(post.getVisibility()).isEqualTo(PostVisibility.PRIVATE);
			assertThat(post.getCommitHash()).isEqualTo("old-commit");
			assertThat(post.getSyncedAt()).isEqualTo(Instant.parse("2026-07-01T00:00:00Z"));
		});
	}

	@Test
	void deletingMissingRowPreservesContentMetadataChecksumAndSourcePath() {
		PostReadModel missing = samplePost("preserved-post", "blog/preserved-post/index.md", PostSyncStatus.ACTIVE);
		repository.save(missing);

		retirementService.retireMissingSources(Set.of("blog/current-post/index.md"), COMMIT_HASH, SYNCED_AT);

		assertThat(repository.findBySlug("preserved-post")).hasValueSatisfying(post -> {
			assertThat(post.getSourceStatus()).isEqualTo(PostSourceStatus.PUBLISHED);
			assertThat(post.getChecksum()).isEqualTo("checksum-preserved-post");
			assertThat(post.getRawBody()).isEqualTo("raw body for preserved-post");
			assertThat(post.getSlug()).isEqualTo("preserved-post");
			assertThat(post.getTitle()).isEqualTo("Post preserved-post");
			assertThat(post.getDescription()).isEqualTo("Description for preserved-post");
			assertThat(post.getCategory()).isEqualTo("blog");
			assertThat(post.getTagsJson()).isEqualTo("[\"java\"]");
			assertThat(post.getPostDate()).isEqualTo(LocalDate.of(2026, 7, 1));
			assertThat(post.getPostUpdatedDate()).isEqualTo(LocalDate.of(2026, 7, 2));
			assertThat(post.getThumbnail()).isEqualTo("./assets/thumbnail.png");
			assertThat(post.getSeries()).isEqualTo("Series");
			assertThat(post.isFeatured()).isTrue();
			assertThat(post.getPlatform()).isEqualTo("boj");
			assertThat(post.getProblemId()).isEqualTo("1000");
			assertThat(post.getSourcePath()).isEqualTo("blog/preserved-post/index.md");
		});
	}

	@Test
	void emptyCurrentSourcePathsDoNotDeleteEverything() {
		repository.save(samplePost("safe-post", "blog/safe-post/index.md", PostSyncStatus.ACTIVE));

		int deletedCount = retirementService.retireMissingSources(Set.of(), COMMIT_HASH, SYNCED_AT);

		assertThat(deletedCount).isZero();
		assertThat(repository.findBySlug("safe-post")).hasValueSatisfying(post -> {
			assertThat(post.getSyncStatus()).isEqualTo(PostSyncStatus.ACTIVE);
			assertThat(post.getVisibility()).isEqualTo(PostVisibility.PUBLIC);
		});
	}

	private static PostReadModel samplePost(String slug, String sourcePath, PostSyncStatus syncStatus) {
		PostReadModel post = new PostReadModel();
		post.setSlug(slug);
		post.setTitle("Post " + slug);
		post.setDescription("Description for " + slug);
		post.setCategory("blog");
		post.setTagsJson("[\"java\"]");
		post.setPostDate(LocalDate.of(2026, 7, 1));
		post.setPostUpdatedDate(LocalDate.of(2026, 7, 2));
		post.setThumbnail("./assets/thumbnail.png");
		post.setSeries("Series");
		post.setFeatured(true);
		post.setPlatform("boj");
		post.setProblemId("1000");
		post.setSourceRepository("houkago.posts");
		post.setSourcePath(sourcePath);
		post.setSourceUrl(null);
		post.setRawBody("raw body for " + slug);
		post.setCommitHash("old-commit");
		post.setChecksum("checksum-" + slug);
		post.setSourceStatus(PostSourceStatus.PUBLISHED);
		post.setSyncStatus(syncStatus);
		post.setVisibility(syncStatus == PostSyncStatus.ACTIVE ? PostVisibility.PUBLIC : PostVisibility.PRIVATE);
		post.setSyncedAt(Instant.parse("2026-07-01T00:00:00Z"));
		return post;
	}
}
