package com.houkago.server.content.post;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;

public class PostMarkdownParser {

	private static final String FRONTMATTER_DELIMITER = "---";

	private final YAMLMapper yamlMapper;

	public PostMarkdownParser() {
		this(YAMLMapper.builder()
				.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
				.build());
	}

	PostMarkdownParser(YAMLMapper yamlMapper) {
		this.yamlMapper = yamlMapper;
	}

	public ParsedPostCandidate parse(String sourcePath, String markdownSource) {
		FrontmatterParts parts = splitFrontmatter(sourcePath, markdownSource);
		PostMetadataInput metadataInput = parseMetadata(sourcePath, parts.frontmatter());
		return new ParsedPostCandidate(sourcePath, metadataInput, parts.rawBody());
	}

	private PostMetadataInput parseMetadata(String sourcePath, String frontmatter) {
		try {
			PostMetadataInput metadataInput = yamlMapper.readValue(frontmatter, PostMetadataInput.class);
			if (metadataInput == null) {
				throw new PostParseException("Failed to parse post frontmatter for " + sourcePath
						+ ": frontmatter did not produce metadata");
			}
			return metadataInput;
		} catch (JsonProcessingException exception) {
			throw new PostParseException("Failed to parse post frontmatter for " + sourcePath
					+ ": invalid YAML frontmatter", exception);
		}
	}

	private static FrontmatterParts splitFrontmatter(String sourcePath, String markdownSource) {
		if (markdownSource == null) {
			throw new PostParseException("Failed to parse post frontmatter for " + sourcePath
					+ ": markdown source must not be null");
		}

		Line openingLine = readLine(markdownSource, 0);
		if (!FRONTMATTER_DELIMITER.equals(openingLine.content())) {
			throw new PostParseException("Failed to parse post frontmatter for " + sourcePath
					+ ": opening --- delimiter is required");
		}

		int frontmatterStart = openingLine.nextIndex();
		int lineStart = frontmatterStart;
		while (lineStart < markdownSource.length()) {
			Line line = readLine(markdownSource, lineStart);
			if (FRONTMATTER_DELIMITER.equals(line.content())) {
				String frontmatter = markdownSource.substring(frontmatterStart, line.startIndex());
				String rawBody = markdownSource.substring(line.nextIndex());
				return new FrontmatterParts(frontmatter, rawBody);
			}
			lineStart = line.nextIndex();
		}

		throw new PostParseException("Failed to parse post frontmatter for " + sourcePath
				+ ": closing --- delimiter is required");
	}

	private static Line readLine(String value, int startIndex) {
		int index = startIndex;
		while (index < value.length()) {
			char character = value.charAt(index);
			if (character == '\n' || character == '\r') {
				int nextIndex = index + 1;
				if (character == '\r' && nextIndex < value.length() && value.charAt(nextIndex) == '\n') {
					nextIndex++;
				}
				return new Line(startIndex, value.substring(startIndex, index), nextIndex);
			}
			index++;
		}
		return new Line(startIndex, value.substring(startIndex), value.length());
	}

	private record FrontmatterParts(String frontmatter, String rawBody) {
	}

	private record Line(int startIndex, String content, int nextIndex) {
	}
}
