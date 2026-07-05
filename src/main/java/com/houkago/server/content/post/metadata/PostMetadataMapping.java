package com.houkago.server.content.post.metadata;

import java.time.LocalDate;
import java.util.List;

import com.houkago.server.content.post.policy.PostSourceStatus;
import com.houkago.server.content.post.policy.PostSyncStatus;
import com.houkago.server.content.post.policy.PostVisibility;
import com.houkago.server.content.post.policy.PostVisibilityPolicy;

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
