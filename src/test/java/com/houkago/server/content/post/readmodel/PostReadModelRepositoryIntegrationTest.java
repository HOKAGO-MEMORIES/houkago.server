package com.houkago.server.content.post.readmodel;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
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
class PostReadModelRepositoryIntegrationTest {

	@Container
	@ServiceConnection
	static final MySQLContainer mysql = new MySQLContainer("mysql:8.4.0");

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private PostReadModelRepository repository;

	@Test
	void flywayCreatesPostReadModelTable() {
		Integer tableCount = jdbcTemplate.queryForObject("""
				SELECT COUNT(*)
				FROM information_schema.tables
				WHERE table_schema = DATABASE()
				AND table_name = 'post_read_models'
				""", Integer.class);

		assertThat(tableCount).isEqualTo(1);
	}

	@Test
	void repositorySavesAndFindsPostReadModelBySlug() {
		PostReadModel saved = repository.save(samplePost("boj-1002", "algorithm", PostSourceStatus.PUBLISHED,
				PostSyncStatus.ACTIVE, PostVisibility.PUBLIC));

		assertThat(saved.getId()).isNotNull();
		assertThat(repository.findBySlug("boj-1002")).hasValueSatisfying(post -> {
			assertThat(post.getTitle()).isEqualTo("BOJ 1002 - Turret");
			assertThat(post.getTagsJson()).isEqualTo("[\"algorithm\",\"geometry\"]");
			assertThat(post.getRawBody()).contains("problem notes");
			assertThat(post.getSourceStatus()).isEqualTo(PostSourceStatus.PUBLISHED);
			assertThat(post.getSyncStatus()).isEqualTo(PostSyncStatus.ACTIVE);
			assertThat(post.getVisibility()).isEqualTo(PostVisibility.PUBLIC);
		});
	}

	@Test
	void repositoryFindsPublicVisiblePostsByCategory() {
		repository.save(samplePost("boj-1002", "algorithm", PostSourceStatus.PUBLISHED, PostSyncStatus.ACTIVE,
				PostVisibility.PUBLIC));
		repository.save(samplePost("spring-notes", "blog", PostSourceStatus.PUBLISHED, PostSyncStatus.ACTIVE,
				PostVisibility.PUBLIC));

		Page<PostReadModel> page = repository
				.findByCategoryAndSourceStatusAndSyncStatusAndVisibilityOrderByPostDateDescSlugAsc(
						"algorithm",
						PostSourceStatus.PUBLISHED,
						PostSyncStatus.ACTIVE,
						PostVisibility.PUBLIC,
						PageRequest.of(0, 10));

		assertThat(page.getContent()).extracting(PostReadModel::getSlug).containsExactly("boj-1002");
	}

	private static PostReadModel samplePost(
			String slug,
			String category,
			PostSourceStatus sourceStatus,
			PostSyncStatus syncStatus,
			PostVisibility visibility) {
		PostReadModel post = new PostReadModel();
		post.setSlug(slug);
		post.setTitle("BOJ 1002 - Turret");
		post.setDescription("Geometry problem notes.");
		post.setCategory(category);
		post.setTagsJson("[\"algorithm\",\"geometry\"]");
		post.setPostDate(LocalDate.of(2026, 7, 1));
		post.setPostUpdatedDate(null);
		post.setThumbnail("./assets/thumbnail.png");
		post.setSeries(null);
		post.setFeatured(false);
		post.setPlatform("boj");
		post.setProblemId("1002");
		post.setSourceRepository("houkago.posts");
		post.setSourcePath(category + "/" + slug + "/index.md");
		post.setSourceUrl(null);
		post.setRawBody("## problem notes\n\nraw markdown body");
		post.setCommitHash("0123456789abcdef0123456789abcdef01234567");
		post.setChecksum("checksum-" + slug);
		post.setSourceStatus(sourceStatus);
		post.setSyncStatus(syncStatus);
		post.setVisibility(visibility);
		post.setSyncedAt(Instant.parse("2026-07-01T00:00:00Z"));
		return post;
	}
}
