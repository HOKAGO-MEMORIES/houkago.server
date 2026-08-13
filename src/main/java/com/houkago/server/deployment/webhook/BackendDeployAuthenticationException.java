package com.houkago.server.deployment.webhook;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(value = HttpStatus.UNAUTHORIZED, reason = "Invalid deployment authentication")
public class BackendDeployAuthenticationException extends RuntimeException {

	public BackendDeployAuthenticationException(String message) {
		super(message);
	}
}
