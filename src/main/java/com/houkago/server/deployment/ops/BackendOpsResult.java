package com.houkago.server.deployment.ops;

public record BackendOpsResult(Status status, String deliveryId, String revision) {

	public enum Status {
		ACCEPTED,
		DUPLICATE
	}
}
