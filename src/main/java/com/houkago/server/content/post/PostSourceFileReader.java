package com.houkago.server.content.post;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Objects;

public class PostSourceFileReader {

	private final PostMarkdownParser parser;

	public PostSourceFileReader(PostMarkdownParser parser) {
		this.parser = Objects.requireNonNull(parser, "parser is required");
	}

	public ParsedPostCandidate read(PostSourceFile sourceFile) {
		Objects.requireNonNull(sourceFile, "sourceFile is required");

		try {
			String markdownSource = Files.readString(sourceFile.absolutePath(), StandardCharsets.UTF_8);
			return parser.parse(sourceFile.sourcePath(), markdownSource);
		} catch (IOException exception) {
			throw new PostSourceReadException("Failed to read post source file "
					+ sourceFile.absolutePath() + " (" + sourceFile.sourcePath() + "): " + exception.getMessage(),
					exception);
		}
	}
}
