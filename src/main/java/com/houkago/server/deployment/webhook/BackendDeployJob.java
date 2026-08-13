package com.houkago.server.deployment.webhook;

public record BackendDeployJob(
		String deliveryId,
		String revision,
		String image,
		String receivedAt,
		String notBefore) {
}
