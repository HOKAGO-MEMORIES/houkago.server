package com.houkago.server.content.post.webhook;

public record PostGitHubWebhookJob(String deliveryId, String commitSha, String receivedAt) {
}
