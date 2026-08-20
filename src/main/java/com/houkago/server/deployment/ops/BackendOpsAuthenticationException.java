package com.houkago.server.deployment.ops;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(value = HttpStatus.UNAUTHORIZED, reason = "Invalid operations authentication")
public class BackendOpsAuthenticationException extends RuntimeException {

	public BackendOpsAuthenticationException(String message) {
		super(message);
	}
}
