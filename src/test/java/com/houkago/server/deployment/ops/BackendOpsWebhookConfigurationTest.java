package com.houkago.server.deployment.ops;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;

import com.fasterxml.jackson.databind.ObjectMapper;

class BackendOpsWebhookConfigurationTest {

	private final WebApplicationContextRunner contextRunner = new WebApplicationContextRunner()
			.withBean(ObjectMapper.class, ObjectMapper::new)
			.withUserConfiguration(BackendOpsWebhookConfiguration.class);

	@Test
	void opsComponentsAreDisabledByDefault() {
		contextRunner.run(context -> {
			assertThat(context).doesNotHaveBean(BackendOpsVerifier.class);
			assertThat(context).doesNotHaveBean(BackendOpsSpool.class);
			assertThat(context).doesNotHaveBean(BackendOpsReceiver.class);
		});
	}

	@Test
	void enabledOpsEndpointFailsClosedWhenSecretIsMissing() {
		contextRunner
				.withPropertyValues(
						"houkago.ops.webhook.enabled=true",
						"houkago.ops.webhook.spool-root=/tmp/synthetic-ops-spool")
				.run(context -> assertThat(context).hasFailed());
	}
}
