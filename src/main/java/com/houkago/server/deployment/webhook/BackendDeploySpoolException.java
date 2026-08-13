package com.houkago.server.deployment.webhook;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(value = HttpStatus.INTERNAL_SERVER_ERROR, reason = "Deployment job could not be published")
public class BackendDeploySpoolException extends RuntimeException {

	public BackendDeploySpoolException(String message, Throwable cause) {
		super(message, cause);
	}
}
