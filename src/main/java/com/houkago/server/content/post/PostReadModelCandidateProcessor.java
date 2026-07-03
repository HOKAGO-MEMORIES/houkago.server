package com.houkago.server.content.post;

import java.time.Instant;
import java.util.Objects;

public class PostReadModelCandidateProcessor {

	private final PostMetadataMapper metadataMapper;
	private final PostChecksumCalculator checksumCalculator;
	private final PostReadModelAssembler assembler;

	public PostReadModelCandidateProcessor(
			PostMetadataMapper metadataMapper,
			PostChecksumCalculator checksumCalculator,
			PostReadModelAssembler assembler) {
		this.metadataMapper = Objects.requireNonNull(metadataMapper, "metadataMapper is required");
		this.checksumCalculator = Objects.requireNonNull(checksumCalculator, "checksumCalculator is required");
		this.assembler = Objects.requireNonNull(assembler, "assembler is required");
	}

	public PostReadModel create(ParsedPostCandidate candidate, String commitHash, Instant syncedAt) {
		Objects.requireNonNull(candidate, "candidate is required");
		String requiredCommitHash = requireText("commitHash", commitHash);
		Objects.requireNonNull(syncedAt, "syncedAt is required");

		PostMetadataMapping metadata = metadataMapper.map(candidate.metadataInput());
		String checksum = checksumCalculator.calculate(PostChecksumInput.from(metadata, candidate.rawBody()));
		return assembler.create(
				metadata,
				candidate.rawBody(),
				candidate.sourcePath(),
				requiredCommitHash,
				checksum,
				syncedAt);
	}

	public PostReadModel update(
			PostReadModel existing,
			ParsedPostCandidate candidate,
			String commitHash,
			Instant syncedAt) {
		Objects.requireNonNull(existing, "existing post read model is required");
		Objects.requireNonNull(candidate, "candidate is required");
		String requiredCommitHash = requireText("commitHash", commitHash);
		Objects.requireNonNull(syncedAt, "syncedAt is required");

		PostMetadataMapping metadata = metadataMapper.map(candidate.metadataInput());
		String checksum = checksumCalculator.calculate(PostChecksumInput.from(metadata, candidate.rawBody()));
		return assembler.update(
				existing,
				metadata,
				candidate.rawBody(),
				candidate.sourcePath(),
				requiredCommitHash,
				checksum,
				syncedAt);
	}

	private static String requireText(String field, String value) {
		if (value == null || value.isBlank()) {
			throw new IllegalArgumentException(field + " is required");
		}
		return value;
	}
}
