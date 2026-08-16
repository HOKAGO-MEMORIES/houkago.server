package com.houkago.server.content.post.asset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.houkago.server.content.post.metadata.PostMetadataMapping;
import com.houkago.server.content.post.policy.PostSourceStatus;
import com.houkago.server.content.post.policy.PostSyncStatus;
import com.houkago.server.content.post.policy.PostVisibility;
import com.houkago.server.content.post.readmodel.PostReadModelPreparedCandidate;

class PostPublicAssetSnapshotPublisherTest {

	private final PostPublicAssetSnapshotPublisher publisher = new PostPublicAssetSnapshotPublisher();

	@TempDir
	Path temporaryDirectory;

	@Test
	void stagesAndAtomicallyActivatesPublishedAssets() throws IOException {
		Path postsRoot = postsRoot();
		Path assetRoot = assetRoot();
		writePost(postsRoot, "blog/first-post/index.md");
		write(postsRoot.resolve("blog/first-post/assets/cover.png"), "cover");
		write(postsRoot.resolve("blog/first-post/assets/diagrams/flow.png"), "flow");
		writePost(postsRoot, "project/no-assets/index.md");

		PostPublicAssetSnapshot snapshot = publisher.stage(
				postsRoot,
				assetRoot,
				List.of(
						candidate("first-post", "blog/first-post/index.md", publicMetadata("first-post"),
								"![cover](./assets/cover.png)\n![flow](./assets/diagrams/flow.png)"),
						candidate("no-assets", "project/no-assets/index.md", publicMetadata("no-assets"), "body")),
				"commit-a");

		assertThat(snapshot.publicPostCount()).isEqualTo(2);
		assertThat(snapshot.assetCount()).isEqualTo(2);
		assertThat(snapshot.totalBytes()).isEqualTo(9);
		assertThat(snapshot.releaseDirectory().resolve("posts/first-post/cover.png")).hasContent("cover");
		assertThat(snapshot.releaseDirectory().resolve("posts/first-post/diagrams/flow.png")).hasContent("flow");
		assertThat(assetRoot.resolve("current")).doesNotExist();

		publisher.activate(snapshot);

		Path current = assetRoot.resolve("current");
		assertThat(Files.isSymbolicLink(current)).isTrue();
		assertThat(current.resolve("posts/first-post/cover.png")).hasContent("cover");
	}

	@Test
	void publishesOnlyPostsAllowedBySharedVisibilityPolicy() throws IOException {
		Path postsRoot = postsRoot();
		Path assetRoot = assetRoot();
		writePostWithAsset(postsRoot, "blog/public-post/index.md", "public");
		writePostWithAsset(postsRoot, "blog/draft-post/index.md", "draft");
		writePostWithAsset(postsRoot, "blog/private-post/index.md", "private");
		writePostWithAsset(postsRoot, "blog/deleted-post/index.md", "deleted");

		PostPublicAssetSnapshot snapshot = publisher.stage(
				postsRoot,
				assetRoot,
				List.of(
						candidate("public-post", "blog/public-post/index.md", publicMetadata("public-post"), "body"),
						candidate("draft-post", "blog/draft-post/index.md",
								metadata("draft-post", PostSourceStatus.DRAFT, PostSyncStatus.ACTIVE, PostVisibility.PRIVATE),
								"body"),
						candidate("private-post", "blog/private-post/index.md",
								metadata("private-post", PostSourceStatus.PUBLISHED, PostSyncStatus.ACTIVE,
										PostVisibility.PRIVATE),
								"body"),
						candidate("deleted-post", "blog/deleted-post/index.md",
								metadata("deleted-post", PostSourceStatus.PUBLISHED, PostSyncStatus.DELETED,
										PostVisibility.PRIVATE),
								"body")),
				"commit-a");

		Path publishedPosts = snapshot.releaseDirectory().resolve("posts");
		assertThat(snapshot.publicPostCount()).isOne();
		assertThat(snapshot.assetCount()).isOne();
		assertThat(publishedPosts.resolve("public-post/image.png")).exists();
		assertThat(publishedPosts.resolve("draft-post")).doesNotExist();
		assertThat(publishedPosts.resolve("private-post")).doesNotExist();
		assertThat(publishedPosts.resolve("deleted-post")).doesNotExist();
	}

	@Test
	void fullSnapshotsConvergeAddsUpdatesDeletesRenamesAndUnpublishes() throws IOException {
		Path postsRoot = postsRoot();
		Path assetRoot = assetRoot();
		writePost(postsRoot, "blog/example-post/index.md");
		Path assets = postsRoot.resolve("blog/example-post/assets");
		write(assets.resolve("old.png"), "old");
		write(assets.resolve("stable.png"), "before");
		PostReadModelPreparedCandidate published = candidate(
				"example-post",
				"blog/example-post/index.md",
				publicMetadata("example-post"),
				"![old](./assets/old.png)");

		PostPublicAssetSnapshot first = publisher.stage(postsRoot, assetRoot, List.of(published), "commit-a");
		publisher.activate(first);

		Files.delete(assets.resolve("old.png"));
		write(assets.resolve("renamed.png"), "new-bytes");
		write(assets.resolve("stable.png"), "after");
		write(assets.resolve("added.png"), "added");
		PostReadModelPreparedCandidate updated = candidate(
				"example-post",
				"blog/example-post/index.md",
				publicMetadata("example-post"),
				"![new](./assets/renamed.png)");
		PostPublicAssetSnapshot second = publisher.stage(postsRoot, assetRoot, List.of(updated), "commit-b");

		assertThat(assetRoot.resolve("current/posts/example-post/old.png")).hasContent("old");
		publisher.activate(second);
		assertThat(assetRoot.resolve("current/posts/example-post/old.png")).doesNotExist();
		assertThat(assetRoot.resolve("current/posts/example-post/renamed.png")).hasContent("new-bytes");
		assertThat(assetRoot.resolve("current/posts/example-post/stable.png")).hasContent("after");
		assertThat(assetRoot.resolve("current/posts/example-post/added.png")).hasContent("added");
		assertThat(first.releaseDirectory().resolve("posts/example-post/old.png")).hasContent("old");
		assertThat(first.releaseDirectory().resolve("posts/example-post/stable.png")).hasContent("before");

		PostReadModelPreparedCandidate unpublished = candidate(
				"example-post",
				"blog/example-post/index.md",
				metadata("example-post", PostSourceStatus.DRAFT, PostSyncStatus.ACTIVE, PostVisibility.PRIVATE),
				"body");
		PostPublicAssetSnapshot third = publisher.stage(postsRoot, assetRoot, List.of(unpublished), "commit-c");
		publisher.activate(third);
		assertThat(assetRoot.resolve("current/posts/example-post")).doesNotExist();

		PostPublicAssetSnapshot deleted = publisher.stage(postsRoot, assetRoot, List.of(), "commit-d");
		publisher.activate(deleted);
		assertThat(assetRoot.resolve("current/posts")).isEmptyDirectory();
	}

	@Test
	void rejectsTraversalAbsoluteAndMissingReferences() throws IOException {
		Path postsRoot = postsRoot();
		writePost(postsRoot, "blog/example-post/index.md");
		write(postsRoot.resolve("blog/secret.txt"), "secret");
		write(postsRoot.resolve("blog/example-post/assets/existing.png"), "asset");

		assertPublicationFails(postsRoot, "![secret](../secret.txt)", "must stay within assets/");
		assertPublicationFails(postsRoot, "![secret](/tmp/secret.txt)", "Absolute post-local asset");
		assertPublicationFails(postsRoot, "![missing](./assets/missing.png)", "is missing");
	}

	@Test
	void rejectsSymlinkEscapeAndLeavesCurrentSnapshotUnchanged() throws IOException {
		Path postsRoot = postsRoot();
		Path assetRoot = assetRoot();
		writePostWithAsset(postsRoot, "blog/example-post/index.md", "safe");
		PostReadModelPreparedCandidate safe = candidate(
				"example-post",
				"blog/example-post/index.md",
				publicMetadata("example-post"),
				"![safe](./assets/image.png)");
		PostPublicAssetSnapshot first = publisher.stage(postsRoot, assetRoot, List.of(safe), "commit-a");
		publisher.activate(first);

		Path outsideFile = write(temporaryDirectory.resolve("outside.txt"), "private");
		Path symlink = postsRoot.resolve("blog/example-post/assets/escape.txt");
		Files.createSymbolicLink(symlink, outsideFile);
		PostReadModelPreparedCandidate unsafe = candidate(
				"example-post",
				"blog/example-post/index.md",
				publicMetadata("example-post"),
				"![escape](./assets/escape.txt)");

		assertThatThrownBy(() -> publisher.stage(postsRoot, assetRoot, List.of(unsafe), "commit-b"))
				.isInstanceOf(PostPublicAssetPublicationException.class)
				.hasMessageContaining("Symbolic links are not allowed");
		assertThat(assetRoot.resolve("current/posts/example-post/image.png")).hasContent("safe");
		assertThat(assetRoot.resolve("releases/commit-b")).doesNotExist();
	}

	@Test
	void sameGenerationIsIdempotentButCannotChangeBytes() throws IOException {
		Path postsRoot = postsRoot();
		Path assetRoot = assetRoot();
		writePostWithAsset(postsRoot, "blog/example-post/index.md", "same");
		PostReadModelPreparedCandidate candidate = candidate(
				"example-post",
				"blog/example-post/index.md",
				publicMetadata("example-post"),
				"body");

		PostPublicAssetSnapshot first = publisher.stage(postsRoot, assetRoot, List.of(candidate), "commit-a");
		PostPublicAssetSnapshot repeated = publisher.stage(postsRoot, assetRoot, List.of(candidate), "commit-a");
		assertThat(repeated.releaseDirectory()).isEqualTo(first.releaseDirectory());

		write(postsRoot.resolve("blog/example-post/assets/image.png"), "different");
		assertThatThrownBy(() -> publisher.stage(postsRoot, assetRoot, List.of(candidate), "commit-a"))
				.isInstanceOf(PostPublicAssetPublicationException.class)
				.hasMessageContaining("different asset bytes");
	}

	private void assertPublicationFails(Path postsRoot, String body, String message) {
		assertThatThrownBy(() -> publisher.stage(
				postsRoot,
				temporaryDirectory.resolve("assets-" + Math.abs(body.hashCode())),
				List.of(candidate("example-post", "blog/example-post/index.md", publicMetadata("example-post"), body)),
				"commit-a"))
				.isInstanceOf(PostPublicAssetPublicationException.class)
				.hasMessageContaining(message);
	}

	private Path postsRoot() throws IOException {
		return Files.createDirectory(temporaryDirectory.resolve("posts-" + System.nanoTime()));
	}

	private Path assetRoot() {
		return temporaryDirectory.resolve("public-assets-" + System.nanoTime());
	}

	private static void writePostWithAsset(Path postsRoot, String sourcePath, String content) throws IOException {
		writePost(postsRoot, sourcePath);
		write(postsRoot.resolve(sourcePath).getParent().resolve("assets/image.png"), content);
	}

	private static void writePost(Path postsRoot, String sourcePath) throws IOException {
		write(postsRoot.resolve(sourcePath), "post source");
	}

	private static Path write(Path path, String content) throws IOException {
		Files.createDirectories(path.getParent());
		return Files.writeString(path, content);
	}

	private static PostReadModelPreparedCandidate candidate(
			String slug,
			String sourcePath,
			PostMetadataMapping metadata,
			String rawBody) {
		return new PostReadModelPreparedCandidate(metadata, rawBody, sourcePath, "checksum-" + slug);
	}

	private static PostMetadataMapping publicMetadata(String slug) {
		return metadata(slug, PostSourceStatus.PUBLISHED, PostSyncStatus.ACTIVE, PostVisibility.PUBLIC);
	}

	private static PostMetadataMapping metadata(
			String slug,
			PostSourceStatus sourceStatus,
			PostSyncStatus syncStatus,
			PostVisibility visibility) {
		return new PostMetadataMapping(
				"Post " + slug,
				slug,
				LocalDate.of(2026, 8, 16),
				"Description",
				"blog",
				sourceStatus,
				syncStatus,
				visibility,
				List.of("test"),
				null,
				null,
				null,
				false,
				null,
				null);
	}
}
