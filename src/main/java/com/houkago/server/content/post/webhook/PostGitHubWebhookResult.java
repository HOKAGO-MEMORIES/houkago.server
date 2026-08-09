package com.houkago.server.content.post.webhook;

public record PostGitHubWebhookResult(Status status, String deliveryId, String commitSha) {

	public enum Status {
		ACCEPTED,
		DUPLICATE
	}
}
