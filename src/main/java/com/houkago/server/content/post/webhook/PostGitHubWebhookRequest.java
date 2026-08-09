package com.houkago.server.content.post.webhook;

public record PostGitHubWebhookRequest(String deliveryId, String commitSha) {
}
