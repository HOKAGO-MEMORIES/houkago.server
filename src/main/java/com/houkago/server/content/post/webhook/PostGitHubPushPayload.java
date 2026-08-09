package com.houkago.server.content.post.webhook;

import com.fasterxml.jackson.annotation.JsonProperty;

public record PostGitHubPushPayload(
		String ref,
		Boolean forced,
		String after,
		Repository repository) {

	public record Repository(@JsonProperty("full_name") String fullName) {
	}
}
