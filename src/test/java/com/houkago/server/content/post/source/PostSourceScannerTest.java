package com.houkago.server.content.post.source;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PostSourceScannerTest {

	@TempDir
	private Path postsRoot;

	private final PostSourceScanner scanner = new PostSourceScanner();

	@Test
	void validTreeScansIndexMdFiles() throws IOException {
		createFile("blog/my-post/index.md");
		createFile("project/my-project/index.md");
		createFile("cs/os/index.md");
		createFile("algorithm/boj/1000/index.md");

		List<PostSourceFile> sourceFiles = scanner.scan(postsRoot);

		assertThat(sourceFiles)
				.extracting(PostSourceFile::sourcePath)
				.containsExactly(
						"algorithm/boj/1000/index.md",
						"blog/my-post/index.md",
						"cs/os/index.md",
						"project/my-project/index.md");
		assertThat(sourceFiles)
				.extracting(PostSourceFile::absolutePath)
				.allMatch(Path::isAbsolute);
	}

	@Test
	void nonIndexMarkdownIgnored() throws IOException {
		createFile("blog/my-post/post.md");
		createFile("blog/my-post/notes.md");
		createFile("blog/my-post/index.md");

		List<PostSourceFile> sourceFiles = scanner.scan(postsRoot);

		assertThat(sourceFiles)
				.extracting(PostSourceFile::sourcePath)
				.containsExactly("blog/my-post/index.md");
	}

	@Test
	void readmeIgnored() throws IOException {
		createFile("README.md");
		createFile("blog/my-post/README.md");
		createFile("blog/my-post/index.md");

		List<PostSourceFile> sourceFiles = scanner.scan(postsRoot);

		assertThat(sourceFiles)
				.extracting(PostSourceFile::sourcePath)
				.containsExactly("blog/my-post/index.md");
	}

	@Test
	void assetsIgnored() throws IOException {
		createFile("blog/my-post/index.md");
		createFile("blog/my-post/assets/index.md");

		List<PostSourceFile> sourceFiles = scanner.scan(postsRoot);

		assertThat(sourceFiles)
				.extracting(PostSourceFile::sourcePath)
				.containsExactly("blog/my-post/index.md");
	}

	@Test
	void hiddenDirectoryIgnored() throws IOException {
		createFile(".drafts/secret/index.md");
		createFile("blog/my-post/index.md");

		List<PostSourceFile> sourceFiles = scanner.scan(postsRoot);

		assertThat(sourceFiles)
				.extracting(PostSourceFile::sourcePath)
				.containsExactly("blog/my-post/index.md");
	}

	@Test
	void skippedDirectoryTreeIsNotScanned() throws IOException {
		createFile(".git/hooks/index.md");
		createFile(".obsidian/templates/index.md");
		createFile(".trash/old/index.md");
		createFile(".cache/generated/index.md");
		createFile(".next/server/index.md");
		createFile("node_modules/pkg/index.md");
		createFile("dist/static/index.md");
		createFile("build/reports/index.md");
		createFile("blog/my-post/index.md");

		List<PostSourceFile> sourceFiles = scanner.scan(postsRoot);

		assertThat(sourceFiles)
				.extracting(PostSourceFile::sourcePath)
				.containsExactly("blog/my-post/index.md");
	}

	@Test
	void archiveDirectoryIsNotExcludedByNameAlone() throws IOException {
		createFile("archive/old-post/index.md");
		createFile("blog/my-post/index.md");

		List<PostSourceFile> sourceFiles = scanner.scan(postsRoot);

		assertThat(sourceFiles)
				.extracting(PostSourceFile::sourcePath)
				.containsExactly("archive/old-post/index.md", "blog/my-post/index.md");
	}

	@Test
	void sourcePathUsesSlash() throws IOException {
		createFile("algorithm/boj/1000/index.md");

		PostSourceFile sourceFile = scanner.scan(postsRoot).getFirst();

		assertThat(sourceFile.sourcePath()).isEqualTo("algorithm/boj/1000/index.md");
		assertThat(sourceFile.sourcePath()).doesNotContain("\\");
	}

	@Test
	void resultsSortedBySourcePath() throws IOException {
		createFile("project/z-post/index.md");
		createFile("blog/a-post/index.md");
		createFile("algorithm/boj/1000/index.md");

		List<PostSourceFile> sourceFiles = scanner.scan(postsRoot);

		assertThat(sourceFiles)
				.extracting(PostSourceFile::sourcePath)
				.containsExactly(
						"algorithm/boj/1000/index.md",
						"blog/a-post/index.md",
						"project/z-post/index.md");
	}

	@Test
	void missingRootRejected() {
		Path missingRoot = postsRoot.resolve("missing");

		assertThatThrownBy(() -> scanner.scan(missingRoot))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("postsRoot does not exist")
				.hasMessageContaining(missingRoot.toString());
	}

	@Test
	void fileRootRejected() throws IOException {
		Path fileRoot = createFile("not-a-directory.md");

		assertThatThrownBy(() -> scanner.scan(fileRoot))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("postsRoot must be a directory")
				.hasMessageContaining(fileRoot.toString());
	}

	@Test
	void nullRootRejected() {
		assertThatThrownBy(() -> scanner.scan(null))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("postsRoot must not be null");
	}

	private Path createFile(String relativePath) throws IOException {
		Path file = postsRoot.resolve(relativePath);
		if (file.getParent() != null) {
			Files.createDirectories(file.getParent());
		}
		return Files.writeString(file, "not read by scanner");
	}
}
