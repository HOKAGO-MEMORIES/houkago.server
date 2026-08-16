package com.houkago.server.content.post.asset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import com.houkago.server.content.post.readmodel.PostReadModelCandidatePreflight;
import com.houkago.server.content.post.readmodel.PostReadModelPreparedCandidate;
import com.houkago.server.content.post.source.ParsedPostCandidate;
import com.houkago.server.content.post.source.PostSourceCandidateLoader;

@ExtendWith(OutputCaptureExtension.class)
class PostPublicAssetSnapshotRunnerTest {

	private static final Path POSTS_ROOT = Path.of("/workspace/houkago.posts");
	private static final Path ASSET_ROOT = Path.of("/workspace/public-assets");
	private static final String GENERATION_ID = "abcdef0123456789";

	private final PostSourceCandidateLoader candidateLoader = mock(PostSourceCandidateLoader.class);
	private final PostReadModelCandidatePreflight candidatePreflight = mock(PostReadModelCandidatePreflight.class);
	private final PostPublicAssetSnapshotPublisher publisher = mock(PostPublicAssetSnapshotPublisher.class);
	private final PostPublicAssetSnapshotProperties properties = properties();
	private final PostPublicAssetSnapshotRunner runner = new PostPublicAssetSnapshotRunner(
			candidateLoader,
			candidatePreflight,
			publisher,
			properties);

	@Test
	void stagesThenActivatesSnapshotAndLogsSummary(CapturedOutput output) {
		List<ParsedPostCandidate> candidates = List.of(mock(ParsedPostCandidate.class));
		List<PostReadModelPreparedCandidate> preparedCandidates = List.of(mock(PostReadModelPreparedCandidate.class));
		PostPublicAssetSnapshot snapshot = new PostPublicAssetSnapshot(
				ASSET_ROOT,
				ASSET_ROOT.resolve("releases").resolve(GENERATION_ID),
				GENERATION_ID,
				258,
				333,
				8_087_458);
		when(candidateLoader.load(POSTS_ROOT)).thenReturn(candidates);
		when(candidatePreflight.prepareAll(candidates)).thenReturn(preparedCandidates);
		when(publisher.stage(POSTS_ROOT, ASSET_ROOT, preparedCandidates, GENERATION_ID)).thenReturn(snapshot);

		runner.run(null);

		InOrder order = inOrder(candidateLoader, candidatePreflight, publisher);
		order.verify(candidateLoader).load(POSTS_ROOT);
		order.verify(candidatePreflight).prepareAll(candidates);
		order.verify(publisher).stage(POSTS_ROOT, ASSET_ROOT, preparedCandidates, GENERATION_ID);
		order.verify(publisher).activate(snapshot);
		assertThat(runner.getExitCode()).isZero();
		assertThat(output).contains("event=post_public_asset_snapshot status=SUCCESS");
		assertThat(output).contains("generationId=" + GENERATION_ID);
		assertThat(output).contains("publicPostCount=258");
		assertThat(output).contains("assetCount=333");
		assertThat(output).contains("totalBytes=8087458");
	}

	@Test
	void stageFailurePropagatesWithoutActivation(CapturedOutput output) {
		List<ParsedPostCandidate> candidates = List.of(mock(ParsedPostCandidate.class));
		List<PostReadModelPreparedCandidate> preparedCandidates = List.of(mock(PostReadModelPreparedCandidate.class));
		PostPublicAssetPublicationException exception = new PostPublicAssetPublicationException("copy failed");
		when(candidateLoader.load(POSTS_ROOT)).thenReturn(candidates);
		when(candidatePreflight.prepareAll(candidates)).thenReturn(preparedCandidates);
		when(publisher.stage(POSTS_ROOT, ASSET_ROOT, preparedCandidates, GENERATION_ID)).thenThrow(exception);

		assertThatThrownBy(() -> runner.run(null)).isSameAs(exception);

		verify(publisher, never()).activate(org.mockito.ArgumentMatchers.any());
		assertThat(runner.getExitCode()).isOne();
		assertThat(output).contains("event=post_public_asset_snapshot status=FAILED");
		assertThat(output).doesNotContain("status=SUCCESS");
	}

	@Test
	void missingGenerationFailsBeforeLoadingCandidates(CapturedOutput output) {
		properties.setGenerationId("   ");

		assertThatThrownBy(() -> runner.run(null))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("houkago.assets.publication.generation-id");

		verify(candidateLoader, never()).load(org.mockito.ArgumentMatchers.any());
		assertThat(runner.getExitCode()).isOne();
		assertThat(output).contains("generationId=UNSET");
	}

	private static PostPublicAssetSnapshotProperties properties() {
		PostPublicAssetSnapshotProperties properties = new PostPublicAssetSnapshotProperties();
		properties.setPostsRoot(POSTS_ROOT.toString());
		properties.setAssetRoot(ASSET_ROOT.toString());
		properties.setGenerationId(GENERATION_ID);
		return properties;
	}
}
