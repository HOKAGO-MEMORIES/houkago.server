package com.houkago.server.content.post;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;

class ParsedPostCandidateTest {

	@Test
	void createsValidCandidate() {
		ParsedPostCandidate candidate = new ParsedPostCandidate(
				"blog/my-post/index.md",
				metadataInput(),
				"## Body");

		assertThat(candidate.sourcePath()).isEqualTo("blog/my-post/index.md");
		assertThat(candidate.metadataInput()).isEqualTo(metadataInput());
		assertThat(candidate.rawBody()).isEqualTo("## Body");
	}

	@Test
	void blankSourcePathRejected() {
		assertInvalidSourcePath("   ", "sourcePath must not be null or blank");
	}

	@Test
	void absoluteSourcePathRejected() {
		assertInvalidSourcePath("/blog/my-post/index.md", "sourcePath must be relative");
	}

	@Test
	void windowsDriveAbsolutePathRejected() {
		assertInvalidSourcePath("C:\\posts\\index.md", "sourcePath must not be a Windows drive absolute path");
	}

	@Test
	void pathTraversalRejected() {
		assertInvalidSourcePath("../blog/my-post/index.md", "sourcePath must not contain dot or traversal segments");
		assertInvalidSourcePath("blog/../my-post/index.md", "sourcePath must not contain dot or traversal segments");
	}

	@Test
	void dotSegmentRejected() {
		assertInvalidSourcePath("blog/./my-post/index.md", "sourcePath must not contain dot or traversal segments");
	}

	@Test
	void emptyPathSegmentRejected() {
		assertInvalidSourcePath("blog//my-post/index.md", "sourcePath must not contain empty path segments");
	}

	@Test
	void nonIndexMdPathRejected() {
		assertInvalidSourcePath("blog/my-post.md", "sourcePath must point to an index.md post entry file");
		assertInvalidSourcePath("blog/my-post/README.md", "sourcePath must point to an index.md post entry file");
	}

	@Test
	void windowsSeparatorNormalized() {
		ParsedPostCandidate candidate = new ParsedPostCandidate(
				"algorithm\\boj\\1000\\index.md",
				metadataInput(),
				"body");

		assertThat(candidate.sourcePath()).isEqualTo("algorithm/boj/1000/index.md");
	}

	@Test
	void nullMetadataInputRejected() {
		assertThatThrownBy(() -> new ParsedPostCandidate("blog/my-post/index.md", null, "body"))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("metadataInput must not be null");
	}

	@Test
	void nullRawBodyRejected() {
		assertThatThrownBy(() -> new ParsedPostCandidate("blog/my-post/index.md", metadataInput(), null))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("rawBody must not be null");
	}

	@Test
	void emptyRawBodyAccepted() {
		ParsedPostCandidate candidate = new ParsedPostCandidate("blog/my-post/index.md", metadataInput(), "");

		assertThat(candidate.rawBody()).isEmpty();
	}

	@Test
	void whitespaceOnlyRawBodyAccepted() {
		ParsedPostCandidate candidate = new ParsedPostCandidate("blog/my-post/index.md", metadataInput(), "   ");

		assertThat(candidate.rawBody()).isEqualTo("   ");
	}

	@Test
	void rawBodyIsPreservedWithoutTrim() {
		ParsedPostCandidate candidate = new ParsedPostCandidate(
				"blog/my-post/index.md",
				metadataInput(),
				"  first line\r\nsecond line  ");

		assertThat(candidate.rawBody()).isEqualTo("  first line\r\nsecond line  ");
	}

	@Test
	void normalizedSourcePathIsStored() {
		ParsedPostCandidate candidate = new ParsedPostCandidate("index.md", metadataInput(), "body");

		assertThat(candidate.sourcePath()).isEqualTo("index.md");
	}

	@Test
	void pathSegmentContainingDotsIsAllowed() {
		ParsedPostCandidate candidate = new ParsedPostCandidate("blog/my..post/index.md", metadataInput(), "body");

		assertThat(candidate.sourcePath()).isEqualTo("blog/my..post/index.md");
	}

	private static void assertInvalidSourcePath(String sourcePath, String message) {
		assertThatThrownBy(() -> new ParsedPostCandidate(sourcePath, metadataInput(), "body"))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining(message);
	}

	private static PostMetadataInput metadataInput() {
		return new PostMetadataInput(
				"A post",
				"my-post",
				"2026-07-03",
				"A useful post.",
				"blog",
				"published",
				List.of("java", "spring"),
				null,
				null,
				null,
				false,
				null,
				null);
	}
}
