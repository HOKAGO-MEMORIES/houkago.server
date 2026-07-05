package com.houkago.server.content.post.sync;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

import com.houkago.server.content.post.readmodel.PostReadModelUpsertResult;
import com.houkago.server.content.post.readmodel.PostReadModelUpsertService;
import com.houkago.server.content.post.readmodel.PostReadModelUpsertStatus;
import com.houkago.server.content.post.source.ParsedPostCandidate;
import com.houkago.server.content.post.source.PostSourceCandidateLoader;

public class PostManualFullResyncService {

	private final PostSourceCandidateLoader candidateLoader;
	private final PostReadModelUpsertService upsertService;

	public PostManualFullResyncService(
			PostSourceCandidateLoader candidateLoader,
			PostReadModelUpsertService upsertService) {
		this.candidateLoader = Objects.requireNonNull(candidateLoader, "candidateLoader is required");
		this.upsertService = Objects.requireNonNull(upsertService, "upsertService is required");
	}

	public PostManualFullResyncResult resync(Path postsRoot, String commitHash, Instant syncedAt) {
		Objects.requireNonNull(postsRoot, "postsRoot is required");
		String requiredCommitHash = requireText("commitHash", commitHash);
		Objects.requireNonNull(syncedAt, "syncedAt is required");

		List<ParsedPostCandidate> candidates = candidateLoader.load(postsRoot);
		int createdCount = 0;
		int updatedCount = 0;

		for (ParsedPostCandidate candidate : candidates) {
			PostReadModelUpsertResult result = upsertService.upsert(candidate, requiredCommitHash, syncedAt);
			if (result.status() == PostReadModelUpsertStatus.CREATED) {
				createdCount++;
			} else if (result.status() == PostReadModelUpsertStatus.UPDATED) {
				updatedCount++;
			}
		}

		int totalUpsertedCount = createdCount + updatedCount;
		return new PostManualFullResyncResult(
				candidates.size(),
				createdCount,
				updatedCount,
				totalUpsertedCount,
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
