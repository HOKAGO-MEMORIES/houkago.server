package com.houkago.server.content.post.webhook;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import com.fasterxml.jackson.databind.ObjectMapper;

class PostGitHubWebhookConfigurationTest {

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
			.withBean(ObjectMapper.class, ObjectMapper::new)
			.withUserConfiguration(PostGitHubWebhookConfiguration.class);

	@Test
	void webhookComponentsAreDisabledByDefault() {
		contextRunner.run(context -> {
			assertThat(context).doesNotHaveBean(PostGitHubWebhookVerifier.class);
			assertThat(context).doesNotHaveBean(PostGitHubWebhookSpool.class);
			assertThat(context).doesNotHaveBean(PostGitHubWebhookReceiver.class);
		});
	}

	@Test
	void enabledWebhookFailsClosedWhenSecretIsMissing() {
		contextRunner
				.withPropertyValues(
						"houkago.webhook.github.posts.enabled=true",
						"houkago.webhook.github.posts.repository-full-name=example/houkago.posts",
						"houkago.webhook.github.posts.spool-root=/tmp/synthetic-spool")
				.run(context -> assertThat(context).hasFailed());
	}
}
