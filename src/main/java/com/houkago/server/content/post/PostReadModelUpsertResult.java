package com.houkago.server.content.post;

import java.util.Objects;

public record PostReadModelUpsertResult(PostReadModel post, PostReadModelUpsertStatus status) {

	public PostReadModelUpsertResult {
		Objects.requireNonNull(post, "post is required");
		Objects.requireNonNull(status, "status is required");
	}
}
