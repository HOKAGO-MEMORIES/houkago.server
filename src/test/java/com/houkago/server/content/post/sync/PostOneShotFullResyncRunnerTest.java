package com.houkago.server.content.post.sync;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.file.Path;
import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

@ExtendWith(OutputCaptureExtension.class)
class PostOneShotFullResyncRunnerTest {

	private static final Path POSTS_ROOT = Path.of("/path/to/houkago.posts");
	private static final String COMMIT_HASH = "example-commit-hash";

	private final PostManualFullResyncService resyncService = mock(PostManualFullResyncService.class);
	private final PostManualFullResyncProperties properties = properties();
	private final PostOneShotFullResyncRunner runner = new PostOneShotFullResyncRunner(resyncService, properties);

	@Test
	void executesExactlyOnceAndLogsMachineReadableSuccessSummary(CapturedOutput output) {
		when(resyncService.resync(eq(POSTS_ROOT), eq(COMMIT_HASH), any(Instant.class)))
				.thenReturn(result());

		runner.run(null);

		verify(resyncService).resync(eq(POSTS_ROOT), eq(COMMIT_HASH), any(Instant.class));
		assertThat(runner.getExitCode()).isZero();
		assertThat(output).contains("event=post_full_resync status=SUCCESS");
		assertThat(output).contains("commitHash=" + COMMIT_HASH);
		assertThat(output).contains("CREATED=1");
		assertThat(output).contains("UPDATED=2");
		assertThat(output).contains("TOUCHED=3");
		assertThat(output).contains("DELETED=4");
	}

	@Test
	void failureIsLoggedAndProducesNonZeroExitCodeWithoutSuccessSummary(CapturedOutput output) {
		RuntimeException exception = new RuntimeException("database unavailable");
		when(resyncService.resync(eq(POSTS_ROOT), eq(COMMIT_HASH), any(Instant.class)))
				.thenThrow(exception);

		assertThatThrownBy(() -> runner.run(null)).isSameAs(exception);

		assertThat(runner.getExitCode()).isEqualTo(1);
		assertThat(output).contains("event=post_full_resync status=FAILED");
		assertThat(output).contains("commitHash=" + COMMIT_HASH);
		assertThat(output).doesNotContain("status=SUCCESS");
	}

	@Test
	void missingPostsRootFailsBeforeServiceCall(CapturedOutput output) {
		properties.setPostsRoot(null);

		assertThatThrownBy(() -> runner.run(null))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("houkago.resync.posts-root");

		verify(resyncService, never()).resync(any(), any(), any());
		assertThat(runner.getExitCode()).isEqualTo(1);
		assertThat(output).contains("status=FAILED");
	}

	@Test
	void blankCommitHashFailsBeforeServiceCall(CapturedOutput output) {
		properties.setCommitHash("   ");

		assertThatThrownBy(() -> runner.run(null))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("houkago.resync.commit-hash");

		verify(resyncService, never()).resync(any(), any(), any());
		assertThat(runner.getExitCode()).isEqualTo(1);
		assertThat(output).contains("commitHash=UNSET");
	}

	private static PostManualFullResyncProperties properties() {
		PostManualFullResyncProperties properties = new PostManualFullResyncProperties();
		properties.setPostsRoot(POSTS_ROOT.toString());
		properties.setCommitHash(COMMIT_HASH);
		return properties;
	}

	private static PostManualFullResyncResult result() {
		return new PostManualFullResyncResult(
				6,
				1,
				2,
				3,
				6,
				4,
				COMMIT_HASH,
				Instant.parse("2026-08-07T00:00:00Z"));
	}
}
