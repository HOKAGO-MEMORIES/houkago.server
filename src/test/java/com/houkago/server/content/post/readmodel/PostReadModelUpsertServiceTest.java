package com.houkago.server.content.post.readmodel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.houkago.server.content.post.metadata.PostMetadataInput;
import com.houkago.server.content.post.metadata.PostMetadataMapping;
import com.houkago.server.content.post.policy.PostSourceStatus;
import com.houkago.server.content.post.policy.PostSyncStatus;
import com.houkago.server.content.post.policy.PostVisibility;
import com.houkago.server.content.post.source.ParsedPostCandidate;

class PostReadModelUpsertServiceTest {

	private static final String COMMIT_HASH = "commit-touch";
	private static final Instant SYNCED_AT = Instant.parse("2026-07-04T00:00:00Z");

	private final PostReadModelRepository repository = mock(PostReadModelRepository.class);
	private final PostReadModelCandidateProcessor processor = mock(PostReadModelCandidateProcessor.class);
	private final PostReadModelUpsertService service = new PostReadModelUpsertService(repository, processor);

	@Test
	void sameChecksumTouchesExistingRowWithoutFullUpdate() {
		ParsedPostCandidate candidate = candidate();
		PostReadModel existing = existingPost("checksum-a");
		PostReadModelPreparedCandidate preparedCandidate = preparedCandidate("checksum-a");
		when(repository.findBySourcePath(candidate.sourcePath())).thenReturn(Optional.of(existing));
		when(repository.findBySlug(candidate.metadataInput().slug())).thenReturn(Optional.of(existing));
		when(processor.prepare(candidate)).thenReturn(preparedCandidate);
		when(processor.touch(existing, COMMIT_HASH, SYNCED_AT)).thenReturn(existing);
		when(repository.save(existing)).thenReturn(existing);

		PostReadModelUpsertResult result = service.upsert(candidate, COMMIT_HASH, SYNCED_AT);

		assertThat(result.status()).isEqualTo(PostReadModelUpsertStatus.TOUCHED);
		verify(processor).prepare(candidate);
		verify(processor).touch(existing, COMMIT_HASH, SYNCED_AT);
		verify(processor, never()).update(existing, preparedCandidate, COMMIT_HASH, SYNCED_AT);
		verify(processor, never()).create(preparedCandidate, COMMIT_HASH, SYNCED_AT);
		verify(repository).save(existing);
	}

	private static ParsedPostCandidate candidate() {
		return new ParsedPostCandidate(
				"blog/touched-post/index.md",
				new PostMetadataInput(
						"Post touched-post",
						"touched-post",
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

	private static PostReadModel existingPost(String checksum) {
		PostReadModel post = new PostReadModel();
		post.setSlug("touched-post");
		post.setSourcePath("blog/touched-post/index.md");
		post.setChecksum(checksum);
		return post;
	}

	private static PostReadModelPreparedCandidate preparedCandidate(String checksum) {
		return new PostReadModelPreparedCandidate(
				new PostMetadataMapping(
						"Post touched-post",
						"touched-post",
						LocalDate.of(2026, 7, 4),
						"A useful post.",
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
				"blog/touched-post/index.md",
				checksum);
	}
}
