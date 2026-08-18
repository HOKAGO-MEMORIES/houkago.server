package com.houkago.server.content.post.api;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;

import com.houkago.server.content.post.asset.PostPublicAssetUrl;

class PostReadApiConfigurationTest {

	private final WebApplicationContextRunner webContextRunner = new WebApplicationContextRunner()
			.withUserConfiguration(PostReadApiConfiguration.class);

	@Test
	void localProfileUsesLocalhostDefaultWhenOriginIsMissing() {
		webContextRunner
				.withInitializer(new ConfigDataApplicationContextInitializer())
				.withPropertyValues("spring.profiles.active=local")
				.run(context -> {
					assertThat(context).hasNotFailed();
					assertThat(context.getBean(PostPublicAssetUrl.class).baseUrl("example-post"))
							.isEqualTo("http://localhost:8080/assets/posts/example-post/");
				});
	}

	@Test
	void productionEquivalentWebContextRejectsMissingOrigin() {
		webContextRunner
				.withInitializer(new ConfigDataApplicationContextInitializer())
				.withPropertyValues("spring.profiles.active=docker")
				.run(context -> {
					assertThat(context).hasFailed();
					assertThat(context.getStartupFailure())
							.hasRootCauseMessage("public asset origin is required");
				});
	}

	@Test
	void productionEquivalentWebContextRejectsBlankOrigin() {
		webContextRunner
				.withPropertyValues(
						"spring.profiles.active=docker",
						"houkago.assets.public-origin=   ")
				.run(context -> {
					assertThat(context).hasFailed();
					assertThat(context.getStartupFailure())
							.hasRootCauseMessage("public asset origin is required");
				});
	}

	@Test
	void productionEquivalentWebContextRejectsInvalidOrigin() {
		webContextRunner
				.withPropertyValues(
						"spring.profiles.active=docker",
						"houkago.assets.public-origin=api.example.test")
				.run(context -> {
					assertThat(context).hasFailed();
					assertThat(context.getStartupFailure())
							.hasRootCauseMessage("public asset origin must be an HTTP(S) origin without a path");
				});
	}

	@Test
	void productionEquivalentWebContextAcceptsAndNormalizesValidOrigin() {
		webContextRunner
				.withPropertyValues(
						"spring.profiles.active=docker",
						"houkago.assets.public-origin=https://api.example.test/")
				.run(context -> {
					assertThat(context).hasNotFailed();
					assertThat(context.getBean(PostPublicAssetUrl.class).baseUrl("example-post"))
							.isEqualTo("https://api.example.test/assets/posts/example-post/");
				});
	}

	@Test
	void nonWebSyncModeDoesNotRequirePublicOrigin() {
		new ApplicationContextRunner()
				.withUserConfiguration(PostReadApiConfiguration.class, PostReadController.class)
				.withPropertyValues(
						"spring.profiles.active=docker,sync",
						"houkago.assets.public-origin=")
				.run(context -> {
					assertThat(context).hasNotFailed();
					assertThat(context).doesNotHaveBean(PostPublicAssetUrl.class);
					assertThat(context).doesNotHaveBean(PostReadController.class);
				});
	}

	@Test
	void nonWebAssetSyncModeDoesNotRequirePublicOrigin() {
		new ApplicationContextRunner()
				.withUserConfiguration(PostReadApiConfiguration.class, PostReadController.class)
				.withPropertyValues(
						"spring.profiles.active=docker,asset-sync",
						"houkago.assets.public-origin=")
				.run(context -> {
					assertThat(context).hasNotFailed();
					assertThat(context).doesNotHaveBean(PostPublicAssetUrl.class);
					assertThat(context).doesNotHaveBean(PostReadController.class);
				});
	}
}
