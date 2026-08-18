package com.houkago.server.content.post.preparation;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.houkago.server.content.post.source.ParsedPostCandidate;

@Component
public class PostCandidatePreflight {

	private final PostCandidatePreparer preparer;

	public PostCandidatePreflight(PostCandidatePreparer preparer) {
		this.preparer = Objects.requireNonNull(preparer, "preparer is required");
	}

	public List<PreparedPostCandidate> prepareAll(List<ParsedPostCandidate> candidates) {
		Objects.requireNonNull(candidates, "candidates are required");

		List<PreparedPostCandidate> preparedCandidates = new ArrayList<>(candidates.size());
		Set<String> sourcePaths = new HashSet<>();
		Set<String> slugs = new HashSet<>();

		for (ParsedPostCandidate candidate : candidates) {
			PreparedPostCandidate preparedCandidate = preparer.prepare(candidate);
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
