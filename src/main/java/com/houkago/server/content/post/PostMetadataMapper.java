package com.houkago.server.content.post;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.springframework.stereotype.Component;

@Component
public class PostMetadataMapper {

	private static final Set<String> ALLOWED_CATEGORIES = Set.of("algorithm", "project", "cs", "blog");
	private static final Set<String> ALLOWED_STATUSES = Set.of("draft", "published", "archived");
	private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE;

	public PostMetadataMapping map(PostMetadataInput input) {
		if (input == null) {
			throw new InvalidPostMetadataException("metadata input is required");
		}

		String title = requireText("title", input.title());
		String slug = requireText("slug", input.slug());
		LocalDate date = parseRequiredDate("date", input.date());
		String description = requireText("description", input.description());
		String category = requireAllowedCategory(input.category());
		String status = requireAllowedStatus(input.status());

		return new PostMetadataMapping(
				title,
				slug,
				date,
				description,
				category,
				mapSourceStatus(status),
				PostSyncStatus.ACTIVE,
				mapVisibility(status),
				normalizeTags(input.tags()),
				parseOptionalDate("updated", input.updated()),
				input.thumbnail(),
				input.series(),
				Boolean.TRUE.equals(input.featured()),
				input.platform(),
				input.problemId());
	}

	private static String requireText(String field, String value) {
		if (value == null) {
			throw new InvalidPostMetadataException(field + " is required");
		}

		String trimmed = value.trim();
		if (trimmed.isEmpty()) {
			throw new InvalidPostMetadataException(field + " must not be blank");
		}
		return trimmed;
	}

	private static String requireAllowedCategory(String value) {
		String category = requireText("category", value).toLowerCase(Locale.ROOT);
		if (!ALLOWED_CATEGORIES.contains(category)) {
			throw new InvalidPostMetadataException("category has unsupported value: " + value);
		}
		return category;
	}

	private static String requireAllowedStatus(String value) {
		String status = requireText("status", value).toLowerCase(Locale.ROOT);
		if (!ALLOWED_STATUSES.contains(status)) {
			throw new InvalidPostMetadataException("status has unsupported value: " + value);
		}
		return status;
	}

	private static LocalDate parseRequiredDate(String field, String value) {
		return parseDate(field, requireText(field, value));
	}

	private static LocalDate parseOptionalDate(String field, String value) {
		if (value == null || value.isBlank()) {
			return null;
		}
		return parseDate(field, value.trim());
	}

	private static LocalDate parseDate(String field, String value) {
		try {
			return LocalDate.parse(value, DATE_FORMATTER);
		} catch (DateTimeParseException exception) {
			throw new InvalidPostMetadataException(field + " must use YYYY-MM-DD format: " + value);
		}
	}

	private static List<String> normalizeTags(List<String> tags) {
		if (tags == null) {
			return List.of();
		}
		return List.copyOf(tags);
	}

	private static PostSourceStatus mapSourceStatus(String status) {
		return switch (status) {
			case "draft" -> PostSourceStatus.DRAFT;
			case "published" -> PostSourceStatus.PUBLISHED;
			case "archived" -> PostSourceStatus.ARCHIVED;
			default -> throw new InvalidPostMetadataException("status has unsupported value: " + status);
		};
	}

	private static PostVisibility mapVisibility(String status) {
		return "published".equals(status) ? PostVisibility.PUBLIC : PostVisibility.PRIVATE;
	}
}
