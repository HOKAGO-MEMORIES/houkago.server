package com.houkago.server.content.post;

import java.time.LocalDate;
import java.util.List;

public record PostChecksumInput(
		String rawBody,
		String title,
		String slug,
		LocalDate date,
		String description,
		String category,
		PostSourceStatus sourceStatus,
		List<String> tags,
		LocalDate updated,
		String thumbnail,
		String series,
		boolean featured,
		String platform,
		String problemId) {

	public static PostChecksumInput from(PostMetadataMapping metadata, String rawBody) {
		return new PostChecksumInput(
				rawBody,
				metadata.title(),
				metadata.slug(),
				metadata.date(),
				metadata.description(),
				metadata.category(),
				metadata.sourceStatus(),
				metadata.tags(),
				metadata.updated(),
				metadata.thumbnail(),
				metadata.series(),
				metadata.featured(),
				metadata.platform(),
				metadata.problemId());
	}

}
