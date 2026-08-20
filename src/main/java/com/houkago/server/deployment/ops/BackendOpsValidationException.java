package com.houkago.server.deployment.ops;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(value = HttpStatus.BAD_REQUEST, reason = "Rejected operations reconcile request")
public class BackendOpsValidationException extends RuntimeException {

	public BackendOpsValidationException(String message) {
		super(message);
	}

	public BackendOpsValidationException(String message, Throwable cause) {
		super(message, cause);
	}
}
