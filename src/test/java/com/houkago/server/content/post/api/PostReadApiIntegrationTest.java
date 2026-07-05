package com.houkago.server.content.post.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.jpa.repository.Query;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import com.houkago.server.content.post.policy.PostSourceStatus;
import com.houkago.server.content.post.policy.PostSyncStatus;
import com.houkago.server.content.post.policy.PostVisibility;
import com.houkago.server.content.post.readmodel.PostReadModel;
import com.houkago.server.content.post.readmodel.PostReadModelRepository;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
		"spring.jpa.hibernate.ddl-auto=validate",
		"spring.flyway.enabled=true",
		"houkago.resync.enabled=false"
})
class PostReadApiIntegrationTest {

	private static final Instant SYNCED_AT = Instant.parse("2026-07-04T00:00:00Z");

	@Container
	@ServiceConnection
	static final MySQLContainer mysql = new MySQLContainer("mysql:8.4.0");

	@Autowired
	private TestRestTemplate restTemplate;

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private PostReadModelRepository repository;

	@BeforeEach
	void setUp() {
		repository.deleteAll();
	}

	@Test
	void listReturnsOnlyPublicVisiblePosts() throws Exception {
		repository.save(publicPost("public-post", LocalDate.of(2026, 7, 4), "public body"));
		repository.save(post("draft-post", LocalDate.of(2026, 7, 5), PostSourceStatus.DRAFT,
				PostSyncStatus.ACTIVE, PostVisibility.PRIVATE));
		repository.save(post("archived-post", LocalDate.of(2026, 7, 5), PostSourceStatus.ARCHIVED,
				PostSyncStatus.ACTIVE, PostVisibility.PRIVATE));
		repository.save(post("private-post", LocalDate.of(2026, 7, 5), PostSourceStatus.PUBLISHED,
				PostSyncStatus.ACTIVE, PostVisibility.PRIVATE));
		repository.save(post("deleted-post", LocalDate.of(2026, 7, 5), PostSourceStatus.PUBLISHED,
				PostSyncStatus.DELETED, PostVisibility.PUBLIC));

		ResponseEntity<String> response = restTemplate.getForEntity("/api/posts", String.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		JsonNode root = objectMapper.readTree(response.getBody());
		assertThat(root.path("content")).hasSize(1);
		assertThat(root.path("content").get(0).path("slug").asText()).isEqualTo("public-post");
		assertThat(response.getBody()).doesNotContain("draft-post")
				.doesNotContain("archived-post")
				.doesNotContain("private-post")
				.doesNotContain("deleted-post");
	}

	@Test
	void listResponseDoesNotIncludeRawBody() {
		repository.save(publicPost("public-post", LocalDate.of(2026, 7, 4), "hidden raw markdown body"));

		ResponseEntity<String> response = restTemplate.getForEntity("/api/posts", String.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(response.getBody()).doesNotContain("rawBody")
				.doesNotContain("hidden raw markdown body");
	}

	@Test
	void listUsesProjectionQueryThatDoesNotSelectRawBody() throws Exception {
		Method method = PostReadModelRepository.class.getDeclaredMethod(
				"findPublicPostSummaries",
				PostSourceStatus.class,
				PostSyncStatus.class,
				PostVisibility.class,
				org.springframework.data.domain.Pageable.class);
		Query query = method.getAnnotation(Query.class);

		assertThat(query).isNotNull();
		assertThat(query.value()).contains("PostReadSummaryProjection");
		assertThat(query.value()).doesNotContain("rawBody")
				.doesNotContain("raw_body");
	}

	@Test
	void listSortsByPostDateDescThenIdDesc() throws Exception {
		PostReadModel firstSameDate = repository.save(publicPost("first-same-date",
				LocalDate.of(2026, 7, 4), "first body"));
		PostReadModel secondSameDate = repository.save(publicPost("second-same-date",
				LocalDate.of(2026, 7, 4), "second body"));
		PostReadModel older = repository.save(publicPost("older-post", LocalDate.of(2026, 7, 3), "older body"));
		assertThat(firstSameDate.getId()).isLessThan(secondSameDate.getId());
		assertThat(older.getId()).isGreaterThan(secondSameDate.getId());

		ResponseEntity<String> response = restTemplate.getForEntity("/api/posts?size=10", String.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		JsonNode content = objectMapper.readTree(response.getBody()).path("content");
		assertThat(textValues(content, "slug"))
				.containsExactly("second-same-date", "first-same-date", "older-post");
	}

	@Test
	void listReturnsPaginationMetadata() throws Exception {
		repository.save(publicPost("page-post-1", LocalDate.of(2026, 7, 4), "body 1"));
		repository.save(publicPost("page-post-2", LocalDate.of(2026, 7, 3), "body 2"));
		repository.save(publicPost("page-post-3", LocalDate.of(2026, 7, 2), "body 3"));

		ResponseEntity<String> response = restTemplate.getForEntity("/api/posts?page=0&size=2", String.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		JsonNode root = objectMapper.readTree(response.getBody());
		assertThat(root.path("content")).hasSize(2);
		assertThat(root.path("totalElements").asInt()).isEqualTo(3);
		assertThat(root.path("totalPages").asInt()).isEqualTo(2);
		assertThat(root.path("number").asInt()).isZero();
		assertThat(root.path("size").asInt()).isEqualTo(2);
	}

	@Test
	void detailReturnsPublicPostWithRawBody() throws Exception {
		repository.save(publicPost("detail-post", LocalDate.of(2026, 7, 4), "## detail raw body"));

		ResponseEntity<String> response = restTemplate.getForEntity("/api/posts/detail-post", String.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		JsonNode root = objectMapper.readTree(response.getBody());
		assertThat(root.path("slug").asText()).isEqualTo("detail-post");
		assertThat(root.path("rawBody").asText()).isEqualTo("## detail raw body");
		assertThat(textValues(root.path("tags"))).containsExactly("java", "spring");
	}

	@Test
	void detailReturnsNotFoundForMissingSlug() {
		ResponseEntity<String> response = restTemplate.getForEntity("/api/posts/missing-post", String.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
	}

	@Test
	void detailReturnsNotFoundForNonPublicPosts() {
		repository.save(post("draft-post", LocalDate.of(2026, 7, 4), PostSourceStatus.DRAFT,
				PostSyncStatus.ACTIVE, PostVisibility.PRIVATE));
		repository.save(post("archived-post", LocalDate.of(2026, 7, 4), PostSourceStatus.ARCHIVED,
				PostSyncStatus.ACTIVE, PostVisibility.PRIVATE));
		repository.save(post("private-post", LocalDate.of(2026, 7, 4), PostSourceStatus.PUBLISHED,
				PostSyncStatus.ACTIVE, PostVisibility.PRIVATE));
		repository.save(post("deleted-post", LocalDate.of(2026, 7, 4), PostSourceStatus.PUBLISHED,
				PostSyncStatus.DELETED, PostVisibility.PUBLIC));

		assertThat(restTemplate.getForEntity("/api/posts/draft-post", String.class).getStatusCode())
				.isEqualTo(HttpStatus.NOT_FOUND);
		assertThat(restTemplate.getForEntity("/api/posts/archived-post", String.class).getStatusCode())
				.isEqualTo(HttpStatus.NOT_FOUND);
		assertThat(restTemplate.getForEntity("/api/posts/private-post", String.class).getStatusCode())
				.isEqualTo(HttpStatus.NOT_FOUND);
		assertThat(restTemplate.getForEntity("/api/posts/deleted-post", String.class).getStatusCode())
				.isEqualTo(HttpStatus.NOT_FOUND);
	}

	private static PostReadModel publicPost(String slug, LocalDate postDate, String rawBody) {
		PostReadModel post = post(slug, postDate, PostSourceStatus.PUBLISHED, PostSyncStatus.ACTIVE,
				PostVisibility.PUBLIC);
		post.setRawBody(rawBody);
		return post;
	}

	private static PostReadModel post(
			String slug,
			LocalDate postDate,
			PostSourceStatus sourceStatus,
			PostSyncStatus syncStatus,
			PostVisibility visibility) {
		PostReadModel post = newPostReadModel();
		post.setSlug(slug);
		post.setTitle("Post " + slug);
		post.setDescription("Description for " + slug);
		post.setCategory("blog");
		post.setTagsJson("[\"java\", \"spring\"]");
		post.setPostDate(postDate);
		post.setPostUpdatedDate(LocalDate.of(2026, 7, 5));
		post.setThumbnail("./assets/thumbnail.png");
		post.setSeries("Backend MVP");
		post.setFeatured(false);
		post.setPlatform(null);
		post.setProblemId(null);
		post.setSourceRepository("houkago.posts");
		post.setSourcePath("blog/" + slug + "/index.md");
		post.setSourceUrl(null);
		post.setRawBody("raw body for " + slug);
		post.setCommitHash("commit-" + slug);
		post.setChecksum("checksum-" + slug);
		post.setSourceStatus(sourceStatus);
		post.setSyncStatus(syncStatus);
		post.setVisibility(visibility);
		post.setSyncedAt(SYNCED_AT);
		return post;
	}

	private static PostReadModel newPostReadModel() {
		try {
			Constructor<PostReadModel> constructor = PostReadModel.class.getDeclaredConstructor();
			constructor.setAccessible(true);
			return constructor.newInstance();
		} catch (ReflectiveOperationException exception) {
			throw new IllegalStateException("Failed to create PostReadModel for test", exception);
		}
	}

	private static List<String> textValues(JsonNode arrayNode, String fieldName) {
		List<String> values = new ArrayList<>();
		arrayNode.forEach(node -> values.add(node.path(fieldName).asText()));
		return values;
	}

	private static List<String> textValues(JsonNode arrayNode) {
		List<String> values = new ArrayList<>();
		arrayNode.forEach(node -> values.add(node.asText()));
		return values;
	}
}
