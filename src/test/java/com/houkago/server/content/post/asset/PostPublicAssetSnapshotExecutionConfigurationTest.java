package com.houkago.server.content.post.asset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import com.houkago.server.content.post.preparation.PostCandidatePreflight;
import com.houkago.server.content.post.source.PostSourceCandidateLoader;

class PostPublicAssetSnapshotExecutionConfigurationTest {

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
			.withUserConfiguration(PostPublicAssetSnapshotExecutionConfiguration.class)
			.withBean(PostSourceCandidateLoader.class, () -> mock(PostSourceCandidateLoader.class))
			.withBean(PostCandidatePreflight.class, () -> mock(PostCandidatePreflight.class))
			.withBean(PostPublicAssetSnapshotPublisher.class, () -> mock(PostPublicAssetSnapshotPublisher.class));

	@Test
	void regularApplicationModeDoesNotRegisterAssetRunner() {
		contextRunner.run(context -> assertThat(context).doesNotHaveBean(PostPublicAssetSnapshotRunner.class));
	}

	@Test
	void assetSyncModeRegistersOnlyAssetRunner() {
		contextRunner
				.withPropertyValues("spring.profiles.active=asset-sync")
				.run(context -> assertThat(context).hasSingleBean(PostPublicAssetSnapshotRunner.class));
	}
}
