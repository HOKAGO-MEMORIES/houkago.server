package com.houkago.server.content.post.webhook;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(value = HttpStatus.INTERNAL_SERVER_ERROR, reason = "Webhook job could not be published")
public class PostGitHubWebhookSpoolException extends RuntimeException {

	public PostGitHubWebhookSpoolException(String message, Throwable cause) {
		super(message, cause);
	}
}
