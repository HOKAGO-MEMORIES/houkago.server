package com.houkago.server.content.post.readmodel;

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

class PostReadModelCandidatePreflightTest {

	private final PostReadModelCandidateProcessor processor = mock(PostReadModelCandidateProcessor.class);
	private final PostReadModelCandidatePreflight preflight = new PostReadModelCandidatePreflight(processor);

	@Test
	void preparesEveryCandidateInSourceOrder() {
		ParsedPostCandidate first = candidate("first", "blog/first/index.md");
		ParsedPostCandidate second = candidate("second", "blog/second/index.md");
		PostReadModelPreparedCandidate preparedFirst = prepared("first", first.sourcePath());
		PostReadModelPreparedCandidate preparedSecond = prepared("second", second.sourcePath());
		when(processor.prepare(first)).thenReturn(preparedFirst);
		when(processor.prepare(second)).thenReturn(preparedSecond);

		List<PostReadModelPreparedCandidate> result = preflight.prepareAll(List.of(first, second));

		assertThat(result).containsExactly(preparedFirst, preparedSecond);
		verify(processor).prepare(first);
		verify(processor).prepare(second);
	}

	@Test
	void duplicateSlugIsRejected() {
		ParsedPostCandidate first = candidate("same", "blog/first/index.md");
		ParsedPostCandidate second = candidate("same", "blog/second/index.md");
		when(processor.prepare(first)).thenReturn(prepared("same", first.sourcePath()));
		when(processor.prepare(second)).thenReturn(prepared("same", second.sourcePath()));

		assertThatThrownBy(() -> preflight.prepareAll(List.of(first, second)))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("slug")
				.hasMessageContaining("same");
	}

	@Test
	void duplicateSourcePathIsRejected() {
		ParsedPostCandidate first = candidate("first", "blog/shared/index.md");
		ParsedPostCandidate second = candidate("second", "blog/other/index.md");
		when(processor.prepare(first)).thenReturn(prepared("first", "blog/shared/index.md"));
		when(processor.prepare(second)).thenReturn(prepared("second", "blog/shared/index.md"));

		assertThatThrownBy(() -> preflight.prepareAll(List.of(first, second)))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("sourcePath")
				.hasMessageContaining("blog/shared/index.md");
	}

	@Test
	void metadataOrChecksumPreparationFailurePropagates() {
		ParsedPostCandidate candidate = candidate("invalid", "blog/invalid/index.md");
		IllegalArgumentException exception = new IllegalArgumentException("metadata invalid");
		when(processor.prepare(candidate)).thenThrow(exception);

		assertThatThrownBy(() -> preflight.prepareAll(List.of(candidate)))
				.isSameAs(exception);
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

	private static PostReadModelPreparedCandidate prepared(String slug, String sourcePath) {
		return new PostReadModelPreparedCandidate(
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
