package com.houkago.server.content.post.sync;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.transaction.annotation.Transactional;

import com.houkago.server.content.post.readmodel.PostReadModel;
import com.houkago.server.content.post.preparation.PostCandidatePreflight;
import com.houkago.server.content.post.preparation.PreparedPostCandidate;
import com.houkago.server.content.post.readmodel.PostReadModelRetirementService;
import com.houkago.server.content.post.readmodel.PostReadModelUpsertConflictException;
import com.houkago.server.content.post.readmodel.PostReadModelUpsertResult;
import com.houkago.server.content.post.readmodel.PostReadModelUpsertService;
import com.houkago.server.content.post.readmodel.PostReadModelUpsertStatus;
import com.houkago.server.content.post.source.ParsedPostCandidate;
import com.houkago.server.content.post.source.PostSourceCandidateLoader;
import com.houkago.server.content.post.source.PostSourceScanException;
import com.houkago.server.content.post.metadata.PostMetadataInput;
import com.houkago.server.content.post.metadata.PostMetadataMapping;
import com.houkago.server.content.post.policy.PostSourceStatus;
import com.houkago.server.content.post.policy.PostSyncStatus;
import com.houkago.server.content.post.policy.PostVisibility;

class PostFullResyncServiceTest {

	private static final Path POSTS_ROOT = Path.of("/tmp/houkago.posts");
	private static final String COMMIT_HASH = "commit-a";
	private static final Instant SYNCED_AT = Instant.parse("2026-07-04T00:00:00Z");

	private final PostSourceCandidateLoader candidateLoader = mock(PostSourceCandidateLoader.class);
	private final PostCandidatePreflight candidatePreflight = mock(PostCandidatePreflight.class);
	private final PostReadModelUpsertService upsertService = mock(PostReadModelUpsertService.class);
	private final PostReadModelRetirementService retirementService = mock(PostReadModelRetirementService.class);
	private final PostFullResyncService service = new PostFullResyncService(
			candidateLoader,
			candidatePreflight,
			upsertService,
			retirementService);

	@Test
	void upsertsCandidatesSequentially() {
		ParsedPostCandidate first = candidate("first-post", "blog/first-post/index.md");
		ParsedPostCandidate second = candidate("second-post", "blog/second-post/index.md");
		List<ParsedPostCandidate> candidates = List.of(first, second);
		List<PreparedPostCandidate> prepared = stubPreflight(candidates);
		when(candidateLoader.load(POSTS_ROOT)).thenReturn(candidates);
		when(upsertService.upsertPreparedCandidate(prepared.get(0), COMMIT_HASH, SYNCED_AT))
				.thenReturn(result(PostReadModelUpsertStatus.CREATED));
		when(upsertService.upsertPreparedCandidate(prepared.get(1), COMMIT_HASH, SYNCED_AT))
				.thenReturn(result(PostReadModelUpsertStatus.UPDATED));
		when(retirementService.retireMissingSources(Set.of(first.sourcePath(), second.sourcePath()), COMMIT_HASH,
				SYNCED_AT)).thenReturn(0);

		service.resync(POSTS_ROOT, COMMIT_HASH, SYNCED_AT);

		InOrder order = inOrder(upsertService, retirementService);
		order.verify(upsertService).upsertPreparedCandidate(prepared.get(0), COMMIT_HASH, SYNCED_AT);
		order.verify(upsertService).upsertPreparedCandidate(prepared.get(1), COMMIT_HASH, SYNCED_AT);
		order.verify(retirementService).retireMissingSources(Set.of(first.sourcePath(), second.sourcePath()),
				COMMIT_HASH, SYNCED_AT);
		order.verifyNoMoreInteractions();
	}

	@Test
	void aggregatesCreatedUpdatedAndTouchedCounts() {
		ParsedPostCandidate first = candidate("created-post", "blog/created-post/index.md");
		ParsedPostCandidate second = candidate("updated-post", "blog/updated-post/index.md");
		ParsedPostCandidate third = candidate("touched-post", "blog/touched-post/index.md");
		ParsedPostCandidate fourth = candidate("another-created-post", "blog/another-created-post/index.md");
		List<ParsedPostCandidate> candidates = List.of(first, second, third, fourth);
		List<PreparedPostCandidate> prepared = stubPreflight(candidates);
		when(candidateLoader.load(POSTS_ROOT)).thenReturn(candidates);
		when(upsertService.upsertPreparedCandidate(prepared.get(0), COMMIT_HASH, SYNCED_AT))
				.thenReturn(result(PostReadModelUpsertStatus.CREATED));
		when(upsertService.upsertPreparedCandidate(prepared.get(1), COMMIT_HASH, SYNCED_AT))
				.thenReturn(result(PostReadModelUpsertStatus.UPDATED));
		when(upsertService.upsertPreparedCandidate(prepared.get(2), COMMIT_HASH, SYNCED_AT))
				.thenReturn(result(PostReadModelUpsertStatus.TOUCHED));
		when(upsertService.upsertPreparedCandidate(prepared.get(3), COMMIT_HASH, SYNCED_AT))
				.thenReturn(result(PostReadModelUpsertStatus.CREATED));
		when(retirementService.retireMissingSources(
				Set.of(first.sourcePath(), second.sourcePath(), third.sourcePath(), fourth.sourcePath()),
				COMMIT_HASH,
				SYNCED_AT)).thenReturn(2);

		PostFullResyncResult result = service.resync(POSTS_ROOT, COMMIT_HASH, SYNCED_AT);

		assertThat(result.candidateCount()).isEqualTo(4);
		assertThat(result.createdCount()).isEqualTo(2);
		assertThat(result.updatedCount()).isEqualTo(1);
		assertThat(result.touchedCount()).isEqualTo(1);
		assertThat(result.totalUpsertedCount()).isEqualTo(4);
		assertThat(result.totalUpsertedCount())
				.isEqualTo(result.createdCount() + result.updatedCount() + result.touchedCount());
		assertThat(result.deletedCount()).isEqualTo(2);
		assertThat(result.commitHash()).isEqualTo(COMMIT_HASH);
		assertThat(result.syncedAt()).isEqualTo(SYNCED_AT);
	}

	@Test
	void emptyCandidateListReturnsZeroSummary() {
		when(candidateLoader.load(POSTS_ROOT)).thenReturn(List.of());
		when(candidatePreflight.prepareAll(List.of())).thenReturn(List.of());

		PostFullResyncResult result = service.resync(POSTS_ROOT, COMMIT_HASH, SYNCED_AT);

		assertThat(result.candidateCount()).isZero();
		assertThat(result.createdCount()).isZero();
		assertThat(result.updatedCount()).isZero();
		assertThat(result.touchedCount()).isZero();
		assertThat(result.totalUpsertedCount()).isZero();
		assertThat(result.deletedCount()).isZero();
		assertThat(result.commitHash()).isEqualTo(COMMIT_HASH);
		assertThat(result.syncedAt()).isEqualTo(SYNCED_AT);
		verify(retirementService, never()).retireMissingSources(any(), any(), any());
	}

	@Test
	void preservesCandidateProcessingOrder() {
		ParsedPostCandidate first = candidate("a-post", "blog/a-post/index.md");
		ParsedPostCandidate second = candidate("b-post", "blog/b-post/index.md");
		ParsedPostCandidate third = candidate("c-post", "blog/c-post/index.md");
		List<ParsedPostCandidate> candidates = List.of(first, second, third);
		List<PreparedPostCandidate> prepared = stubPreflight(candidates);
		when(candidateLoader.load(POSTS_ROOT)).thenReturn(candidates);
		when(upsertService.upsertPreparedCandidate(any(), any(), any()))
				.thenReturn(result(PostReadModelUpsertStatus.UPDATED));
		when(retirementService.retireMissingSources(any(), any(), any())).thenReturn(0);

		service.resync(POSTS_ROOT, COMMIT_HASH, SYNCED_AT);

		InOrder order = inOrder(upsertService);
		order.verify(upsertService).upsertPreparedCandidate(prepared.get(0), COMMIT_HASH, SYNCED_AT);
		order.verify(upsertService).upsertPreparedCandidate(prepared.get(1), COMMIT_HASH, SYNCED_AT);
		order.verify(upsertService).upsertPreparedCandidate(prepared.get(2), COMMIT_HASH, SYNCED_AT);
	}

	@Test
	void loaderFailurePropagates() {
		PostSourceScanException exception = new PostSourceScanException("scan failed", new IOException("boom"));
		when(candidateLoader.load(POSTS_ROOT)).thenThrow(exception);

		assertThatThrownBy(() -> service.resync(POSTS_ROOT, COMMIT_HASH, SYNCED_AT))
				.isSameAs(exception);
		verify(retirementService, never()).retireMissingSources(any(), any(), any());
	}

	@Test
	void upsertFailureFailsFastAndDoesNotProcessLaterCandidates() {
		ParsedPostCandidate first = candidate("first-post", "blog/first-post/index.md");
		ParsedPostCandidate second = candidate("second-post", "blog/second-post/index.md");
		ParsedPostCandidate third = candidate("third-post", "blog/third-post/index.md");
		PostReadModelUpsertConflictException exception = new PostReadModelUpsertConflictException("conflict");
		List<ParsedPostCandidate> candidates = List.of(first, second, third);
		List<PreparedPostCandidate> prepared = stubPreflight(candidates);
		when(candidateLoader.load(POSTS_ROOT)).thenReturn(candidates);
		when(upsertService.upsertPreparedCandidate(prepared.get(0), COMMIT_HASH, SYNCED_AT))
				.thenReturn(result(PostReadModelUpsertStatus.CREATED));
		when(upsertService.upsertPreparedCandidate(prepared.get(1), COMMIT_HASH, SYNCED_AT)).thenThrow(exception);

		assertThatThrownBy(() -> service.resync(POSTS_ROOT, COMMIT_HASH, SYNCED_AT))
				.isSameAs(exception);
		verify(upsertService, never()).upsertPreparedCandidate(prepared.get(2), COMMIT_HASH, SYNCED_AT);
		verify(retirementService, never()).retireMissingSources(any(), any(), any());
	}

	@Test
	void preflightFailureHappensBeforeAnyDatabaseWrite() {
		ParsedPostCandidate first = candidate("first-post", "blog/first-post/index.md");
		ParsedPostCandidate second = candidate("duplicate-post", "blog/duplicate-post/index.md");
		List<ParsedPostCandidate> candidates = List.of(first, second);
		IllegalArgumentException exception = new IllegalArgumentException("Duplicate post candidate slug: duplicate-post");
		when(candidateLoader.load(POSTS_ROOT)).thenReturn(candidates);
		when(candidatePreflight.prepareAll(candidates)).thenThrow(exception);

		assertThatThrownBy(() -> service.resync(POSTS_ROOT, COMMIT_HASH, SYNCED_AT))
				.isSameAs(exception);
		verify(upsertService, never()).upsertPreparedCandidate(any(), any(), any());
		verify(retirementService, never()).retireMissingSources(any(), any(), any());
	}

	@Test
	void nullPostsRootRejected() {
		assertThatThrownBy(() -> service.resync(null, COMMIT_HASH, SYNCED_AT))
				.isInstanceOf(NullPointerException.class)
				.hasMessageContaining("postsRoot is required");
	}

	@Test
	void blankCommitHashRejected() {
		assertThatThrownBy(() -> service.resync(POSTS_ROOT, "   ", SYNCED_AT))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("commitHash is required");
	}

	@Test
	void nullSyncedAtRejected() {
		assertThatThrownBy(() -> service.resync(POSTS_ROOT, COMMIT_HASH, null))
				.isInstanceOf(NullPointerException.class)
				.hasMessageContaining("syncedAt is required");
	}

	@Test
	void serviceDoesNotDeclareLargeTransaction() throws NoSuchMethodException {
		Method resyncMethod = PostFullResyncService.class.getDeclaredMethod(
				"resync",
				Path.class,
				String.class,
				Instant.class);

		assertThat(PostFullResyncService.class.isAnnotationPresent(Transactional.class)).isFalse();
		assertThat(resyncMethod.isAnnotationPresent(Transactional.class)).isFalse();
	}

	private static ParsedPostCandidate candidate(String slug, String sourcePath) {
		return new ParsedPostCandidate(
				sourcePath,
				new PostMetadataInput(
						"Post " + slug,
						slug,
						"2026-07-04",
						"A useful post.",
						"blog",
						"published",
						List.of("java"),
						null,
						null,
						null,
						false,
						null,
						null),
				"body");
	}

	private static PostReadModelUpsertResult result(PostReadModelUpsertStatus status) {
		return new PostReadModelUpsertResult(mock(PostReadModel.class), status);
	}

	private List<PreparedPostCandidate> stubPreflight(List<ParsedPostCandidate> candidates) {
		List<PreparedPostCandidate> prepared = candidates.stream()
				.map(PostFullResyncServiceTest::preparedCandidate)
				.toList();
		when(candidatePreflight.prepareAll(candidates)).thenReturn(prepared);
		return prepared;
	}

	private static PreparedPostCandidate preparedCandidate(ParsedPostCandidate candidate) {
		PostMetadataInput input = candidate.metadataInput();
		return new PreparedPostCandidate(
				new PostMetadataMapping(
						input.title(),
						input.slug(),
						java.time.LocalDate.parse(input.date()),
						input.description(),
						input.category(),
						PostSourceStatus.PUBLISHED,
						PostSyncStatus.ACTIVE,
						PostVisibility.PUBLIC,
						input.tags(),
						null,
						null,
						null,
						false,
						null,
						null),
				candidate.rawBody(),
				candidate.sourcePath(),
				"checksum-" + input.slug());
	}
}
