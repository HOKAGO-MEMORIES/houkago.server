package com.houkago.server.content.post.asset;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.stream.Collectors;

public final class PostPublicAssetPath {

	public static final String PUBLIC_PATH_PREFIX = "/assets/posts";

	private PostPublicAssetPath() {
	}

	public static String basePath(String slug) {
		return PUBLIC_PATH_PREFIX + "/" + requirePathSegment("slug", slug) + "/";
	}

	public static String assetPath(String slug, String assetRelativePath) {
		String normalizedAssetPath = requireRelativeAssetPath(assetRelativePath);
		return basePath(slug) + normalizedAssetPath;
	}

	public static String encodedAssetPath(String slug, String assetRelativePath) {
		String requiredSlug = requirePathSegment("slug", slug);
		String normalizedAssetPath = requireRelativeAssetPath(assetRelativePath);
		return PUBLIC_PATH_PREFIX + "/" + encodeSegment(requiredSlug) + "/"
				+ Arrays.stream(normalizedAssetPath.split("/"))
						.map(PostPublicAssetPath::encodeSegment)
						.collect(Collectors.joining("/"));
	}

	static String requirePathSegment(String field, String value) {
		if (value == null || value.isBlank()) {
			throw new IllegalArgumentException(field + " is required");
		}
		if (value.contains("/") || value.contains("\\") || ".".equals(value) || "..".equals(value)) {
			throw new IllegalArgumentException(field + " must be one safe path segment: " + value);
		}
		return value;
	}

	private static String requireRelativeAssetPath(String value) {
		if (value == null || value.isBlank()) {
			throw new IllegalArgumentException("assetRelativePath is required");
		}

		String normalizedValue = value.replace('\\', '/');
		try {
			Path path = Path.of(normalizedValue);
			if (path.isAbsolute()) {
				throw new IllegalArgumentException("assetRelativePath must be relative: " + value);
			}
			for (Path segment : path) {
				String segmentValue = segment.toString();
				if (".".equals(segmentValue) || "..".equals(segmentValue)) {
					throw new IllegalArgumentException("assetRelativePath must not traverse directories: " + value);
				}
			}
		} catch (InvalidPathException exception) {
			throw new IllegalArgumentException("assetRelativePath is invalid: " + value, exception);
		}
		return normalizedValue;
	}

	private static String encodeSegment(String value) {
		return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
	}
}
