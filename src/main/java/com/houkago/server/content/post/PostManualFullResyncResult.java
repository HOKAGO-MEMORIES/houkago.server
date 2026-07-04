package com.houkago.server.content.post;

import java.time.Instant;
import java.util.Objects;

public record PostManualFullResyncResult(
		int candidateCount,
		int createdCount,
		int updatedCount,
		int totalUpsertedCount,
		String commitHash,
		Instant syncedAt) {

	public PostManualFullResyncResult {
		if (candidateCount < 0) {
			throw new IllegalArgumentException("candidateCount must not be negative");
		}
		if (createdCount < 0) {
			throw new IllegalArgumentException("createdCount must not be negative");
		}
		if (updatedCount < 0) {
			throw new IllegalArgumentException("updatedCount must not be negative");
		}
		if (totalUpsertedCount < 0) {
			throw new IllegalArgumentException("totalUpsertedCount must not be negative");
		}
		if (commitHash == null || commitHash.isBlank()) {
			throw new IllegalArgumentException("commitHash is required");
		}
		Objects.requireNonNull(syncedAt, "syncedAt is required");
	}
}
