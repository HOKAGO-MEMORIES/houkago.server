package com.houkago.server.content.post.sync;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

import com.houkago.server.content.post.policy.PostSourceStatus;
import com.houkago.server.content.post.policy.PostSyncStatus;
import com.houkago.server.content.post.policy.PostVisibility;
import com.houkago.server.content.post.readmodel.PostReadModelRepository;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
		"spring.jpa.hibernate.ddl-auto=validate",
		"spring.flyway.enabled=true",
		"houkago.resync.enabled=false",
		"houkago.assets.public-origin=https://assets.example.test"
})
class PostManualFullResyncServiceIntegrationTest {

	private static final Instant FIRST_SYNCED_AT = Instant.parse("2026-08-17T00:00:00Z");
	private static final Instant SECOND_SYNCED_AT = Instant.parse("2026-08-17T01:00:00Z");
	private static final Instant THIRD_SYNCED_AT = Instant.parse("2026-08-17T02:00:00Z");

	@Container
	@ServiceConnection
	static final MySQLContainer mysql = new MySQLContainer("mysql:8.4.0");

	@Autowired
	private PostManualFullResyncService resyncService;

	@Autowired
	private PostReadModelRepository repository;

	@Autowired
	private TestRestTemplate restTemplate;

	@TempDir
	private Path postsRoot;

	@BeforeEach
	void setUp() {
		repository.deleteAll();
	}

	@Test
	void sameChecksumRelocationUpdatesPathAndSurvivesRetirement() throws IOException {
		Path original = writePost("blog/original-path/index.md", "relocated-post", "Unchanged body.\n");

		PostManualFullResyncResult initial = resyncService.resync(postsRoot, "commit-initial", FIRST_SYNCED_AT);
		String initialChecksum = repository.findBySlug("relocated-post").orElseThrow().getChecksum();
		Path relocated = postsRoot.resolve("blog/relocated-path/index.md");
		Files.createDirectories(relocated.getParent());
		Files.move(original, relocated);

		PostManualFullResyncResult result = resyncService.resync(postsRoot, "commit-relocated", SECOND_SYNCED_AT);

		assertThat(initial.createdCount()).isEqualTo(1);
		assertThat(result.updatedCount()).isEqualTo(1);
		assertThat(result.touchedCount()).isZero();
		assertThat(result.deletedCount()).isZero();
		assertThat(repository.findBySourcePath("blog/original-path/index.md")).isEmpty();
		assertThat(repository.findBySlug("relocated-post")).hasValueSatisfying(post -> {
			assertThat(post.getSourcePath()).isEqualTo("blog/relocated-path/index.md");
			assertThat(post.getChecksum()).isEqualTo(initialChecksum);
			assertThat(post.getSyncStatus()).isEqualTo(PostSyncStatus.ACTIVE);
			assertThat(post.getVisibility()).isEqualTo(PostVisibility.PUBLIC);
		});
	}

	@Test
	void sameChecksumReappearanceRestoresLifecycleAndPublicApiVisibility() throws IOException {
		Path restoredPost = writePost("blog/restored-post/index.md", "restored-post", "Restored body.\n");
		writePost("blog/keeper-post/index.md", "keeper-post", "Keeper body.\n");
		resyncService.resync(postsRoot, "commit-initial", FIRST_SYNCED_AT);
		String initialChecksum = repository.findBySlug("restored-post").orElseThrow().getChecksum();

		Files.delete(restoredPost);
		PostManualFullResyncResult retired = resyncService.resync(postsRoot, "commit-retired", SECOND_SYNCED_AT);

		assertThat(retired.deletedCount()).isEqualTo(1);
		assertThat(repository.findBySlug("restored-post")).hasValueSatisfying(post -> {
			assertThat(post.getSyncStatus()).isEqualTo(PostSyncStatus.DELETED);
			assertThat(post.getVisibility()).isEqualTo(PostVisibility.PRIVATE);
			assertThat(post.getChecksum()).isEqualTo(initialChecksum);
		});
		assertThat(restTemplate.getForEntity("/api/posts/restored-post", String.class).getStatusCode())
				.isEqualTo(HttpStatus.NOT_FOUND);

		writePost("blog/restored-post/index.md", "restored-post", "Restored body.\n");
		PostManualFullResyncResult restored = resyncService.resync(postsRoot, "commit-restored", THIRD_SYNCED_AT);

		assertThat(restored.updatedCount()).isEqualTo(1);
		assertThat(restored.touchedCount()).isEqualTo(1);
		assertThat(restored.deletedCount()).isZero();
		assertThat(repository.findBySlug("restored-post")).hasValueSatisfying(post -> {
			assertThat(post.getChecksum()).isEqualTo(initialChecksum);
			assertThat(post.getSourceStatus()).isEqualTo(PostSourceStatus.PUBLISHED);
			assertThat(post.getSyncStatus()).isEqualTo(PostSyncStatus.ACTIVE);
			assertThat(post.getVisibility()).isEqualTo(PostVisibility.PUBLIC);
			assertThat(post.getCommitHash()).isEqualTo("commit-restored");
		});
		ResponseEntity<String> listResponse = restTemplate.getForEntity("/api/posts?size=10", String.class);
		ResponseEntity<String> detailResponse = restTemplate.getForEntity(
				"/api/posts/restored-post",
				String.class);
		assertThat(listResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(listResponse.getBody()).contains("restored-post");
		assertThat(detailResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(detailResponse.getBody()).contains("Restored body.");
	}

	private Path writePost(String sourcePath, String slug, String rawBody) throws IOException {
		Path post = postsRoot.resolve(sourcePath);
		Files.createDirectories(post.getParent());
		Files.writeString(post, markdown(slug, rawBody), StandardCharsets.UTF_8);
		return post;
	}

	private static String markdown(String slug, String rawBody) {
		return """
				---
				title: Post %s
				slug: %s
				date: 2026-08-17
				description: A useful post.
				category: blog
				status: published
				tags:
				  - java
				---
				%s""".formatted(slug, slug, rawBody);
	}
}
