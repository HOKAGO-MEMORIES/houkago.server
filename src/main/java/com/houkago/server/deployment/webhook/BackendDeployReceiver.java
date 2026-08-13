package com.houkago.server.deployment.webhook;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BackendDeployReceiver {

	private static final Logger logger = LoggerFactory.getLogger(BackendDeployReceiver.class);

	private final BackendDeployVerifier verifier;
	private final BackendDeploySpool spool;

	public BackendDeployReceiver(BackendDeployVerifier verifier, BackendDeploySpool spool) {
		this.verifier = verifier;
		this.spool = spool;
	}

	public BackendDeployResult receive(String authorization, byte[] rawBody) {
		BackendDeployRequest request = verifier.verify(authorization, rawBody);
		BackendDeployResult result = spool.publish(request);
		logger.info(
				"event=backend_deploy_request status={} deliveryId={} revision={} image={}",
				result.status(),
				result.deliveryId(),
				result.revision(),
				result.image());
		return result;
	}
}
