package com.houkago.server.content.post;

import java.time.LocalDate;
import java.util.List;

public record PostMetadataMapping(
		String title,
		String slug,
		LocalDate date,
		String description,
		String category,
		PostSourceStatus sourceStatus,
		PostSyncStatus syncStatus,
		PostVisibility visibility,
		List<String> tags,
		LocalDate updated,
		String thumbnail,
		String series,
		boolean featured,
		String platform,
		String problemId) {

	public boolean isPubliclyVisible() {
		return PostVisibilityPolicy.isPubliclyVisible(sourceStatus, syncStatus, visibility);
	}
}
