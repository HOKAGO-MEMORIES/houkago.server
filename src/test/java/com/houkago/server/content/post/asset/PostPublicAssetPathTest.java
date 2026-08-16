package com.houkago.server.content.post.asset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class PostPublicAssetPathTest {

	@Test
	void mapsSlugAndNestedAssetToCanonicalPublicPath() {
		assertThat(PostPublicAssetPath.basePath("example-post"))
				.isEqualTo("/assets/posts/example-post/");
		assertThat(PostPublicAssetPath.assetPath("example-post", "diagrams/architecture.png"))
				.isEqualTo("/assets/posts/example-post/diagrams/architecture.png");
	}

	@Test
	void rejectsUnsafeSlugAndAssetPath() {
		assertThatThrownBy(() -> PostPublicAssetPath.basePath("../private"))
				.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> PostPublicAssetPath.assetPath("example-post", "../private.txt"))
				.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> PostPublicAssetPath.assetPath("example-post", "/private.txt"))
				.isInstanceOf(IllegalArgumentException.class);
	}
}
