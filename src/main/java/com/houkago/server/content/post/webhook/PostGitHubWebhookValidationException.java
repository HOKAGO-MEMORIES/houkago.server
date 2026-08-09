package com.houkago.server.content.post.webhook;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(value = HttpStatus.BAD_REQUEST, reason = "Rejected webhook request")
public class PostGitHubWebhookValidationException extends RuntimeException {

	public PostGitHubWebhookValidationException(String message) {
		super(message);
	}

	public PostGitHubWebhookValidationException(String message, Throwable cause) {
		super(message, cause);
	}
}
