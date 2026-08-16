package com.houkago.server.content.post.asset;

import java.net.URI;
import java.net.URISyntaxException;

public final class PostPublicAssetUrl {

	private final URI publicOrigin;

	public PostPublicAssetUrl(String publicOrigin) {
		this.publicOrigin = normalizeOrigin(publicOrigin);
	}

	public String baseUrl(String slug) {
		return publicOrigin.resolve(PostPublicAssetPath.encodedBasePath(slug)).toASCIIString();
	}

	private static URI normalizeOrigin(String value) {
		if (value == null || value.isBlank()) {
			throw new IllegalArgumentException("public asset origin is required");
		}

		URI origin;
		try {
			origin = new URI(value.trim());
		} catch (URISyntaxException exception) {
			throw new IllegalArgumentException("public asset origin must be a valid URI", exception);
		}

		String scheme = origin.getScheme();
		if (!("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))
				|| origin.getHost() == null
				|| origin.getUserInfo() != null
				|| origin.getQuery() != null
				|| origin.getFragment() != null
				|| !(origin.getPath().isEmpty() || "/".equals(origin.getPath()))) {
			throw new IllegalArgumentException("public asset origin must be an HTTP(S) origin without a path");
		}

		try {
			return new URI(
					origin.getScheme().toLowerCase(),
					null,
					origin.getHost(),
					origin.getPort(),
					"/",
					null,
					null);
		} catch (URISyntaxException exception) {
			throw new IllegalArgumentException("public asset origin could not be normalized", exception);
		}
	}
}
