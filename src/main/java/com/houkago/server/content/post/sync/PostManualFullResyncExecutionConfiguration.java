package com.houkago.server.content.post.sync;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
@Profile("!test")
@EnableConfigurationProperties(PostManualFullResyncProperties.class)
public class PostManualFullResyncExecutionConfiguration {

	@Bean
	@Profile("!sync & !asset-sync")
	PostManualFullResyncRunner postManualFullResyncRunner(
			PostManualFullResyncService resyncService,
			PostManualFullResyncProperties properties) {
		return new PostManualFullResyncRunner(resyncService, properties);
	}

	@Bean
	@Profile("sync")
	PostOneShotFullResyncRunner postOneShotFullResyncRunner(
			PostManualFullResyncService resyncService,
			PostManualFullResyncProperties properties) {
		return new PostOneShotFullResyncRunner(resyncService, properties);
	}
}
