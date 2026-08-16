package com.houkago.server.content.post.asset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class PostPublicAssetUrlTest {

	@Test
	void combinesConfiguredOriginWithCanonicalEncodedBasePath() {
		PostPublicAssetUrl url = new PostPublicAssetUrl("https://assets.example.test/");

		assertThat(url.baseUrl("한글 post"))
				.isEqualTo("https://assets.example.test/assets/posts/%ED%95%9C%EA%B8%80%20post/");
	}

	@Test
	void preservesConfiguredPortAndAlwaysReturnsTrailingSlash() {
		PostPublicAssetUrl url = new PostPublicAssetUrl("http://localhost:9090");

		assertThat(url.baseUrl("example-post"))
				.isEqualTo("http://localhost:9090/assets/posts/example-post/");
	}

	@Test
	void rejectsMissingOrNonOriginConfiguration() {
		assertThatThrownBy(() -> new PostPublicAssetUrl(" "))
				.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new PostPublicAssetUrl("ftp://assets.example.test"))
				.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new PostPublicAssetUrl("https://user@assets.example.test"))
				.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new PostPublicAssetUrl("https://assets.example.test/base"))
				.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new PostPublicAssetUrl("https://assets.example.test?query=true"))
				.isInstanceOf(IllegalArgumentException.class);
	}
}
