package com.houkago.server.content.post.metadata;

import java.util.List;

public record PostMetadataInput(
		String title,
		String slug,
		String date,
		String description,
		String category,
		String status,
		List<String> tags,
		String updated,
		String thumbnail,
		String series,
		Boolean featured,
		String platform,
		String problemId) {
}
