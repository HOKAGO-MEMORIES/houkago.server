package com.houkago.server.content.post.source;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.houkago.server.content.post.metadata.PostMetadataInput;
import com.houkago.server.content.post.metadata.PostMetadataMapper;
import com.houkago.server.content.post.metadata.PostMetadataMapping;

class PostSourceLayoutValidatorTest {

	private final PostSourceLayoutValidator validator = new PostSourceLayoutValidator();
	private final PostMetadataMapper metadataMapper = new PostMetadataMapper();

	@Test
	void acceptsCanonicalFlatCategoryLayouts() {
		assertThatCode(() -> validator.validate(
				"project/002-read-model/index.md",
				metadata("project", "002-read-model", null, null))).doesNotThrowAnyException();
		assertThatCode(() -> validator.validate(
				"cs/001-osiv/index.md",
				metadata("cs", "001-osiv", null, null))).doesNotThrowAnyException();
		assertThatCode(() -> validator.validate(
				"blog/hello-world/index.md",
				metadata("blog", "hello-world", null, null))).doesNotThrowAnyException();
	}

	@Test
	void acceptsCanonicalAlgorithmLayoutAndOptionalMetadata() {
		assertThatCode(() -> validator.validate(
				"algorithm/boj/2461/index.md",
				metadata("algorithm", "boj-2461", "boj", "2461"))).doesNotThrowAnyException();
		assertThatCode(() -> validator.validate(
				"algorithm/atcoder/abc350-a/index.md",
				metadata("algorithm", "atcoder-abc350-a", null, null))).doesNotThrowAnyException();
	}

	@Test
	void rejectsCategoryAndTopLevelDirectoryMismatch() {
		assertThatThrownBy(() -> validator.validate(
				"algorithm/boj/2461/index.md",
				metadata("project", "boj-2461", null, null)))
				.isInstanceOf(InvalidPostSourceLayoutException.class)
				.hasMessageContaining("sourcePath=algorithm/boj/2461/index.md")
				.hasMessageContaining("invariant=category_path_match")
				.hasMessageContaining("actual=algorithm")
				.hasMessageContaining("category 'project'");
	}

	@Test
	void rejectsFlatSlugAndDirectoryMismatch() {
		assertThatThrownBy(() -> validator.validate(
				"project/folder-name/index.md",
				metadata("project", "different-slug", null, null)))
				.isInstanceOf(InvalidPostSourceLayoutException.class)
				.hasMessageContaining("invariant=flat_slug_directory_match")
				.hasMessageContaining("actual=folder-name")
				.hasMessageContaining("different-slug");
	}

	@Test
	void rejectsUnexpectedFlatAndAlgorithmNesting() {
		assertThatThrownBy(() -> validator.validate(
				"blog/extra/post/index.md",
				metadata("blog", "post", null, null)))
				.isInstanceOf(InvalidPostSourceLayoutException.class)
				.hasMessageContaining("invariant=source_path_layout")
				.hasMessageContaining("{category}/{slug}/index.md");

		assertThatThrownBy(() -> validator.validate(
				"algorithm/boj-2461/index.md",
				metadata("algorithm", "boj-2461", "boj", "2461")))
				.isInstanceOf(InvalidPostSourceLayoutException.class)
				.hasMessageContaining("invariant=source_path_layout")
				.hasMessageContaining("algorithm/{platform}/{problemId}/index.md");
	}

	@Test
	void rejectsAlgorithmSlugMismatch() {
		assertThatThrownBy(() -> validator.validate(
				"algorithm/boj/2461/index.md",
				metadata("algorithm", "boj-2462", "boj", "2461")))
				.isInstanceOf(InvalidPostSourceLayoutException.class)
				.hasMessageContaining("invariant=algorithm_slug_path_match")
				.hasMessageContaining("actual=boj-2462")
				.hasMessageContaining("slug 'boj-2461'");
	}

	@Test
	void rejectsAlgorithmPlatformAndProblemIdMismatchWhenMetadataExists() {
		assertThatThrownBy(() -> validator.validate(
				"algorithm/boj/2461/index.md",
				metadata("algorithm", "boj-2461", "leetcode", "2461")))
				.isInstanceOf(InvalidPostSourceLayoutException.class)
				.hasMessageContaining("invariant=algorithm_platform_path_match")
				.hasMessageContaining("actual=leetcode")
				.hasMessageContaining("path segment 'boj'");

		assertThatThrownBy(() -> validator.validate(
				"algorithm/boj/2461/index.md",
				metadata("algorithm", "boj-2461", "boj", "9999")))
				.isInstanceOf(InvalidPostSourceLayoutException.class)
				.hasMessageContaining("invariant=algorithm_problem_id_path_match")
				.hasMessageContaining("actual=9999")
				.hasMessageContaining("path segment '2461'");
	}

	private PostMetadataMapping metadata(String category, String slug, String platform, String problemId) {
		return metadataMapper.map(new PostMetadataInput(
				"Post " + slug,
				slug,
				"2026-08-18",
				"Description",
				category,
				"published",
				List.of("java"),
				null,
				null,
				null,
				false,
				platform,
				problemId));
	}
}
