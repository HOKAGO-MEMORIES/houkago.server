package com.houkago.server.content.post.query;

import java.time.LocalDate;

public record PostReadNavigationItem(
		String slug,
		String title,
		LocalDate postDate) {
}
