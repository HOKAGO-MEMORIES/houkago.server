package com.houkago.server.content.post.query;

import java.time.LocalDate;
import java.util.List;

public record PostReadListItem(
		String slug,
		String title,
		String description,
		String category,
		LocalDate postDate,
		LocalDate updated,
		List<String> tags,
		String thumbnail,
		String series,
		boolean featured) {

	public PostReadListItem {
		tags = tags == null ? List.of() : List.copyOf(tags);
	}
}
