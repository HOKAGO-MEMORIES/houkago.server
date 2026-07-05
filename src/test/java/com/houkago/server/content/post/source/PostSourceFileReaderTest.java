package com.houkago.server.content.post.source;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.houkago.server.content.post.metadata.PostMetadataInput;

class PostSourceFileReaderTest {

	@TempDir
	private Path tempDir;

	@Test
	void validPostSourceFileLoadsAndParsesCandidate() throws IOException {
		Path file = writePost("blog/my-post/index.md", sampleMarkdown("## Body"));
		PostSourceFileReader reader = new PostSourceFileReader(new PostMarkdownParser());

		ParsedPostCandidate candidate = reader.read(sourceFile(file, "blog/my-post/index.md"));

		assertThat(candidate.sourcePath()).isEqualTo("blog/my-post/index.md");
		assertThat(candidate.metadataInput().slug()).isEqualTo("my-post");
		assertThat(candidate.rawBody()).isEqualTo("## Body");
	}

	@Test
	void utf8ContentIsPreserved() throws IOException {
		String rawBody = "## 안녕\n카페와 수학 $N$";
		Path file = writePost("blog/korean-post/index.md", sampleMarkdown(rawBody));
		PostSourceFileReader reader = new PostSourceFileReader(new PostMarkdownParser());

		ParsedPostCandidate candidate = reader.read(sourceFile(file, "blog/korean-post/index.md"));

		assertThat(candidate.rawBody()).isEqualTo(rawBody);
	}

	@Test
	void rawBodyIsNotTrimmed() throws IOException {
		String rawBody = "  first line\r\nsecond line  ";
		Path file = writePost("blog/my-post/index.md", sampleMarkdown(rawBody));
		PostSourceFileReader reader = new PostSourceFileReader(new PostMarkdownParser());

		ParsedPostCandidate candidate = reader.read(sourceFile(file, "blog/my-post/index.md"));

		assertThat(candidate.rawBody()).isEqualTo(rawBody);
	}

	@Test
	void fileReadFailureThrowsReadException() {
		Path missingFile = tempDir.resolve("blog/missing/index.md").toAbsolutePath();
		PostSourceFile sourceFile = new PostSourceFile(missingFile, "blog/missing/index.md");
		PostSourceFileReader reader = new PostSourceFileReader(new PostMarkdownParser());

		assertThatThrownBy(() -> reader.read(sourceFile))
				.isInstanceOf(PostSourceReadException.class);
	}

	@Test
	void readExceptionContainsAbsolutePathAndSourcePath() {
		Path missingFile = tempDir.resolve("blog/missing/index.md").toAbsolutePath();
		PostSourceFile sourceFile = new PostSourceFile(missingFile, "blog/missing/index.md");
		PostSourceFileReader reader = new PostSourceFileReader(new PostMarkdownParser());

		assertThatThrownBy(() -> reader.read(sourceFile))
				.isInstanceOf(PostSourceReadException.class)
				.hasMessageContaining(missingFile.toString())
				.hasMessageContaining("blog/missing/index.md");
	}

	@Test
	void readExceptionPreservesCause() {
		Path missingFile = tempDir.resolve("blog/missing/index.md").toAbsolutePath();
		PostSourceFile sourceFile = new PostSourceFile(missingFile, "blog/missing/index.md");
		PostSourceFileReader reader = new PostSourceFileReader(new PostMarkdownParser());

		assertThatThrownBy(() -> reader.read(sourceFile))
				.isInstanceOf(PostSourceReadException.class)
				.hasCauseInstanceOf(IOException.class);
	}

	@Test
	void parserFailurePropagates() throws IOException {
		Path file = writePost("blog/broken/index.md", "body without frontmatter");
		PostSourceFileReader reader = new PostSourceFileReader(new PostMarkdownParser());

		assertThatThrownBy(() -> reader.read(sourceFile(file, "blog/broken/index.md")))
				.isInstanceOf(PostParseException.class)
				.hasMessageContaining("blog/broken/index.md");
	}

	@Test
	void sourcePathIsPassedToParser() throws IOException {
		Path file = writePost("blog/my-post/index.md", "file text");
		RecordingPostMarkdownParser parser = new RecordingPostMarkdownParser();
		PostSourceFileReader reader = new PostSourceFileReader(parser);

		reader.read(sourceFile(file, "blog/my-post/index.md"));

		assertThat(parser.sourcePath()).isEqualTo("blog/my-post/index.md");
		assertThat(parser.markdownSource()).isEqualTo("file text");
	}

	@Test
	void readerDoesNotCallMapperChecksumAssemblerOrRepository() throws IOException {
		Path file = writePost("blog/my-post/index.md", sampleMarkdown("body"));
		RecordingPostMarkdownParser parser = new RecordingPostMarkdownParser();
		PostSourceFileReader reader = new PostSourceFileReader(parser);

		ParsedPostCandidate candidate = reader.read(sourceFile(file, "blog/my-post/index.md"));

		assertThat(candidate).isNotNull();
		assertThat(parser.callCount()).isOne();
	}

	private Path writePost(String relativePath, String content) throws IOException {
		Path file = tempDir.resolve(relativePath).toAbsolutePath();
		Files.createDirectories(file.getParent());
		Files.writeString(file, content, StandardCharsets.UTF_8);
		return file;
	}

	private static PostSourceFile sourceFile(Path file, String sourcePath) {
		return new PostSourceFile(file.toAbsolutePath(), sourcePath);
	}

	private static String sampleMarkdown(String rawBody) {
		return "---\n"
				+ """
						title: "A post"
						slug: "my-post"
						date: "2026-07-03"
						description: "A useful post."
						category: "blog"
						status: "published"
						""".stripIndent()
				+ "---\n"
				+ rawBody;
	}

	private static class RecordingPostMarkdownParser extends PostMarkdownParser {

		private String sourcePath;
		private String markdownSource;
		private int callCount;

		@Override
		public ParsedPostCandidate parse(String sourcePath, String markdownSource) {
			this.sourcePath = sourcePath;
			this.markdownSource = markdownSource;
			this.callCount++;
			return new ParsedPostCandidate(sourcePath, metadataInput(), markdownSource);
		}

		String sourcePath() {
			return sourcePath;
		}

		String markdownSource() {
			return markdownSource;
		}

		int callCount() {
			return callCount;
		}

		private static PostMetadataInput metadataInput() {
			return new PostMetadataInput(
					"A post",
					"my-post",
					"2026-07-03",
					"A useful post.",
					"blog",
					"published",
					null,
					null,
					null,
					null,
					null,
					null,
					null);
		}
	}
}
