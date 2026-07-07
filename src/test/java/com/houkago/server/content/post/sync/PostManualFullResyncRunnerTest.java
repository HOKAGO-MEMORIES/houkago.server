package com.houkago.server.content.post.sync;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.file.Path;
import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class PostManualFullResyncRunnerTest {

	private static final Path POSTS_ROOT = Path.of("/path/to/houkago.posts");
	private static final String COMMIT_HASH = "example-commit-hash";

	private final PostManualFullResyncService resyncService = mock(PostManualFullResyncService.class);
	private final PostManualFullResyncProperties properties = new PostManualFullResyncProperties();
	private final PostManualFullResyncRunner runner = new PostManualFullResyncRunner(resyncService, properties);

	@Test
	void disabledRunnerDoesNotCallService() {
		properties.setEnabled(false);

		runner.run(null);

		verify(resyncService, never()).resync(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
				org.mockito.ArgumentMatchers.any());
	}

	@Test
	void enabledRunnerCallsServiceOnce() {
		enableResync();
		when(resyncService.resync(org.mockito.ArgumentMatchers.eq(POSTS_ROOT),
				org.mockito.ArgumentMatchers.eq(COMMIT_HASH),
				org.mockito.ArgumentMatchers.any(Instant.class)))
				.thenReturn(result());

		runner.run(null);

		verify(resyncService).resync(org.mockito.ArgumentMatchers.eq(POSTS_ROOT),
				org.mockito.ArgumentMatchers.eq(COMMIT_HASH),
				org.mockito.ArgumentMatchers.any(Instant.class));
	}

	@Test
	void missingPostsRootRejectedWhenEnabled() {
		properties.setEnabled(true);
		properties.setCommitHash(COMMIT_HASH);

		assertThatThrownBy(() -> runner.run(null))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("houkago.resync.posts-root is required");
	}

	@Test
	void blankCommitHashRejectedWhenEnabled() {
		properties.setEnabled(true);
		properties.setPostsRoot(POSTS_ROOT.toString());
		properties.setCommitHash("   ");

		assertThatThrownBy(() -> runner.run(null))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("houkago.resync.commit-hash is required");
	}

	@Test
	void serviceExceptionPropagates() {
		enableResync();
		RuntimeException exception = new RuntimeException("resync failed");
		when(resyncService.resync(org.mockito.ArgumentMatchers.eq(POSTS_ROOT),
				org.mockito.ArgumentMatchers.eq(COMMIT_HASH),
				org.mockito.ArgumentMatchers.any(Instant.class)))
				.thenThrow(exception);

		assertThatThrownBy(() -> runner.run(null)).isSameAs(exception);
	}

	@Test
	void syncedAtIsPassedToService() {
		enableResync();
		ArgumentCaptor<Instant> syncedAtCaptor = ArgumentCaptor.forClass(Instant.class);
		when(resyncService.resync(org.mockito.ArgumentMatchers.eq(POSTS_ROOT),
				org.mockito.ArgumentMatchers.eq(COMMIT_HASH),
				syncedAtCaptor.capture()))
				.thenReturn(result());

		runner.run(null);

		assertThat(syncedAtCaptor.getValue()).isNotNull();
		assertThat(syncedAtCaptor.getValue()).isBeforeOrEqualTo(Instant.now());
	}

	@Test
	void runnerReturnsAfterSuccessfulResyncWithoutForcingApplicationExit() {
		enableResync();
		when(resyncService.resync(org.mockito.ArgumentMatchers.eq(POSTS_ROOT),
				org.mockito.ArgumentMatchers.eq(COMMIT_HASH),
				org.mockito.ArgumentMatchers.any(Instant.class)))
				.thenReturn(result());

		runner.run(null);

		verify(resyncService).resync(org.mockito.ArgumentMatchers.eq(POSTS_ROOT),
				org.mockito.ArgumentMatchers.eq(COMMIT_HASH),
				org.mockito.ArgumentMatchers.any(Instant.class));
	}

	private void enableResync() {
		properties.setEnabled(true);
		properties.setPostsRoot(POSTS_ROOT.toString());
		properties.setCommitHash(COMMIT_HASH);
	}

	private static PostManualFullResyncResult result() {
		return new PostManualFullResyncResult(
				2,
				1,
				1,
				0,
				2,
				COMMIT_HASH,
				Instant.parse("2026-07-04T00:00:00Z"));
	}
}
