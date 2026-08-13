package com.houkago.server.deployment.webhook;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(value = HttpStatus.BAD_REQUEST, reason = "Rejected deployment request")
public class BackendDeployValidationException extends RuntimeException {

	public BackendDeployValidationException(String message) {
		super(message);
	}

	public BackendDeployValidationException(String message, Throwable cause) {
		super(message, cause);
	}
}
