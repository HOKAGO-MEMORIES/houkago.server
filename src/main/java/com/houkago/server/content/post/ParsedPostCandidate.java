package com.houkago.server.content.post;

public record ParsedPostCandidate(
		String sourcePath,
		PostMetadataInput metadataInput,
		String rawBody) {

	public ParsedPostCandidate {
		sourcePath = normalizeSourcePath(sourcePath);
		if (metadataInput == null) {
			throw new IllegalArgumentException("metadataInput must not be null");
		}
		if (rawBody == null) {
			throw new IllegalArgumentException("rawBody must not be null");
		}
	}

	private static String normalizeSourcePath(String sourcePath) {
		if (sourcePath == null || sourcePath.isBlank()) {
			throw new IllegalArgumentException("sourcePath must not be null or blank");
		}

		String normalizedPath = sourcePath.replace('\\', '/');
		if (normalizedPath.startsWith("/")) {
			throw new IllegalArgumentException("sourcePath must be relative: " + sourcePath);
		}
		if (isWindowsDriveAbsolutePath(normalizedPath)) {
			throw new IllegalArgumentException("sourcePath must not be a Windows drive absolute path: " + sourcePath);
		}

		String[] segments = normalizedPath.split("/", -1);
		for (String segment : segments) {
			if (".".equals(segment) || "..".equals(segment)) {
				throw new IllegalArgumentException("sourcePath must not contain dot or traversal segments: " + sourcePath);
			}
			if (segment.isEmpty()) {
				throw new IllegalArgumentException("sourcePath must not contain empty path segments: " + sourcePath);
			}
		}

		if (!"index.md".equals(normalizedPath) && !normalizedPath.endsWith("/index.md")) {
			throw new IllegalArgumentException("sourcePath must point to an index.md post entry file: " + sourcePath);
		}

		return normalizedPath;
	}

	private static boolean isWindowsDriveAbsolutePath(String sourcePath) {
		return sourcePath.length() >= 3
				&& Character.isLetter(sourcePath.charAt(0))
				&& sourcePath.charAt(1) == ':'
				&& sourcePath.charAt(2) == '/';
	}
}
