package com.houkago.server.content.post;

import java.time.LocalDate;
import java.util.List;

public record PostDetailResponse(
		String slug,
		String title,
		String description,
		String category,
		LocalDate postDate,
		LocalDate updated,
		List<String> tags,
		String thumbnail,
		String series,
		boolean featured,
		String rawBody) {
}
