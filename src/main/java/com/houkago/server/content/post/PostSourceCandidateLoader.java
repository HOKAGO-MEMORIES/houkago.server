package com.houkago.server.content.post;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

public class PostSourceCandidateLoader {

	private final PostSourceScanner scanner;
	private final PostSourceFileReader reader;

	public PostSourceCandidateLoader(PostSourceScanner scanner, PostSourceFileReader reader) {
		this.scanner = Objects.requireNonNull(scanner, "scanner is required");
		this.reader = Objects.requireNonNull(reader, "reader is required");
	}

	public List<ParsedPostCandidate> load(Path postsRoot) {
		List<PostSourceFile> sourceFiles = scanner.scan(postsRoot).stream()
				.sorted(Comparator.comparing(PostSourceFile::sourcePath))
				.toList();
		List<ParsedPostCandidate> candidates = new ArrayList<>(sourceFiles.size());

		for (PostSourceFile sourceFile : sourceFiles) {
			candidates.add(reader.read(sourceFile));
		}

		return List.copyOf(candidates);
	}
}
