package com.houkago.server.deployment.ops;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BackendOpsReceiver {

	private static final Logger logger = LoggerFactory.getLogger(BackendOpsReceiver.class);

	private final BackendOpsVerifier verifier;
	private final BackendOpsSpool spool;

	public BackendOpsReceiver(BackendOpsVerifier verifier, BackendOpsSpool spool) {
		this.verifier = verifier;
		this.spool = spool;
	}

	public BackendOpsResult receive(String authorization, byte[] rawBody) {
		BackendOpsRequest request = verifier.verify(authorization, rawBody);
		BackendOpsResult result = spool.publish(request);
		logger.info(
				"event=backend_ops_reconcile_request status={} deliveryId={} revision={}",
				result.status(),
				result.deliveryId(),
				result.revision());
		return result;
	}
}
