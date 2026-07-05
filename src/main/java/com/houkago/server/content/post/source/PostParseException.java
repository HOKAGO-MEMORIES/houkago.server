package com.houkago.server.content.post.source;

public class PostParseException extends RuntimeException {

	public PostParseException(String message) {
		super(message);
	}

	public PostParseException(String message, Throwable cause) {
		super(message, cause);
	}
}
