package com.houkago.server.content.post.readmodel;

import java.time.LocalDate;

public record PostReadSummaryProjection(
		String slug,
		String title,
		String description,
		String category,
		LocalDate postDate,
		LocalDate updated,
		String tagsJson,
		String thumbnail,
		String series,
		boolean featured) {
}
