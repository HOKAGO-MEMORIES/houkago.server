package com.houkago.server.deployment.webhook;

public record BackendDeployPayload(String deliveryId, String revision, String image) {
}
