package com.houkago.server.content.post.policy;

public final class PostVisibilityPolicy {

	private PostVisibilityPolicy() {
	}

	public static boolean isPubliclyVisible(
			PostSourceStatus sourceStatus,
			PostSyncStatus syncStatus,
			PostVisibility visibility) {
		return sourceStatus == PostSourceStatus.PUBLISHED
				&& syncStatus == PostSyncStatus.ACTIVE
				&& visibility == PostVisibility.PUBLIC;
	}
}
