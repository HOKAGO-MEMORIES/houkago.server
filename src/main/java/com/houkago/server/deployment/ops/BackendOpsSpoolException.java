package com.houkago.server.deployment.ops;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(value = HttpStatus.INTERNAL_SERVER_ERROR, reason = "Ops reconcile job could not be published")
public class BackendOpsSpoolException extends RuntimeException {

	public BackendOpsSpoolException(String message, Throwable cause) {
		super(message, cause);
	}
}
