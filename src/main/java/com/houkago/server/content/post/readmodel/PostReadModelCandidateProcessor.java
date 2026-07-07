package com.houkago.server.content.post.readmodel;

import java.time.Instant;
import java.util.Objects;

import org.springframework.stereotype.Component;

import com.houkago.server.content.post.checksum.PostChecksumCalculator;
import com.houkago.server.content.post.checksum.PostChecksumInput;
import com.houkago.server.content.post.metadata.PostMetadataMapper;
import com.houkago.server.content.post.metadata.PostMetadataMapping;
import com.houkago.server.content.post.source.ParsedPostCandidate;

@Component
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

	public PostReadModelPreparedCandidate prepare(ParsedPostCandidate candidate) {
		Objects.requireNonNull(candidate, "candidate is required");

		PostMetadataMapping metadata = metadataMapper.map(candidate.metadataInput());
		String checksum = checksumCalculator.calculate(PostChecksumInput.from(metadata, candidate.rawBody()));
		return new PostReadModelPreparedCandidate(
				metadata,
				candidate.rawBody(),
				candidate.sourcePath(),
				checksum);
	}

	public PostReadModel create(ParsedPostCandidate candidate, String commitHash, Instant syncedAt) {
		return create(prepare(candidate), commitHash, syncedAt);
	}

	public PostReadModel create(PostReadModelPreparedCandidate candidate, String commitHash, Instant syncedAt) {
		String requiredCommitHash = requireText("commitHash", commitHash);
		Objects.requireNonNull(syncedAt, "syncedAt is required");

		return assembler.create(candidate, requiredCommitHash, syncedAt);
	}

	public PostReadModel update(
			PostReadModel existing,
			ParsedPostCandidate candidate,
			String commitHash,
			Instant syncedAt) {
		return update(existing, prepare(candidate), commitHash, syncedAt);
	}

	public PostReadModel update(
			PostReadModel existing,
			PostReadModelPreparedCandidate candidate,
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
