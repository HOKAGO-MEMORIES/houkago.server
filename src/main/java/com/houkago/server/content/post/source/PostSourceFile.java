package com.houkago.server.content.post.source;

import java.nio.file.Path;
import java.util.Objects;

public record PostSourceFile(Path absolutePath, String sourcePath) {

	public PostSourceFile {
		Objects.requireNonNull(absolutePath, "absolutePath is required");
		if (!absolutePath.isAbsolute()) {
			throw new IllegalArgumentException("absolutePath must be absolute: " + absolutePath);
		}
		if (sourcePath == null || sourcePath.isBlank()) {
			throw new IllegalArgumentException("sourcePath is required");
		}
	}
}
