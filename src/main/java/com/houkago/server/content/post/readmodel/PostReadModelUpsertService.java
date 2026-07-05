package com.houkago.server.content.post.readmodel;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

import org.springframework.transaction.annotation.Transactional;

import com.houkago.server.content.post.metadata.PostMetadataInput;
import com.houkago.server.content.post.source.ParsedPostCandidate;

public class PostReadModelUpsertService {

	private final PostReadModelRepository repository;
	private final PostReadModelCandidateProcessor processor;

	public PostReadModelUpsertService(
			PostReadModelRepository repository,
			PostReadModelCandidateProcessor processor) {
		this.repository = Objects.requireNonNull(repository, "repository is required");
		this.processor = Objects.requireNonNull(processor, "processor is required");
	}

	@Transactional
	public PostReadModelUpsertResult upsert(
			ParsedPostCandidate candidate,
			String commitHash,
			Instant syncedAt) {
		Objects.requireNonNull(candidate, "candidate is required");
		String requiredCommitHash = requireText("commitHash", commitHash);
		Objects.requireNonNull(syncedAt, "syncedAt is required");

		Optional<PostReadModel> rowBySourcePath = repository.findBySourcePath(candidate.sourcePath());
		Optional<PostReadModel> rowBySlug = findBySlug(candidate);
		Optional<PostReadModel> existing = selectExistingRow(candidate, rowBySourcePath, rowBySlug);

		PostReadModel post = existing
				.map(row -> processor.update(row, candidate, requiredCommitHash, syncedAt))
				.orElseGet(() -> processor.create(candidate, requiredCommitHash, syncedAt));
		PostReadModel saved = repository.save(post);

		PostReadModelUpsertStatus status = existing.isPresent()
				? PostReadModelUpsertStatus.UPDATED
				: PostReadModelUpsertStatus.CREATED;
		return new PostReadModelUpsertResult(saved, status);
	}

	private Optional<PostReadModel> findBySlug(ParsedPostCandidate candidate) {
		PostMetadataInput metadataInput = candidate.metadataInput();
		if (metadataInput == null || metadataInput.slug() == null || metadataInput.slug().isBlank()) {
			return Optional.empty();
		}
		return repository.findBySlug(metadataInput.slug());
	}

	private static Optional<PostReadModel> selectExistingRow(
			ParsedPostCandidate candidate,
			Optional<PostReadModel> rowBySourcePath,
			Optional<PostReadModel> rowBySlug) {
		if (rowBySourcePath.isPresent() && rowBySlug.isPresent()) {
			PostReadModel sourcePathRow = rowBySourcePath.get();
			PostReadModel slugRow = rowBySlug.get();
			if (!Objects.equals(sourcePathRow.getId(), slugRow.getId())) {
				throw new PostReadModelUpsertConflictException("Post read model upsert conflict for sourcePath="
						+ candidate.sourcePath()
						+ ", slug="
						+ candidate.metadataInput().slug()
						+ ", sourcePathRowId="
						+ sourcePathRow.getId()
						+ ", slugRowId="
						+ slugRow.getId());
			}
			return rowBySourcePath;
		}

		return rowBySourcePath.or(() -> rowBySlug);
	}

	private static String requireText(String field, String value) {
		if (value == null || value.isBlank()) {
			throw new IllegalArgumentException(field + " is required");
		}
		return value;
	}
}
