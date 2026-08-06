package com.houkago.server.content.post.readmodel;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.houkago.server.content.post.source.ParsedPostCandidate;

@Component
public class PostReadModelCandidatePreflight {

	private final PostReadModelCandidateProcessor processor;

	public PostReadModelCandidatePreflight(PostReadModelCandidateProcessor processor) {
		this.processor = Objects.requireNonNull(processor, "processor is required");
	}

	public List<PostReadModelPreparedCandidate> prepareAll(List<ParsedPostCandidate> candidates) {
		Objects.requireNonNull(candidates, "candidates are required");

		List<PostReadModelPreparedCandidate> preparedCandidates = new ArrayList<>(candidates.size());
		Set<String> sourcePaths = new HashSet<>();
		Set<String> slugs = new HashSet<>();

		for (ParsedPostCandidate candidate : candidates) {
			PostReadModelPreparedCandidate preparedCandidate = processor.prepare(candidate);
			requireUnique("sourcePath", preparedCandidate.sourcePath(), sourcePaths);
			requireUnique("slug", preparedCandidate.metadata().slug(), slugs);
			preparedCandidates.add(preparedCandidate);
		}

		return List.copyOf(preparedCandidates);
	}

	private static void requireUnique(String field, String value, Set<String> values) {
		if (!values.add(value)) {
			throw new IllegalArgumentException("Duplicate post candidate " + field + ": " + value);
		}
	}
}
