package com.houkago.server.content.post.readmodel;

import java.time.LocalDate;

public record PostReadNavigationProjection(
		String slug,
		String title,
		LocalDate postDate) {
}
