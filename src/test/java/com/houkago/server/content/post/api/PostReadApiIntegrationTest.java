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
		"houkago.resync.enabled=false",
		"houkago.assets.public-origin=https://assets.example.test"
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
				PostSyncStatus.DELETED, PostVisibility.PRIVATE));

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
				Boolean.class,
				String.class,
				String.class,
				String.class,
				org.springframework.data.domain.Pageable.class);
		Query query = method.getAnnotation(Query.class);
		String selectClause = query.value().substring(0, query.value().indexOf("from PostReadModel"));

		assertThat(query).isNotNull();
		assertThat(query.value()).contains("PostReadSummaryProjection");
		assertThat(selectClause).doesNotContain("rawBody")
				.doesNotContain("raw_body");
	}

	@Test
	void listWithoutFeaturedParameterIncludesFeaturedAndNonFeaturedPosts() throws Exception {
		repository.save(featured(publicPost("featured-post", LocalDate.of(2026, 7, 5), "featured body")));
		repository.save(publicPost("regular-post", LocalDate.of(2026, 7, 4), "regular body"));

		ResponseEntity<String> response = restTemplate.getForEntity("/api/posts?size=10", String.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		JsonNode content = objectMapper.readTree(response.getBody()).path("content");
		assertThat(textValues(content, "slug")).containsExactly("featured-post", "regular-post");
	}

	@Test
	void featuredTrueReturnsOnlyPublicPublishedActiveFeaturedPosts() throws Exception {
		repository.save(featured(publicPost("public-featured", LocalDate.of(2026, 7, 4), "public body")));
		repository.save(publicPost("public-regular", LocalDate.of(2026, 7, 5), "regular body"));
		repository.save(featured(post("private-featured", LocalDate.of(2026, 7, 5),
				PostSourceStatus.PUBLISHED, PostSyncStatus.ACTIVE, PostVisibility.PRIVATE)));
		repository.save(featured(post("draft-featured", LocalDate.of(2026, 7, 5),
				PostSourceStatus.DRAFT, PostSyncStatus.ACTIVE, PostVisibility.PRIVATE)));
		repository.save(featured(post("deleted-featured", LocalDate.of(2026, 7, 5),
				PostSourceStatus.PUBLISHED, PostSyncStatus.DELETED, PostVisibility.PRIVATE)));

		ResponseEntity<String> response = restTemplate.getForEntity(
				"/api/posts?featured=true&page=0&size=10", String.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		JsonNode root = objectMapper.readTree(response.getBody());
		assertThat(textValues(root.path("content"), "slug")).containsExactly("public-featured");
		assertThat(root.path("content").get(0).path("featured").asBoolean()).isTrue();
		assertThat(root.path("totalElements").asInt()).isEqualTo(1);
	}

	@Test
	void featuredFalseReturnsOnlyPublicNonFeaturedPosts() throws Exception {
		repository.save(featured(publicPost("public-featured", LocalDate.of(2026, 7, 5), "featured body")));
		repository.save(publicPost("public-regular", LocalDate.of(2026, 7, 4), "regular body"));

		ResponseEntity<String> response = restTemplate.getForEntity(
				"/api/posts?featured=false&page=0&size=10", String.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		JsonNode root = objectMapper.readTree(response.getBody());
		assertThat(textValues(root.path("content"), "slug")).containsExactly("public-regular");
		assertThat(root.path("content").get(0).path("featured").asBoolean()).isFalse();
	}

	@Test
	void featuredListSortsByPostDateDescThenIdDesc() throws Exception {
		PostReadModel firstSameDate = repository.save(featured(publicPost("first-featured",
				LocalDate.of(2026, 7, 4), "first body")));
		PostReadModel secondSameDate = repository.save(featured(publicPost("second-featured",
				LocalDate.of(2026, 7, 4), "second body")));
		repository.save(featured(publicPost("older-featured", LocalDate.of(2026, 7, 3), "older body")));
		assertThat(firstSameDate.getId()).isLessThan(secondSameDate.getId());

		ResponseEntity<String> response = restTemplate.getForEntity(
				"/api/posts?featured=true&page=0&size=10", String.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		JsonNode content = objectMapper.readTree(response.getBody()).path("content");
		assertThat(textValues(content, "slug"))
				.containsExactly("second-featured", "first-featured", "older-featured");
	}

	@Test
	void featuredListReturnsFilteredPaginationMetadata() throws Exception {
		for (int index = 1; index <= 4; index++) {
			repository.save(featured(publicPost("featured-page-" + index,
					LocalDate.of(2026, 7, 6 - index), "body " + index)));
		}
		repository.save(publicPost("regular-post", LocalDate.of(2026, 7, 6), "regular body"));

		ResponseEntity<String> response = restTemplate.getForEntity(
				"/api/posts?featured=true&page=0&size=3", String.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		JsonNode root = objectMapper.readTree(response.getBody());
		assertThat(root.path("content")).hasSize(3);
		assertThat(root.path("totalElements").asInt()).isEqualTo(4);
		assertThat(root.path("totalPages").asInt()).isEqualTo(2);
		assertThat(root.path("number").asInt()).isZero();
		assertThat(root.path("size").asInt()).isEqualTo(3);
	}

	@Test
	void invalidFeaturedParameterReturnsBadRequest() {
		ResponseEntity<String> response = restTemplate.getForEntity(
				"/api/posts?featured=invalid&page=0&size=3", String.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
	}

	@Test
	void categoryReturnsOnlyPublicPublishedActivePostsWithExactCategory() throws Exception {
		repository.save(category(publicPost("algorithm-public", LocalDate.of(2026, 7, 5), "public body"),
				"algorithm"));
		repository.save(category(publicPost("blog-public", LocalDate.of(2026, 7, 6), "blog body"), "blog"));
		repository.save(category(post("algorithm-draft", LocalDate.of(2026, 7, 6), PostSourceStatus.DRAFT,
				PostSyncStatus.ACTIVE, PostVisibility.PRIVATE), "algorithm"));
		repository.save(category(post("algorithm-private", LocalDate.of(2026, 7, 6),
				PostSourceStatus.PUBLISHED, PostSyncStatus.ACTIVE, PostVisibility.PRIVATE), "algorithm"));
		repository.save(category(post("algorithm-deleted", LocalDate.of(2026, 7, 6),
				PostSourceStatus.PUBLISHED, PostSyncStatus.DELETED, PostVisibility.PRIVATE), "algorithm"));

		ResponseEntity<String> response = restTemplate.getForEntity(
				"/api/posts?category=algorithm&page=0&size=10", String.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		JsonNode root = objectMapper.readTree(response.getBody());
		assertThat(textValues(root.path("content"), "slug")).containsExactly("algorithm-public");
		assertThat(root.path("content").get(0).path("category").asText()).isEqualTo("algorithm");
		assertThat(root.path("totalElements").asInt()).isEqualTo(1);
	}

	@Test
	void categoryFilterPreservesOrderingAndFilteredPaginationMetadata() throws Exception {
		PostReadModel firstSameDate = repository.save(category(publicPost("algorithm-first",
				LocalDate.of(2026, 7, 5), "first body"), "algorithm"));
		PostReadModel secondSameDate = repository.save(category(publicPost("algorithm-second",
				LocalDate.of(2026, 7, 5), "second body"), "algorithm"));
		repository.save(category(publicPost("algorithm-older", LocalDate.of(2026, 7, 4), "older body"),
				"algorithm"));
		repository.save(category(publicPost("blog-newer", LocalDate.of(2026, 7, 6), "blog body"), "blog"));
		assertThat(firstSameDate.getId()).isLessThan(secondSameDate.getId());

		ResponseEntity<String> response = restTemplate.getForEntity(
				"/api/posts?category=algorithm&page=0&size=2", String.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		JsonNode root = objectMapper.readTree(response.getBody());
		assertThat(textValues(root.path("content"), "slug"))
				.containsExactly("algorithm-second", "algorithm-first");
		assertThat(root.path("totalElements").asInt()).isEqualTo(3);
		assertThat(root.path("totalPages").asInt()).isEqualTo(2);
		assertThat(root.path("number").asInt()).isZero();
		assertThat(root.path("size").asInt()).isEqualTo(2);
	}

	@Test
	void categoryAndFeaturedFiltersUseAndSemantics() throws Exception {
		repository.save(featured(category(publicPost("algorithm-featured", LocalDate.of(2026, 7, 5),
				"featured body"), "algorithm")));
		repository.save(category(publicPost("algorithm-regular", LocalDate.of(2026, 7, 6), "regular body"),
				"algorithm"));
		repository.save(featured(category(publicPost("blog-featured", LocalDate.of(2026, 7, 6), "blog body"),
				"blog")));

		ResponseEntity<String> response = restTemplate.getForEntity(
				"/api/posts?category=algorithm&featured=true&page=0&size=3", String.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		JsonNode root = objectMapper.readTree(response.getBody());
		assertThat(textValues(root.path("content"), "slug")).containsExactly("algorithm-featured");
		assertThat(root.path("totalElements").asInt()).isEqualTo(1);
	}

	@Test
	void unknownCategoryReturnsAnEmptyPage() throws Exception {
		repository.save(category(publicPost("algorithm-public", LocalDate.of(2026, 7, 5), "public body"),
				"algorithm"));

		ResponseEntity<String> response = restTemplate.getForEntity(
				"/api/posts?category=unknown&page=0&size=3", String.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		JsonNode root = objectMapper.readTree(response.getBody());
		assertThat(root.path("content")).isEmpty();
		assertThat(root.path("totalElements").asInt()).isZero();
		assertThat(root.path("totalPages").asInt()).isZero();
	}

	@Test
	void tagReturnsOnlyExactPublicArrayMembersWithoutPrefixFalsePositives() throws Exception {
		repository.save(tags(publicPost("graph-post", LocalDate.of(2026, 7, 5), "graph body"), "graph"));
		repository.save(tags(publicPost("graph-theory-post", LocalDate.of(2026, 7, 6), "theory body"),
				"graph-theory"));
		repository.save(tags(publicPost("multi-tag-post", LocalDate.of(2026, 7, 4), "multi body"),
				"algorithm", "graph"));
		repository.save(tags(post("private-graph-post", LocalDate.of(2026, 7, 7), PostSourceStatus.PUBLISHED,
				PostSyncStatus.ACTIVE, PostVisibility.PRIVATE), "graph"));

		ResponseEntity<String> response = restTemplate.getForEntity(
				"/api/posts?tag=graph&page=0&size=10", String.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		JsonNode root = objectMapper.readTree(response.getBody());
		assertThat(textValues(root.path("content"), "slug"))
				.containsExactly("graph-post", "multi-tag-post");
		assertThat(root.path("totalElements").asInt()).isEqualTo(2);
		assertThat(response.getBody()).doesNotContain("graph-theory-post")
				.doesNotContain("private-graph-post")
				.doesNotContain("rawBody");
	}

	@Test
	void tagFilterPreservesOrderingAndFilteredPaginationMetadata() throws Exception {
		PostReadModel firstSameDate = repository.save(tags(publicPost("tag-first",
				LocalDate.of(2026, 7, 5), "first body"), "spring"));
		PostReadModel secondSameDate = repository.save(tags(publicPost("tag-second",
				LocalDate.of(2026, 7, 5), "second body"), "spring"));
		repository.save(tags(publicPost("tag-older", LocalDate.of(2026, 7, 4), "older body"), "spring"));
		repository.save(tags(publicPost("other-newer", LocalDate.of(2026, 7, 6), "other body"), "java"));
		assertThat(firstSameDate.getId()).isLessThan(secondSameDate.getId());

		ResponseEntity<String> response = restTemplate.getForEntity(
				"/api/posts?tag=spring&page=0&size=2", String.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		JsonNode root = objectMapper.readTree(response.getBody());
		assertThat(textValues(root.path("content"), "slug"))
				.containsExactly("tag-second", "tag-first");
		assertThat(root.path("totalElements").asInt()).isEqualTo(3);
		assertThat(root.path("totalPages").asInt()).isEqualTo(2);
		assertThat(root.path("number").asInt()).isZero();
		assertThat(root.path("size").asInt()).isEqualTo(2);
	}

	@Test
	void tagCombinesWithCategoryAndFeaturedUsingAndSemantics() throws Exception {
		repository.save(featured(category(tags(publicPost("matching-post", LocalDate.of(2026, 7, 5),
				"matching body"), "spring"), "project")));
		repository.save(category(tags(publicPost("not-featured", LocalDate.of(2026, 7, 6),
				"regular body"), "spring"), "project"));
		repository.save(featured(category(tags(publicPost("wrong-category", LocalDate.of(2026, 7, 6),
				"category body"), "spring"), "blog")));
		repository.save(featured(category(tags(publicPost("wrong-tag", LocalDate.of(2026, 7, 6),
				"tag body"), "java"), "project")));

		ResponseEntity<String> categoryResponse = restTemplate.getForEntity(
				"/api/posts?tag=spring&category=project&page=0&size=10", String.class);
		ResponseEntity<String> featuredResponse = restTemplate.getForEntity(
				"/api/posts?tag=spring&featured=true&page=0&size=10", String.class);
		ResponseEntity<String> combinedResponse = restTemplate.getForEntity(
				"/api/posts?tag=spring&category=project&featured=true&page=0&size=10", String.class);

		assertThat(textValues(objectMapper.readTree(categoryResponse.getBody()).path("content"), "slug"))
				.containsExactly("not-featured", "matching-post");
		assertThat(textValues(objectMapper.readTree(featuredResponse.getBody()).path("content"), "slug"))
				.containsExactly("wrong-category", "matching-post");
		JsonNode combinedRoot = objectMapper.readTree(combinedResponse.getBody());
		assertThat(textValues(combinedRoot.path("content"), "slug")).containsExactly("matching-post");
		assertThat(combinedRoot.path("totalElements").asInt()).isEqualTo(1);
	}

	@Test
	void unknownTagReturnsAnEmptyPage() throws Exception {
		repository.save(tags(publicPost("known-tag-post", LocalDate.of(2026, 7, 5), "body"), "known"));

		ResponseEntity<String> response = restTemplate.getForEntity(
				"/api/posts?tag=this-tag-does-not-exist&page=0&size=3", String.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		JsonNode root = objectMapper.readTree(response.getBody());
		assertThat(root.path("content")).isEmpty();
		assertThat(root.path("totalElements").asInt()).isZero();
		assertThat(root.path("totalPages").asInt()).isZero();
	}

	@Test
	void tagMatchingIsCaseSensitiveAndSupportsUnicode() throws Exception {
		repository.save(tags(publicPost("lowercase-tag", LocalDate.of(2026, 7, 5), "lower body"), "spring"));
		repository.save(tags(publicPost("uppercase-tag", LocalDate.of(2026, 7, 6), "upper body"), "Spring"));
		repository.save(tags(publicPost("unicode-tag", LocalDate.of(2026, 7, 4), "unicode body"), "그래프"));

		ResponseEntity<String> lowercase = restTemplate.getForEntity(
				"/api/posts?tag=spring&page=0&size=10", String.class);
		ResponseEntity<String> uppercase = restTemplate.getForEntity(
				"/api/posts?tag=Spring&page=0&size=10", String.class);
		ResponseEntity<String> unicode = restTemplate.getForEntity(
				"/api/posts?tag={tag}&page=0&size=10", String.class, "그래프");

		assertThat(textValues(objectMapper.readTree(lowercase.getBody()).path("content"), "slug"))
				.containsExactly("lowercase-tag");
		assertThat(textValues(objectMapper.readTree(uppercase.getBody()).path("content"), "slug"))
				.containsExactly("uppercase-tag");
		assertThat(textValues(objectMapper.readTree(unicode.getBody()).path("content"), "slug"))
				.containsExactly("unicode-tag");
	}

	@Test
	void blankTagReturnsBadRequest() {
		assertThat(restTemplate.getForEntity("/api/posts?tag=", String.class).getStatusCode())
				.isEqualTo(HttpStatus.BAD_REQUEST);
		assertThat(restTemplate.getForEntity("/api/posts?tag={tag}", String.class, "   ").getStatusCode())
					.isEqualTo(HttpStatus.BAD_REQUEST);
	}

	@Test
	void searchMatchesTitleDescriptionAndRawBodyWithoutReturningRawBody() throws Exception {
		repository.save(title(publicPost("title-match", LocalDate.of(2026, 7, 6), "ordinary body"),
				"Unique Telescope Guide"));
		repository.save(description(publicPost("description-match", LocalDate.of(2026, 7, 5), "ordinary body"),
				"Unique telescope description"));
		repository.save(publicPost("body-match", LocalDate.of(2026, 7, 4), "Markdown with unique telescope notes"));
		repository.save(publicPost("no-match", LocalDate.of(2026, 7, 7), "unrelated body"));

		ResponseEntity<String> response = restTemplate.getForEntity(
				"/api/posts?q=TELESCOPE&page=0&size=10", String.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		JsonNode root = objectMapper.readTree(response.getBody());
		assertThat(textValues(root.path("content"), "slug"))
				.containsExactly("title-match", "description-match", "body-match");
		assertThat(root.path("totalElements").asInt()).isEqualTo(3);
		assertThat(response.getBody()).doesNotContain("rawBody")
				.doesNotContain("unique telescope notes");
	}

	@Test
	void searchTreatsPercentUnderscoreAndBackslashAsLiteralCharacters() throws Exception {
		repository.save(publicPost("percent-match", LocalDate.of(2026, 7, 6), "literal % marker"));
		repository.save(publicPost("underscore-match", LocalDate.of(2026, 7, 5), "literal _ marker"));
		repository.save(publicPost("backslash-match", LocalDate.of(2026, 7, 4), "literal \\ marker"));
		repository.save(publicPost("ordinary", LocalDate.of(2026, 7, 7), "ordinary marker"));

		ResponseEntity<String> percent = restTemplate.getForEntity(
				"/api/posts?q={q}&page=0&size=10", String.class, "%");
		ResponseEntity<String> underscore = restTemplate.getForEntity(
				"/api/posts?q={q}&page=0&size=10", String.class, "_");
		ResponseEntity<String> backslash = restTemplate.getForEntity(
				"/api/posts?q={q}&page=0&size=10", String.class, "\\");

		assertThat(textValues(objectMapper.readTree(percent.getBody()).path("content"), "slug"))
				.containsExactly("percent-match");
		assertThat(textValues(objectMapper.readTree(underscore.getBody()).path("content"), "slug"))
				.containsExactly("underscore-match");
		assertThat(textValues(objectMapper.readTree(backslash.getBody()).path("content"), "slug"))
				.containsExactly("backslash-match");
	}

	@Test
	void searchAppliesPublicVisibilityBeforeTextMatching() throws Exception {
		repository.save(publicPost("public-search", LocalDate.of(2026, 7, 4), "visibility needle"));
		repository.save(rawBody(post("draft-search", LocalDate.of(2026, 7, 7), PostSourceStatus.DRAFT,
				PostSyncStatus.ACTIVE, PostVisibility.PRIVATE), "visibility needle"));
		repository.save(rawBody(post("private-search", LocalDate.of(2026, 7, 6), PostSourceStatus.PUBLISHED,
				PostSyncStatus.ACTIVE, PostVisibility.PRIVATE), "visibility needle"));
		repository.save(rawBody(post("deleted-search", LocalDate.of(2026, 7, 5), PostSourceStatus.PUBLISHED,
				PostSyncStatus.DELETED, PostVisibility.PRIVATE), "visibility needle"));

		ResponseEntity<String> response = restTemplate.getForEntity(
				"/api/posts?q={q}&page=0&size=10", String.class, "visibility needle");

		assertThat(textValues(objectMapper.readTree(response.getBody()).path("content"), "slug"))
				.containsExactly("public-search");
	}

	@Test
	void searchCombinesWithCategoryTagAndFeaturedUsingAndSemantics() throws Exception {
		repository.save(featured(category(tags(publicPost("matching-search", LocalDate.of(2026, 7, 5),
				"search conjunction"), "backend"), "project")));
		repository.save(category(tags(publicPost("not-featured-search", LocalDate.of(2026, 7, 6),
				"search conjunction"), "backend"), "project"));
		repository.save(featured(category(tags(publicPost("wrong-category-search", LocalDate.of(2026, 7, 7),
				"search conjunction"), "backend"), "blog")));
		repository.save(featured(category(tags(publicPost("wrong-tag-search", LocalDate.of(2026, 7, 8),
				"search conjunction"), "java"), "project")));

		ResponseEntity<String> categoryResponse = restTemplate.getForEntity(
				"/api/posts?q=conjunction&category=project&page=0&size=10", String.class);
		ResponseEntity<String> tagResponse = restTemplate.getForEntity(
				"/api/posts?q=conjunction&tag=backend&page=0&size=10", String.class);
		ResponseEntity<String> featuredResponse = restTemplate.getForEntity(
				"/api/posts?q=conjunction&featured=true&page=0&size=10", String.class);
		ResponseEntity<String> combinedResponse = restTemplate.getForEntity(
				"/api/posts?q=conjunction&category=project&tag=backend&featured=true&page=0&size=10",
				String.class);

		assertThat(textValues(objectMapper.readTree(categoryResponse.getBody()).path("content"), "slug"))
				.containsExactly("wrong-tag-search", "not-featured-search", "matching-search");
		assertThat(textValues(objectMapper.readTree(tagResponse.getBody()).path("content"), "slug"))
				.containsExactly("wrong-category-search", "not-featured-search", "matching-search");
		assertThat(textValues(objectMapper.readTree(featuredResponse.getBody()).path("content"), "slug"))
				.containsExactly("wrong-tag-search", "wrong-category-search", "matching-search");
		assertThat(textValues(objectMapper.readTree(combinedResponse.getBody()).path("content"), "slug"))
				.containsExactly("matching-search");
	}

	@Test
	void searchPreservesPaginationCountAndCanonicalOrder() throws Exception {
		PostReadModel firstSameDate = repository.save(publicPost("search-first",
				LocalDate.of(2026, 7, 5), "pagination needle"));
		PostReadModel secondSameDate = repository.save(publicPost("search-second",
				LocalDate.of(2026, 7, 5), "pagination needle"));
		repository.save(publicPost("search-older", LocalDate.of(2026, 7, 4), "pagination needle"));
		assertThat(firstSameDate.getId()).isLessThan(secondSameDate.getId());

		ResponseEntity<String> response = restTemplate.getForEntity(
				"/api/posts?q=pagination&page=0&size=2", String.class);

		JsonNode root = objectMapper.readTree(response.getBody());
		assertThat(textValues(root.path("content"), "slug"))
				.containsExactly("search-second", "search-first");
		assertThat(root.path("totalElements").asInt()).isEqualTo(3);
		assertThat(root.path("totalPages").asInt()).isEqualTo(2);
		assertThat(root.path("number").asInt()).isZero();
		assertThat(root.path("size").asInt()).isEqualTo(2);
	}

	@Test
	void searchSupportsKoreanEnglishCaseAndInternalSpaces() throws Exception {
		repository.save(publicPost("korean-search", LocalDate.of(2026, 7, 5), "다익스트라 최단 경로 정리"));
		repository.save(publicPost("english-search", LocalDate.of(2026, 7, 4), "Spring Boot deployment"));

		ResponseEntity<String> korean = restTemplate.getForEntity(
				"/api/posts?q={q}&page=0&size=10", String.class, "최단 경로");
		ResponseEntity<String> english = restTemplate.getForEntity(
				"/api/posts?q={q}&page=0&size=10", String.class, "  SPRING BOOT  ");

		assertThat(textValues(objectMapper.readTree(korean.getBody()).path("content"), "slug"))
				.containsExactly("korean-search");
		assertThat(textValues(objectMapper.readTree(english.getBody()).path("content"), "slug"))
				.containsExactly("english-search");
	}

	@Test
	void unknownSearchReturnsAnEmptyPage() throws Exception {
		repository.save(publicPost("known-search", LocalDate.of(2026, 7, 5), "known body"));

		ResponseEntity<String> response = restTemplate.getForEntity(
				"/api/posts?q=no-such-search-term&page=0&size=10", String.class);

		JsonNode root = objectMapper.readTree(response.getBody());
		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(root.path("content")).isEmpty();
		assertThat(root.path("totalElements").asInt()).isZero();
	}

	@Test
	void blankAndTooLongSearchQueriesReturnBadRequest() {
		assertThat(restTemplate.getForEntity("/api/posts?q=", String.class).getStatusCode())
				.isEqualTo(HttpStatus.BAD_REQUEST);
		assertThat(restTemplate.getForEntity("/api/posts?q={q}", String.class, "   ").getStatusCode())
				.isEqualTo(HttpStatus.BAD_REQUEST);
		assertThat(restTemplate.getForEntity("/api/posts?q={q}", String.class, "x".repeat(101)).getStatusCode())
				.isEqualTo(HttpStatus.BAD_REQUEST);
	}

	@Test
	void listCapsRequestedPageSizeAtFifty() throws Exception {
		for (int index = 0; index < 51; index++) {
			repository.save(publicPost("max-size-" + index, LocalDate.of(2026, 7, 5), "body " + index));
		}

		ResponseEntity<String> response = restTemplate.getForEntity("/api/posts?page=0&size=51", String.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		JsonNode root = objectMapper.readTree(response.getBody());
		assertThat(root.path("content")).hasSize(50);
		assertThat(root.path("size").asInt()).isEqualTo(50);
		assertThat(root.path("totalElements").asInt()).isEqualTo(51);
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
		assertThat(root.path("assetBaseUrl").asText())
				.isEqualTo("https://assets.example.test/assets/posts/detail-post/");
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
				PostSyncStatus.DELETED, PostVisibility.PRIVATE));

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

	private static PostReadModel featured(PostReadModel post) {
		post.setFeatured(true);
		return post;
	}

	private static PostReadModel category(PostReadModel post, String category) {
		post.setCategory(category);
		return post;
	}

	private static PostReadModel title(PostReadModel post, String title) {
		post.setTitle(title);
		return post;
	}

	private static PostReadModel description(PostReadModel post, String description) {
		post.setDescription(description);
		return post;
	}

	private static PostReadModel rawBody(PostReadModel post, String rawBody) {
		post.setRawBody(rawBody);
		return post;
	}

	private static PostReadModel tags(PostReadModel post, String... tags) throws Exception {
		post.setTagsJson(new ObjectMapper().writeValueAsString(tags));
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
