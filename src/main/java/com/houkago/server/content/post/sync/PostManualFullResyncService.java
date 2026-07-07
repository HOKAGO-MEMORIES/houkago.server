package com.houkago.server.content.post.sync;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import com.houkago.server.content.post.readmodel.PostReadModelRetirementService;
import com.houkago.server.content.post.readmodel.PostReadModelUpsertResult;
import com.houkago.server.content.post.readmodel.PostReadModelUpsertService;
import com.houkago.server.content.post.readmodel.PostReadModelUpsertStatus;
import com.houkago.server.content.post.source.ParsedPostCandidate;
import com.houkago.server.content.post.source.PostSourceCandidateLoader;

public class PostManualFullResyncService {

	private final PostSourceCandidateLoader candidateLoader;
	private final PostReadModelUpsertService upsertService;
	private final PostReadModelRetirementService retirementService;

	public PostManualFullResyncService(
			PostSourceCandidateLoader candidateLoader,
			PostReadModelUpsertService upsertService,
			PostReadModelRetirementService retirementService) {
		this.candidateLoader = Objects.requireNonNull(candidateLoader, "candidateLoader is required");
		this.upsertService = Objects.requireNonNull(upsertService, "upsertService is required");
		this.retirementService = Objects.requireNonNull(retirementService, "retirementService is required");
	}

	public PostManualFullResyncResult resync(Path postsRoot, String commitHash, Instant syncedAt) {
		Objects.requireNonNull(postsRoot, "postsRoot is required");
		String requiredCommitHash = requireText("commitHash", commitHash);
		Objects.requireNonNull(syncedAt, "syncedAt is required");

		List<ParsedPostCandidate> candidates = candidateLoader.load(postsRoot);
		int createdCount = 0;
		int updatedCount = 0;
		int touchedCount = 0;

		for (ParsedPostCandidate candidate : candidates) {
			PostReadModelUpsertResult result = upsertService.upsert(candidate, requiredCommitHash, syncedAt);
			if (result.status() == PostReadModelUpsertStatus.CREATED) {
				createdCount++;
			} else if (result.status() == PostReadModelUpsertStatus.UPDATED) {
				updatedCount++;
			} else if (result.status() == PostReadModelUpsertStatus.TOUCHED) {
				touchedCount++;
			}
		}

		Set<String> currentSourcePaths = candidates.stream()
				.map(ParsedPostCandidate::sourcePath)
				.collect(java.util.stream.Collectors.toUnmodifiableSet());
		int deletedCount = currentSourcePaths.isEmpty()
				? 0
				: retirementService.retireMissingSources(currentSourcePaths, requiredCommitHash, syncedAt);
		int totalUpsertedCount = createdCount + updatedCount + touchedCount;
		return new PostManualFullResyncResult(
				candidates.size(),
				createdCount,
				updatedCount,
				touchedCount,
				totalUpsertedCount,
				deletedCount,
				requiredCommitHash,
				syncedAt);
	}

	private static String requireText(String field, String value) {
		if (value == null || value.isBlank()) {
			throw new IllegalArgumentException(field + " is required");
		}
		return value;
	}
}
