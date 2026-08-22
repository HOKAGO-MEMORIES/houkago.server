package com.houkago.server.content.post.api;

import java.time.LocalDate;

public record PostNavigationResponse(
		String slug,
		String title,
		LocalDate postDate) {
}
