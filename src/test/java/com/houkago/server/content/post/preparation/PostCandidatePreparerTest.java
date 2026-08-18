package com.houkago.server.content.post.preparation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.houkago.server.content.post.checksum.PostChecksumCalculator;
import com.houkago.server.content.post.checksum.PostChecksumInput;
import com.houkago.server.content.post.metadata.InvalidPostMetadataException;
import com.houkago.server.content.post.metadata.PostMetadataInput;
import com.houkago.server.content.post.metadata.PostMetadataMapper;
import com.houkago.server.content.post.metadata.PostMetadataMapping;
import com.houkago.server.content.post.policy.PostSourceStatus;
import com.houkago.server.content.post.policy.PostSyncStatus;
import com.houkago.server.content.post.policy.PostVisibility;
import com.houkago.server.content.post.source.ParsedPostCandidate;
import com.houkago.server.content.post.source.PostSourceLayoutValidator;

class PostCandidatePreparerTest {

	private final PostMetadataMapper metadataMapper = new PostMetadataMapper();
	private final PostSourceLayoutValidator sourceLayoutValidator = new PostSourceLayoutValidator();
	private final PostChecksumCalculator checksumCalculator = new PostChecksumCalculator();
	private final PostCandidatePreparer preparer = new PostCandidatePreparer(
			metadataMapper,
			sourceLayoutValidator,
			checksumCalculator);

	@Test
	void preparesNormalizedMetadataBodySourcePathAndChecksum() {
		ParsedPostCandidate candidate = publishedCandidate("## Body");

		PreparedPostCandidate prepared = preparer.prepare(candidate);

		assertThat(prepared.metadata().title()).isEqualTo("A post");
		assertThat(prepared.metadata().date()).isEqualTo(LocalDate.of(2026, 7, 3));
		assertThat(prepared.metadata().sourceStatus()).isEqualTo(PostSourceStatus.PUBLISHED);
		assertThat(prepared.metadata().syncStatus()).isEqualTo(PostSyncStatus.ACTIVE);
		assertThat(prepared.metadata().visibility()).isEqualTo(PostVisibility.PUBLIC);
		assertThat(prepared.rawBody()).isEqualTo(candidate.rawBody());
		assertThat(prepared.sourcePath()).isEqualTo(candidate.sourcePath());
		assertThat(prepared.checksum()).isEqualTo(expectedChecksum(candidate));
	}

	@Test
	void checksumChangesWhenCanonicalContentChanges() {
		PreparedPostCandidate first = preparer.prepare(publishedCandidate("## First body"));
		PreparedPostCandidate second = preparer.prepare(publishedCandidate("## Second body"));

		assertThat(first.checksum()).isNotEqualTo(second.checksum());
	}

	@Test
	void nullCandidateIsRejected() {
		assertThatThrownBy(() -> preparer.prepare(null))
				.isInstanceOf(NullPointerException.class)
				.hasMessageContaining("candidate is required");
	}

	@Test
	void invalidMetadataExceptionPropagates() {
		ParsedPostCandidate candidate = new ParsedPostCandidate(
				"blog/my-post/index.md",
				new PostMetadataInput(
						null,
						"my-post",
						"2026-07-03",
						"A useful post.",
						"blog",
						"published",
						List.of("java"),
						null,
						null,
						null,
						null,
						null,
						null),
				"body");

		assertThatThrownBy(() -> preparer.prepare(candidate))
				.isInstanceOf(InvalidPostMetadataException.class)
				.hasMessageContaining("title is required");
	}

	@Test
	void layoutFailureStopsBeforeChecksum() {
		PostMetadataMapper mapper = mock(PostMetadataMapper.class);
		PostSourceLayoutValidator layoutValidator = mock(PostSourceLayoutValidator.class);
		PostChecksumCalculator calculator = mock(PostChecksumCalculator.class);
		PostCandidatePreparer candidatePreparer = new PostCandidatePreparer(mapper, layoutValidator, calculator);
		ParsedPostCandidate candidate = publishedCandidate("## Body");
		PostMetadataMapping metadata = metadataMapper.map(candidate.metadataInput());
		RuntimeException exception = new RuntimeException("layout invalid");
		when(mapper.map(candidate.metadataInput())).thenReturn(metadata);
		doThrow(exception).when(layoutValidator).validate(candidate.sourcePath(), metadata);

		assertThatThrownBy(() -> candidatePreparer.prepare(candidate)).isSameAs(exception);

		verifyNoInteractions(calculator);
	}

	private String expectedChecksum(ParsedPostCandidate candidate) {
		PostMetadataMapping metadata = metadataMapper.map(candidate.metadataInput());
		return checksumCalculator.calculate(PostChecksumInput.from(metadata, candidate.rawBody()));
	}

	private static ParsedPostCandidate publishedCandidate(String rawBody) {
		return new ParsedPostCandidate(
				"blog/my-post/index.md",
				new PostMetadataInput(
						"A post",
						"my-post",
						"2026-07-03",
						"A useful post.",
						"blog",
						"published",
						List.of("java", "spring"),
						"2026-07-04",
						"./assets/thumbnail.png",
						"Houkago",
						true,
						"boj",
						"1000"),
				rawBody);
	}
}
