package com.houkago.server.content.post.webhook;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PostGitHubWebhookReceiver {

	private static final Logger logger = LoggerFactory.getLogger(PostGitHubWebhookReceiver.class);

	private final PostGitHubWebhookVerifier verifier;
	private final PostGitHubWebhookSpool spool;

	public PostGitHubWebhookReceiver(PostGitHubWebhookVerifier verifier, PostGitHubWebhookSpool spool) {
		this.verifier = verifier;
		this.spool = spool;
	}

	public PostGitHubWebhookResult receive(
			String signature,
			String deliveryId,
			String event,
			byte[] rawBody) {
		PostGitHubWebhookRequest request = verifier.verify(signature, deliveryId, event, rawBody);
		PostGitHubWebhookResult result = spool.publish(request);

		logger.info(
				"event=post_github_webhook status={} deliveryId={} commitSha={}",
				result.status(),
				result.deliveryId(),
				result.commitSha());
		return result;
	}
}
