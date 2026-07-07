package com.houkago.server.content.post.readmodel;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import org.springframework.transaction.annotation.Transactional;

import com.houkago.server.content.post.policy.PostSyncStatus;

public class PostReadModelRetirementService {

	private final PostReadModelRepository repository;
	private final PostReadModelAssembler assembler;

	public PostReadModelRetirementService(
			PostReadModelRepository repository,
			PostReadModelAssembler assembler) {
		this.repository = Objects.requireNonNull(repository, "repository is required");
		this.assembler = Objects.requireNonNull(assembler, "assembler is required");
	}

	@Transactional
	public int retireMissingSources(Set<String> currentSourcePaths, String commitHash, Instant syncedAt) {
		Objects.requireNonNull(currentSourcePaths, "currentSourcePaths is required");
		String requiredCommitHash = requireText("commitHash", commitHash);
		Objects.requireNonNull(syncedAt, "syncedAt is required");

		if (currentSourcePaths.isEmpty()) {
			return 0;
		}

		Set<String> sourcePaths = Set.copyOf(currentSourcePaths);
		sourcePaths.forEach(sourcePath -> requireText("sourcePath", sourcePath));
		List<PostReadModel> missingRows = repository.findBySyncStatusAndSourcePathNotIn(
				PostSyncStatus.ACTIVE,
				sourcePaths);

		for (PostReadModel missingRow : missingRows) {
			repository.save(assembler.markDeleted(missingRow, requiredCommitHash, syncedAt));
		}
		return missingRows.size();
	}

	private static String requireText(String field, String value) {
		if (value == null || value.isBlank()) {
			throw new IllegalArgumentException(field + " is required");
		}
		return value;
	}
}
