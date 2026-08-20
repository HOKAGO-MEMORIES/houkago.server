package com.houkago.server.deployment.ops;

public record BackendOpsJob(
		String deliveryId,
		String revision,
		String receivedAt,
		String notBefore) {
}
