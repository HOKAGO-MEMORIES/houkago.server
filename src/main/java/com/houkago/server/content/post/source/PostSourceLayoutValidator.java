package com.houkago.server.content.post.source;

import java.util.Objects;

import org.springframework.stereotype.Component;

import com.houkago.server.content.post.metadata.PostMetadataMapping;

@Component
public class PostSourceLayoutValidator {

	private static final String ENTRY_FILE_NAME = "index.md";
	private static final String ALGORITHM_CATEGORY = "algorithm";
	private static final String ALGORITHM_LAYOUT = "algorithm/{platform}/{problemId}/index.md";
	private static final String FLAT_LAYOUT = "{category}/{slug}/index.md";

	public void validate(String sourcePath, PostMetadataMapping metadata) {
		Objects.requireNonNull(sourcePath, "sourcePath is required");
		Objects.requireNonNull(metadata, "metadata is required");

		String[] segments = sourcePath.split("/", -1);
		if (segments.length == 0 || !sameValue(segments[0], metadata.category())) {
			throw invalid(
					sourcePath,
					"category_path_match",
					segments.length == 0 ? "<missing>" : segments[0],
					"top-level directory matching category '" + metadata.category() + "'");
		}

		if (ALGORITHM_CATEGORY.equals(metadata.category())) {
			validateAlgorithmLayout(sourcePath, segments, metadata);
			return;
		}

		validateFlatLayout(sourcePath, segments, metadata);
	}

	private static void validateFlatLayout(
			String sourcePath,
			String[] segments,
			PostMetadataMapping metadata) {
		requireLayout(sourcePath, segments, 3, FLAT_LAYOUT);
		if (!sameValue(segments[1], metadata.slug())) {
			throw invalid(
					sourcePath,
					"flat_slug_directory_match",
					segments[1],
					"directory matching slug '" + metadata.slug() + "'");
		}
	}

	private static void validateAlgorithmLayout(
			String sourcePath,
			String[] segments,
			PostMetadataMapping metadata) {
		requireLayout(sourcePath, segments, 4, ALGORITHM_LAYOUT);

		String platform = segments[1];
		String problemId = segments[2];
		String expectedSlug = platform + "-" + problemId;
		if (!sameValue(metadata.slug(), expectedSlug)) {
			throw invalid(
					sourcePath,
					"algorithm_slug_path_match",
					metadata.slug(),
					"slug '" + expectedSlug + "' derived from platform/problemId path");
		}

		validateOptionalAlgorithmMetadata(
				sourcePath,
				"algorithm_platform_path_match",
				metadata.platform(),
				platform,
				"platform");
		validateOptionalAlgorithmMetadata(
				sourcePath,
				"algorithm_problem_id_path_match",
				metadata.problemId(),
				problemId,
				"problemId");
	}

	private static void requireLayout(String sourcePath, String[] segments, int expectedDepth, String expectedLayout) {
		if (segments.length != expectedDepth || !ENTRY_FILE_NAME.equals(segments[segments.length - 1])) {
			throw invalid(
					sourcePath,
					"source_path_layout",
					sourcePath,
					expectedLayout);
		}
	}

	private static void validateOptionalAlgorithmMetadata(
			String sourcePath,
			String invariant,
			String actualValue,
			String expectedValue,
			String field) {
		if (actualValue != null && !sameValue(actualValue, expectedValue)) {
			throw invalid(
					sourcePath,
					invariant,
					actualValue,
					field + " matching path segment '" + expectedValue + "'");
		}
	}

	private static boolean sameValue(String left, String right) {
		return left != null && right != null && left.equalsIgnoreCase(right);
	}

	private static InvalidPostSourceLayoutException invalid(
			String sourcePath,
			String invariant,
			String actual,
			String expected) {
		return new InvalidPostSourceLayoutException(
				"Invalid post source layout: sourcePath=" + sourcePath
						+ ", invariant=" + invariant
						+ ", actual=" + actual
						+ ", expected=" + expected);
	}
}
