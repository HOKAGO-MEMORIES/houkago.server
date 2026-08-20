package com.houkago.server.content.post.sync;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;

class PostFullResyncExecutionConfigurationTest {

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
			.withUserConfiguration(PostFullResyncExecutionConfiguration.class)
			.withBean(PostFullResyncService.class, () -> mock(PostFullResyncService.class));
	private final WebApplicationContextRunner webContextRunner = new WebApplicationContextRunner()
			.withUserConfiguration(PostFullResyncExecutionConfiguration.class)
			.withBean(PostFullResyncService.class, () -> mock(PostFullResyncService.class));

	@Test
	void applicationModeRegistersOnlyStartupRunner() {
		webContextRunner.run(context -> {
			assertThat(context).hasSingleBean(PostFullResyncRunner.class);
			assertThat(context).doesNotHaveBean(PostOneShotFullResyncRunner.class);
		});
	}

	@Test
	void syncModeRegistersOnlyOneShotRunner() {
		contextRunner
				.withPropertyValues("spring.profiles.active=sync")
				.run(context -> {
					assertThat(context).hasSingleBean(PostOneShotFullResyncRunner.class);
					assertThat(context).doesNotHaveBean(PostFullResyncRunner.class);
				});
	}

	@Test
	void assetSyncModeRegistersNoResyncRunner() {
		contextRunner
				.withPropertyValues("spring.profiles.active=asset-sync")
				.run(context -> {
					assertThat(context).doesNotHaveBean(PostOneShotFullResyncRunner.class);
					assertThat(context).doesNotHaveBean(PostFullResyncRunner.class);
				});
	}
}
