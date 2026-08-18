package com.houkago.server.content.post.readmodel;

import java.time.Instant;
import java.util.Objects;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import com.houkago.server.content.post.preparation.PreparedPostCandidate;

@Component
@Profile("!asset-sync")
public class PostReadModelCandidateProcessor {

	private final PostReadModelAssembler assembler;

	public PostReadModelCandidateProcessor(PostReadModelAssembler assembler) {
		this.assembler = Objects.requireNonNull(assembler, "assembler is required");
	}

	public PostReadModel create(PreparedPostCandidate candidate, String commitHash, Instant syncedAt) {
		String requiredCommitHash = requireText("commitHash", commitHash);
		Objects.requireNonNull(syncedAt, "syncedAt is required");

		return assembler.create(candidate, requiredCommitHash, syncedAt);
	}

	public PostReadModel update(
			PostReadModel existing,
			PreparedPostCandidate candidate,
			String commitHash,
			Instant syncedAt) {
		Objects.requireNonNull(existing, "existing post read model is required");
		String requiredCommitHash = requireText("commitHash", commitHash);
		Objects.requireNonNull(syncedAt, "syncedAt is required");

		return assembler.update(existing, candidate, requiredCommitHash, syncedAt);
	}

	public PostReadModel touch(PostReadModel existing, String commitHash, Instant syncedAt) {
		String requiredCommitHash = requireText("commitHash", commitHash);
		Objects.requireNonNull(syncedAt, "syncedAt is required");

		return assembler.touch(existing, requiredCommitHash, syncedAt);
	}

	private static String requireText(String field, String value) {
		if (value == null || value.isBlank()) {
			throw new IllegalArgumentException(field + " is required");
		}
		return value;
	}
}
