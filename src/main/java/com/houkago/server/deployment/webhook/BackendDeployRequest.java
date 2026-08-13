package com.houkago.server.deployment.webhook;

public record BackendDeployRequest(String deliveryId, String revision, String image) {
}
