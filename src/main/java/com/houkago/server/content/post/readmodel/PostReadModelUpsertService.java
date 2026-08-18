package com.houkago.server.content.post.readmodel;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

import org.springframework.transaction.annotation.Transactional;

import com.houkago.server.content.post.preparation.PostCandidatePreparer;
import com.houkago.server.content.post.preparation.PreparedPostCandidate;
import com.houkago.server.content.post.source.ParsedPostCandidate;

public class PostReadModelUpsertService {

	private final PostReadModelRepository repository;
	private final PostCandidatePreparer preparer;
	private final PostReadModelCandidateProcessor processor;

	public PostReadModelUpsertService(
			PostReadModelRepository repository,
			PostCandidatePreparer preparer,
			PostReadModelCandidateProcessor processor) {
		this.repository = Objects.requireNonNull(repository, "repository is required");
		this.preparer = Objects.requireNonNull(preparer, "preparer is required");
		this.processor = Objects.requireNonNull(processor, "processor is required");
	}

	@Transactional
	public PostReadModelUpsertResult upsert(
			ParsedPostCandidate candidate,
			String commitHash,
			Instant syncedAt) {
		Objects.requireNonNull(candidate, "candidate is required");
		return upsertPreparedCandidate(preparer.prepare(candidate), commitHash, syncedAt);
	}

	@Transactional
	public PostReadModelUpsertResult upsertPreparedCandidate(
			PreparedPostCandidate candidate,
			String commitHash,
			Instant syncedAt) {
		Objects.requireNonNull(candidate, "prepared candidate is required");
		String requiredCommitHash = requireText("commitHash", commitHash);
		Objects.requireNonNull(syncedAt, "syncedAt is required");

		Optional<PostReadModel> rowBySourcePath = repository.findBySourcePath(candidate.sourcePath());
		Optional<PostReadModel> rowBySlug = repository.findBySlug(candidate.metadata().slug());
		Optional<PostReadModel> existing = selectExistingRow(candidate, rowBySourcePath, rowBySlug);

		PostReadModelUpsertStatus status = determineStatus(existing, candidate);
		PostReadModel post = switch (status) {
			case CREATED -> processor.create(candidate, requiredCommitHash, syncedAt);
			case UPDATED -> processor.update(existing.orElseThrow(), candidate, requiredCommitHash, syncedAt);
			case TOUCHED -> processor.touch(existing.orElseThrow(), requiredCommitHash, syncedAt);
		};

		return new PostReadModelUpsertResult(repository.save(post), status);
	}

	private static Optional<PostReadModel> selectExistingRow(
			PreparedPostCandidate candidate,
			Optional<PostReadModel> rowBySourcePath,
			Optional<PostReadModel> rowBySlug) {
		if (rowBySourcePath.isPresent() && rowBySlug.isPresent()) {
			PostReadModel sourcePathRow = rowBySourcePath.get();
			PostReadModel slugRow = rowBySlug.get();
			if (!Objects.equals(sourcePathRow.getId(), slugRow.getId())) {
				throw new PostReadModelUpsertConflictException("Post read model upsert conflict for sourcePath="
						+ candidate.sourcePath()
						+ ", slug="
						+ candidate.metadata().slug()
						+ ", sourcePathRowId="
						+ sourcePathRow.getId()
						+ ", slugRowId="
						+ slugRow.getId());
			}
			return rowBySourcePath;
		}

		return rowBySourcePath.or(() -> rowBySlug);
	}

	private static PostReadModelUpsertStatus determineStatus(
			Optional<PostReadModel> existing,
			PreparedPostCandidate candidate) {
		if (existing.isEmpty()) {
			return PostReadModelUpsertStatus.CREATED;
		}
		return requiresReadModelUpdate(existing.get(), candidate)
				? PostReadModelUpsertStatus.UPDATED
				: PostReadModelUpsertStatus.TOUCHED;
	}

	private static boolean requiresReadModelUpdate(
			PostReadModel existing,
			PreparedPostCandidate candidate) {
		return !Objects.equals(existing.getChecksum(), candidate.checksum())
				|| !Objects.equals(existing.getSourcePath(), candidate.sourcePath())
				|| existing.getSourceStatus() != candidate.metadata().sourceStatus()
				|| existing.getSyncStatus() != candidate.metadata().syncStatus()
				|| existing.getVisibility() != candidate.metadata().visibility();
	}

	private static String requireText(String field, String value) {
		if (value == null || value.isBlank()) {
			throw new IllegalArgumentException(field + " is required");
		}
		return value;
	}
}
