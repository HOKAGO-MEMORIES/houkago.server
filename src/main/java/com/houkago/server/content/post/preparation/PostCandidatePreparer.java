package com.houkago.server.content.post.preparation;

import java.util.Objects;

import org.springframework.stereotype.Component;

import com.houkago.server.content.post.checksum.PostChecksumCalculator;
import com.houkago.server.content.post.checksum.PostChecksumInput;
import com.houkago.server.content.post.metadata.PostMetadataMapper;
import com.houkago.server.content.post.metadata.PostMetadataMapping;
import com.houkago.server.content.post.source.ParsedPostCandidate;
import com.houkago.server.content.post.source.PostSourceLayoutValidator;

@Component
public class PostCandidatePreparer {

	private final PostMetadataMapper metadataMapper;
	private final PostSourceLayoutValidator sourceLayoutValidator;
	private final PostChecksumCalculator checksumCalculator;

	public PostCandidatePreparer(
			PostMetadataMapper metadataMapper,
			PostSourceLayoutValidator sourceLayoutValidator,
			PostChecksumCalculator checksumCalculator) {
		this.metadataMapper = Objects.requireNonNull(metadataMapper, "metadataMapper is required");
		this.sourceLayoutValidator = Objects.requireNonNull(sourceLayoutValidator, "sourceLayoutValidator is required");
		this.checksumCalculator = Objects.requireNonNull(checksumCalculator, "checksumCalculator is required");
	}

	public PreparedPostCandidate prepare(ParsedPostCandidate candidate) {
		Objects.requireNonNull(candidate, "candidate is required");
		PostMetadataMapping metadata = metadataMapper.map(candidate.metadataInput());
		sourceLayoutValidator.validate(candidate.sourcePath(), metadata);
		String checksum = checksumCalculator.calculate(PostChecksumInput.from(metadata, candidate.rawBody()));
		return new PreparedPostCandidate(
				metadata,
				candidate.rawBody(),
				candidate.sourcePath(),
				checksum);
	}
}
