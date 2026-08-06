package com.houkago.server.content.post.sync;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class PostManualFullResyncExecutionConfigurationTest {

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
			.withUserConfiguration(PostManualFullResyncExecutionConfiguration.class)
			.withBean(PostManualFullResyncService.class, () -> mock(PostManualFullResyncService.class));

	@Test
	void applicationModeRegistersOnlyStartupRunner() {
		contextRunner.run(context -> {
			assertThat(context).hasSingleBean(PostManualFullResyncRunner.class);
			assertThat(context).doesNotHaveBean(PostOneShotFullResyncRunner.class);
		});
	}

	@Test
	void syncModeRegistersOnlyOneShotRunner() {
		contextRunner
				.withPropertyValues("spring.profiles.active=sync")
				.run(context -> {
					assertThat(context).hasSingleBean(PostOneShotFullResyncRunner.class);
					assertThat(context).doesNotHaveBean(PostManualFullResyncRunner.class);
				});
	}
}
