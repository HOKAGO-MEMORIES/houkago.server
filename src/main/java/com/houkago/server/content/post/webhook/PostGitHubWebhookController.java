package com.houkago.server.content.post.webhook;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/webhooks/github/posts")
@Profile("!sync")
@ConditionalOnProperty(prefix = "houkago.webhook.github.posts", name = "enabled", havingValue = "true")
public class PostGitHubWebhookController {

	private final PostGitHubWebhookReceiver receiver;

	public PostGitHubWebhookController(PostGitHubWebhookReceiver receiver) {
		this.receiver = receiver;
	}

	@PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<Void> receive(
			@RequestHeader(value = "X-Hub-Signature-256", required = false) String signature,
			@RequestHeader(value = "X-GitHub-Delivery", required = false) String deliveryId,
			@RequestHeader(value = "X-GitHub-Event", required = false) String event,
			@RequestBody byte[] rawBody) {
		PostGitHubWebhookResult result = receiver.receive(signature, deliveryId, event, rawBody);
		if (result.status() == PostGitHubWebhookResult.Status.DUPLICATE) {
			return ResponseEntity.ok().build();
		}
		return ResponseEntity.accepted().build();
	}
}
