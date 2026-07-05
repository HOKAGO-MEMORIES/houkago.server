package com.houkago.server.content.post.source;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

public class PostSourceScanner {

	private static final String POST_ENTRY_FILE_NAME = "index.md";
	private static final Set<String> SKIPPED_DIRECTORY_NAMES = Set.of(
			".git",
			".obsidian",
			".trash",
			".cache",
			".next",
			"node_modules",
			"dist",
			"build",
			"assets");

	public List<PostSourceFile> scan(Path postsRoot) {
		Path root = validateRoot(postsRoot);
		List<PostSourceFile> sourceFiles = new ArrayList<>();

		try {
			Files.walkFileTree(root, new SimpleFileVisitor<>() {
				@Override
				public FileVisitResult preVisitDirectory(Path directory, BasicFileAttributes attributes) {
					if (!directory.equals(root) && shouldSkipDirectory(directory)) {
						return FileVisitResult.SKIP_SUBTREE;
					}
					return FileVisitResult.CONTINUE;
				}

				@Override
				public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) {
					if (attributes.isRegularFile() && isPostEntryFile(file)) {
						sourceFiles.add(toSourceFile(root, file));
					}
					return FileVisitResult.CONTINUE;
				}
			});
		} catch (IOException exception) {
			throw new PostSourceScanException("Failed to scan posts root " + postsRoot + ": " + exception.getMessage(),
					exception);
		}

		return sourceFiles.stream()
				.sorted(Comparator.comparing(PostSourceFile::sourcePath))
				.toList();
	}

	private static Path validateRoot(Path postsRoot) {
		if (postsRoot == null) {
			throw new IllegalArgumentException("postsRoot must not be null");
		}
		if (!Files.exists(postsRoot)) {
			throw new IllegalArgumentException("postsRoot does not exist: " + postsRoot);
		}
		if (!Files.isDirectory(postsRoot)) {
			throw new IllegalArgumentException("postsRoot must be a directory: " + postsRoot);
		}
		return postsRoot.toAbsolutePath().normalize();
	}

	private static boolean shouldSkipDirectory(Path directory) {
		String directoryName = directory.getFileName().toString();
		return SKIPPED_DIRECTORY_NAMES.contains(directoryName)
				|| (directoryName.startsWith(".") && !directoryName.equals("."));
	}

	private static boolean isPostEntryFile(Path file) {
		return POST_ENTRY_FILE_NAME.equals(file.getFileName().toString());
	}

	private static PostSourceFile toSourceFile(Path root, Path file) {
		Path absolutePath = file.toAbsolutePath().normalize();
		String sourcePath = root.relativize(absolutePath)
				.toString()
				.replace('\\', '/');
		return new PostSourceFile(absolutePath, sourcePath);
	}
}
