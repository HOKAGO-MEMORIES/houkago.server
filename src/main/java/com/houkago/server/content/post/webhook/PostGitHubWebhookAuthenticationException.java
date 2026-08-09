package com.houkago.server.content.post.webhook;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(value = HttpStatus.UNAUTHORIZED, reason = "Invalid webhook authentication")
public class PostGitHubWebhookAuthenticationException extends RuntimeException {

	public PostGitHubWebhookAuthenticationException(String message) {
		super(message);
	}
}
