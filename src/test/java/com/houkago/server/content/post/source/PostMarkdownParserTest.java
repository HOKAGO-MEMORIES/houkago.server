package com.houkago.server.content.post.source;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import com.houkago.server.content.post.metadata.InvalidPostMetadataException;
import com.houkago.server.content.post.metadata.PostMetadataMapper;

class PostMarkdownParserTest {

	private final PostMarkdownParser parser = new PostMarkdownParser();

	@Test
	void validFrontmatterAndBodyParsesToParsedPostCandidate() {
		ParsedPostCandidate candidate = parser.parse("blog/my-post/index.md", sampleMarkdown("## Hello\nBody"));

		assertThat(candidate.sourcePath()).isEqualTo("blog/my-post/index.md");
		assertThat(candidate.rawBody()).isEqualTo("## Hello\nBody");
		assertThat(candidate.metadataInput().title()).isEqualTo("A post");
		assertThat(candidate.metadataInput().slug()).isEqualTo("my-post");
		assertThat(candidate.metadataInput().category()).isEqualTo("blog");
		assertThat(candidate.metadataInput().status()).isEqualTo("published");
	}

	@Test
	void rawBodyIsPreservedWithoutTrim() {
		String rawBody = "  first line\r\nsecond line  ";

		ParsedPostCandidate candidate = parser.parse("blog/my-post/index.md", sampleMarkdown(rawBody));

		assertThat(candidate.rawBody()).isEqualTo(rawBody);
	}

	@Test
	void missingOpeningFrontmatterDelimiterRejected() {
		assertThatThrownBy(() -> parser.parse("blog/my-post/index.md", "title: A post\n---\nbody"))
				.isInstanceOf(PostParseException.class)
				.hasMessageContaining("blog/my-post/index.md")
				.hasMessageContaining("opening --- delimiter is required");
	}

	@Test
	void missingClosingFrontmatterDelimiterRejected() {
		assertThatThrownBy(() -> parser.parse("blog/my-post/index.md", "---\ntitle: A post\nbody"))
				.isInstanceOf(PostParseException.class)
				.hasMessageContaining("blog/my-post/index.md")
				.hasMessageContaining("closing --- delimiter is required");
	}

	@Test
	void invalidYamlRejected() {
		String markdown = "---\ntitle: [unclosed\n---\nbody";

		assertThatThrownBy(() -> parser.parse("blog/my-post/index.md", markdown))
				.isInstanceOf(PostParseException.class)
				.hasMessageContaining("blog/my-post/index.md")
				.hasMessageContaining("invalid YAML frontmatter");
	}

	@Test
	void parseExceptionIncludesSourcePath() {
		assertThatThrownBy(() -> parser.parse("blog/special-post/index.md", "body only"))
				.isInstanceOf(PostParseException.class)
				.hasMessageContaining("blog/special-post/index.md");
	}

	@Test
	void requiredMetadataMissingIsParsedButLaterMapperWouldRejectIt() {
		String markdown = frontmatter("""
				slug: "my-post"
				date: "2026-07-03"
				description: "A useful post."
				category: "blog"
				status: "published"
				""", "body");

		ParsedPostCandidate candidate = parser.parse("blog/my-post/index.md", markdown);

		assertThat(candidate.metadataInput().title()).isNull();
		assertThatThrownBy(() -> new PostMetadataMapper().map(candidate.metadataInput()))
				.isInstanceOf(InvalidPostMetadataException.class)
				.hasMessageContaining("title is required");
	}

	@Test
	void tagsBlockListParsed() {
		String markdown = frontmatter("""
				title: "A post"
				slug: "my-post"
				date: "2026-07-03"
				description: "A useful post."
				category: "blog"
				status: "published"
				tags:
				  - java
				  - spring
				""", "body");

		ParsedPostCandidate candidate = parser.parse("blog/my-post/index.md", markdown);

		assertThat(candidate.metadataInput().tags()).containsExactly("java", "spring");
	}

	@Test
	void tagsInlineListParsed() {
		String markdown = frontmatter("""
				title: "A post"
				slug: "my-post"
				date: "2026-07-03"
				description: "A useful post."
				category: "blog"
				status: "published"
				tags: [java, spring]
				""", "body");

		ParsedPostCandidate candidate = parser.parse("blog/my-post/index.md", markdown);

		assertThat(candidate.metadataInput().tags()).containsExactly("java", "spring");
	}

	@Test
	void optionalFieldsAbsentBecomeNull() {
		String markdown = frontmatter("""
				title: "A post"
				slug: "my-post"
				date: "2026-07-03"
				description: "A useful post."
				category: "blog"
				status: "published"
				""", "body");

		ParsedPostCandidate candidate = parser.parse("blog/my-post/index.md", markdown);

		assertThat(candidate.metadataInput().tags()).isNull();
		assertThat(candidate.metadataInput().updated()).isNull();
		assertThat(candidate.metadataInput().thumbnail()).isNull();
		assertThat(candidate.metadataInput().series()).isNull();
		assertThat(candidate.metadataInput().featured()).isNull();
		assertThat(candidate.metadataInput().platform()).isNull();
		assertThat(candidate.metadataInput().problemId()).isNull();
	}

	@Test
	void featuredBooleanParsed() {
		ParsedPostCandidate candidate = parser.parse("blog/my-post/index.md", sampleMarkdown("body"));

		assertThat(candidate.metadataInput().featured()).isTrue();
	}

	@Test
	void dateAndUpdatedRemainString() {
		ParsedPostCandidate candidate = parser.parse("blog/my-post/index.md", sampleMarkdown("body"));

		assertThat(candidate.metadataInput().date()).isEqualTo("2026-07-03");
		assertThat(candidate.metadataInput().updated()).isEqualTo("2026-07-04");
	}

	@Test
	void sourcePathValidationIsDelegatedToParsedPostCandidate() {
		assertThatThrownBy(() -> parser.parse("/blog/my-post/index.md", sampleMarkdown("body")))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("sourcePath must be relative");
	}

	@Test
	void unknownMetadataFieldsAreIgnored() {
		String markdown = frontmatter("""
				title: "A post"
				slug: "my-post"
				date: "2026-07-03"
				description: "A useful post."
				category: "blog"
				status: "draft"
				draftNote: "keep editing"
				sourceRepository: "external"
				sourcePath: "notes/a.md"
				sourceUrl: "https://example.invalid/a"
				""", "body");

		ParsedPostCandidate candidate = parser.parse("blog/my-post/index.md", markdown);

		assertThat(candidate.metadataInput().status()).isEqualTo("draft");
		assertThat(candidate.rawBody()).isEqualTo("body");
	}

	private static String sampleMarkdown(String rawBody) {
		return frontmatter("""
				title: "A post"
				slug: "my-post"
				date: "2026-07-03"
				description: "A useful post."
				category: "blog"
				status: "published"
				tags:
				  - java
				  - spring
				updated: "2026-07-04"
				thumbnail: "./assets/thumbnail.png"
				series: "Houkago"
				featured: true
				platform: "boj"
				problemId: "1000"
				""", rawBody);
	}

	private static String frontmatter(String yaml, String rawBody) {
		return "---\n" + yaml.stripIndent() + "---\n" + rawBody;
	}
}
