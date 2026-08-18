package com.houkago.server.content.post.asset;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;

import com.houkago.server.content.post.checksum.PostChecksumCalculator;
import com.houkago.server.content.post.metadata.PostMetadataMapper;
import com.houkago.server.content.post.preparation.PostCandidatePreflight;
import com.houkago.server.content.post.preparation.PostCandidatePreparer;
import com.houkago.server.content.post.preparation.PreparedPostCandidate;
import com.houkago.server.content.post.source.ParsedPostCandidate;
import com.houkago.server.content.post.source.PostMarkdownParser;
import com.houkago.server.content.post.source.PostSourceCandidateLoader;
import com.houkago.server.content.post.source.PostSourceFileReader;
import com.houkago.server.content.post.source.PostSourceLayoutValidator;
import com.houkago.server.content.post.source.PostSourceScanner;

class PostPublicAssetSnapshotCompatibilityTest {

	@TempDir
	Path temporaryDirectory;

	@Test
	@EnabledIfEnvironmentVariable(named = "HOUKAGO_POSTS_COMPATIBILITY_ROOT", matches = ".+")
	void currentPostsCheckoutCanProducePublishedOnlySnapshot() throws Exception {
		Path postsRoot = Path.of(System.getenv("HOUKAGO_POSTS_COMPATIBILITY_ROOT"));
		PostSourceCandidateLoader loader = new PostSourceCandidateLoader(
				new PostSourceScanner(),
				new PostSourceFileReader(new PostMarkdownParser()));
		PostCandidatePreparer preparer = new PostCandidatePreparer(
				new PostMetadataMapper(),
				new PostSourceLayoutValidator(),
				new PostChecksumCalculator());
		PostCandidatePreflight preflight = new PostCandidatePreflight(preparer);
		List<ParsedPostCandidate> candidates = loader.load(postsRoot);
		List<PreparedPostCandidate> preparedCandidates = preflight.prepareAll(candidates);
		long expectedPublicPosts = preparedCandidates.stream()
				.filter(candidate -> candidate.metadata().isPubliclyVisible())
				.count();

		PostPublicAssetSnapshot snapshot = new PostPublicAssetSnapshotPublisher().stage(
				postsRoot,
				temporaryDirectory.resolve("public-assets"),
				preparedCandidates,
				"compatibility-dry-run");

		assertThat(snapshot.publicPostCount()).isEqualTo(expectedPublicPosts);
		assertThat(snapshot.assetCount()).isPositive();
		assertThat(snapshot.totalBytes()).isPositive();
		assertThat(snapshot.smokeAssetPath()).startsWith("/assets/posts/");
		assertThat(Files.isSymbolicLink(snapshot.assetRoot().resolve("current"))).isFalse();
		assertThat(snapshot.releaseDirectory()).isDirectory();
		new PostPublicAssetSnapshotPublisher().activate(snapshot.assetRoot(), snapshot.generationId());
		assertThat(Files.isSymbolicLink(snapshot.assetRoot().resolve("current"))).isTrue();
		System.out.printf(
				"Public asset compatibility: candidates=%d publicPosts=%d assets=%d bytes=%d%n",
				candidates.size(),
				snapshot.publicPostCount(),
				snapshot.assetCount(),
				snapshot.totalBytes());
	}
}
