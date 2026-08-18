package com.houkago.server.content.post.preparation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.houkago.server.content.post.metadata.PostMetadataInput;
import com.houkago.server.content.post.metadata.PostMetadataMapping;
import com.houkago.server.content.post.policy.PostSourceStatus;
import com.houkago.server.content.post.policy.PostSyncStatus;
import com.houkago.server.content.post.policy.PostVisibility;
import com.houkago.server.content.post.source.ParsedPostCandidate;

class PostCandidatePreflightTest {

	private final PostCandidatePreparer preparer = mock(PostCandidatePreparer.class);
	private final PostCandidatePreflight preflight = new PostCandidatePreflight(preparer);

	@Test
	void preparesEveryCandidateInSourceOrder() {
		ParsedPostCandidate first = candidate("first", "blog/first/index.md");
		ParsedPostCandidate second = candidate("second", "blog/second/index.md");
		PreparedPostCandidate preparedFirst = prepared("first", first.sourcePath());
		PreparedPostCandidate preparedSecond = prepared("second", second.sourcePath());
		when(preparer.prepare(first)).thenReturn(preparedFirst);
		when(preparer.prepare(second)).thenReturn(preparedSecond);

		List<PreparedPostCandidate> result = preflight.prepareAll(List.of(first, second));

		assertThat(result).containsExactly(preparedFirst, preparedSecond);
		verify(preparer).prepare(first);
		verify(preparer).prepare(second);
	}

	@Test
	void duplicateSlugIsRejected() {
		ParsedPostCandidate first = candidate("same", "blog/first/index.md");
		ParsedPostCandidate second = candidate("same", "blog/second/index.md");
		when(preparer.prepare(first)).thenReturn(prepared("same", first.sourcePath()));
		when(preparer.prepare(second)).thenReturn(prepared("same", second.sourcePath()));

		assertThatThrownBy(() -> preflight.prepareAll(List.of(first, second)))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("slug")
				.hasMessageContaining("same");
	}

	@Test
	void duplicateSourcePathIsRejected() {
		ParsedPostCandidate first = candidate("first", "blog/shared/index.md");
		ParsedPostCandidate second = candidate("second", "blog/other/index.md");
		when(preparer.prepare(first)).thenReturn(prepared("first", "blog/shared/index.md"));
		when(preparer.prepare(second)).thenReturn(prepared("second", "blog/shared/index.md"));

		assertThatThrownBy(() -> preflight.prepareAll(List.of(first, second)))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("sourcePath")
				.hasMessageContaining("blog/shared/index.md");
	}

	@Test
	void preparationFailurePropagates() {
		ParsedPostCandidate candidate = candidate("invalid", "blog/invalid/index.md");
		IllegalArgumentException exception = new IllegalArgumentException("metadata invalid");
		when(preparer.prepare(candidate)).thenThrow(exception);

		assertThatThrownBy(() -> preflight.prepareAll(List.of(candidate))).isSameAs(exception);
	}

	private static ParsedPostCandidate candidate(String slug, String sourcePath) {
		return new ParsedPostCandidate(
				sourcePath,
				new PostMetadataInput(
						"Post " + slug,
						slug,
						"2026-08-07",
						"Description",
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

	private static PreparedPostCandidate prepared(String slug, String sourcePath) {
		return new PreparedPostCandidate(
				new PostMetadataMapping(
						"Post " + slug,
						slug,
						LocalDate.parse("2026-08-07"),
						"Description",
						"blog",
						PostSourceStatus.PUBLISHED,
						PostSyncStatus.ACTIVE,
						PostVisibility.PUBLIC,
						List.of("java"),
						null,
						null,
						null,
						false,
						null,
						null),
				"body",
				sourcePath,
				"checksum-" + slug);
	}
}
