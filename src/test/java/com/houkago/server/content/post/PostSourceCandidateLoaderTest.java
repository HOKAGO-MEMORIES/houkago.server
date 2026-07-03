package com.houkago.server.content.post;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PostSourceCandidateLoaderTest {

	@TempDir
	private Path tempDir;

	@Test
	void loadsScannerFilesThroughReader() {
		PostSourceFile first = sourceFile("blog/a-post/index.md");
		PostSourceFile second = sourceFile("project/b-post/index.md");
		FakeScanner scanner = new FakeScanner(List.of(first, second));
		FakeReader reader = new FakeReader();
		PostSourceCandidateLoader loader = new PostSourceCandidateLoader(scanner, reader);

		List<ParsedPostCandidate> candidates = loader.load(tempDir);

		assertThat(candidates)
				.extracting(ParsedPostCandidate::sourcePath)
				.containsExactly("blog/a-post/index.md", "project/b-post/index.md");
		assertThat(reader.readSourcePaths())
				.containsExactly("blog/a-post/index.md", "project/b-post/index.md");
	}

	@Test
	void resultOrderIsSortedBySourcePath() {
		PostSourceFile zPost = sourceFile("project/z-post/index.md");
		PostSourceFile aPost = sourceFile("blog/a-post/index.md");
		PostSourceFile algorithmPost = sourceFile("algorithm/boj/1000/index.md");
		FakeScanner scanner = new FakeScanner(List.of(zPost, aPost, algorithmPost));
		FakeReader reader = new FakeReader();
		PostSourceCandidateLoader loader = new PostSourceCandidateLoader(scanner, reader);

		List<ParsedPostCandidate> candidates = loader.load(tempDir);

		assertThat(candidates)
				.extracting(ParsedPostCandidate::sourcePath)
				.containsExactly(
						"algorithm/boj/1000/index.md",
						"blog/a-post/index.md",
						"project/z-post/index.md");
		assertThat(reader.readSourcePaths())
				.containsExactly(
						"algorithm/boj/1000/index.md",
						"blog/a-post/index.md",
						"project/z-post/index.md");
	}

	@Test
	void emptyScannerResultReturnsEmptyList() {
		PostSourceCandidateLoader loader = new PostSourceCandidateLoader(new FakeScanner(List.of()), new FakeReader());

		List<ParsedPostCandidate> candidates = loader.load(tempDir);

		assertThat(candidates).isEmpty();
	}

	@Test
	void scannerFailurePropagates() {
		PostSourceScanException exception = new PostSourceScanException("scan failed for " + tempDir,
				new IOException("boom"));
		PostSourceCandidateLoader loader = new PostSourceCandidateLoader(
				new FakeScanner(exception),
				new FakeReader());

		assertThatThrownBy(() -> loader.load(tempDir)).isSameAs(exception);
	}

	@Test
	void readerFailureFailsWholeLoadImmediately() {
		PostSourceFile first = sourceFile("blog/a-post/index.md");
		PostSourceFile second = sourceFile("blog/b-post/index.md");
		PostSourceFile third = sourceFile("blog/c-post/index.md");
		PostSourceReadException exception = new PostSourceReadException("read failed for blog/b-post/index.md",
				new IOException("boom"));
		FakeReader reader = new FakeReader();
		reader.failOn("blog/b-post/index.md", exception);
		PostSourceCandidateLoader loader = new PostSourceCandidateLoader(
				new FakeScanner(List.of(first, second, third)),
				reader);

		assertThatThrownBy(() -> loader.load(tempDir)).isSameAs(exception);
		assertThat(reader.readSourcePaths())
				.containsExactly("blog/a-post/index.md", "blog/b-post/index.md");
	}

	@Test
	void readerFailurePropagatesSameException() {
		PostSourceReadException exception = new PostSourceReadException("read failed for blog/a-post/index.md",
				new IOException("boom"));
		FakeReader reader = new FakeReader();
		reader.failOn("blog/a-post/index.md", exception);
		PostSourceCandidateLoader loader = new PostSourceCandidateLoader(
				new FakeScanner(List.of(sourceFile("blog/a-post/index.md"))),
				reader);

		assertThatThrownBy(() -> loader.load(tempDir)).isSameAs(exception);
	}

	@Test
	void parserFailurePropagatesSameException() {
		PostParseException exception = new PostParseException("parse failed for blog/a-post/index.md");
		FakeReader reader = new FakeReader();
		reader.failOn("blog/a-post/index.md", exception);
		PostSourceCandidateLoader loader = new PostSourceCandidateLoader(
				new FakeScanner(List.of(sourceFile("blog/a-post/index.md"))),
				reader);

		assertThatThrownBy(() -> loader.load(tempDir)).isSameAs(exception);
	}

	@Test
	void loaderOnlyCoordinatesScannerAndReader() {
		FakeScanner scanner = new FakeScanner(List.of(sourceFile("blog/a-post/index.md")));
		FakeReader reader = new FakeReader();
		PostSourceCandidateLoader loader = new PostSourceCandidateLoader(scanner, reader);

		List<ParsedPostCandidate> candidates = loader.load(tempDir);

		assertThat(scanner.scanCount()).isOne();
		assertThat(reader.readSourcePaths()).containsExactly("blog/a-post/index.md");
		assertThat(candidates).hasSize(1);
	}

	private PostSourceFile sourceFile(String sourcePath) {
		return new PostSourceFile(tempDir.resolve(sourcePath).toAbsolutePath(), sourcePath);
	}

	private static PostMetadataInput metadataInput(String slug) {
		return new PostMetadataInput(
				"A post",
				slug,
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

	private static String slugFromSourcePath(String sourcePath) {
		int lastSlash = sourcePath.lastIndexOf("/index.md");
		int previousSlash = sourcePath.lastIndexOf('/', lastSlash - 1);
		return sourcePath.substring(previousSlash + 1, lastSlash);
	}

	private static class FakeScanner extends PostSourceScanner {

		private final List<PostSourceFile> sourceFiles;
		private final RuntimeException exception;
		private int scanCount;

		FakeScanner(List<PostSourceFile> sourceFiles) {
			this.sourceFiles = sourceFiles;
			this.exception = null;
		}

		FakeScanner(RuntimeException exception) {
			this.sourceFiles = List.of();
			this.exception = exception;
		}

		@Override
		public List<PostSourceFile> scan(Path postsRoot) {
			scanCount++;
			if (exception != null) {
				throw exception;
			}
			return sourceFiles;
		}

		int scanCount() {
			return scanCount;
		}
	}

	private static class FakeReader extends PostSourceFileReader {

		private final List<String> readSourcePaths = new ArrayList<>();
		private String failingSourcePath;
		private RuntimeException exception;

		FakeReader() {
			super(new PostMarkdownParser());
		}

		@Override
		public ParsedPostCandidate read(PostSourceFile sourceFile) {
			readSourcePaths.add(sourceFile.sourcePath());
			if (sourceFile.sourcePath().equals(failingSourcePath)) {
				throw exception;
			}
			return new ParsedPostCandidate(
					sourceFile.sourcePath(),
					metadataInput(slugFromSourcePath(sourceFile.sourcePath())),
					"body");
		}

		void failOn(String sourcePath, RuntimeException exception) {
			this.failingSourcePath = sourcePath;
			this.exception = exception;
		}

		List<String> readSourcePaths() {
			return readSourcePaths;
		}
	}
}
