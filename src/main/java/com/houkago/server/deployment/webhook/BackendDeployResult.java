package com.houkago.server.deployment.webhook;

public record BackendDeployResult(Status status, String deliveryId, String revision, String image) {

	public enum Status {
		ACCEPTED,
		DUPLICATE
	}
}
