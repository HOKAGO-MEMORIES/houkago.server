package com.houkago.server.content.post.sync;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration(proxyBeanMethods = false)
@Profile("!test & !asset-sync")
@EnableConfigurationProperties(PostFullResyncProperties.class)
public class PostFullResyncExecutionConfiguration {

	@Bean
	@Profile("!sync & !asset-sync")
	PostFullResyncRunner postFullResyncRunner(
			PostFullResyncService resyncService,
			PostFullResyncProperties properties) {
		return new PostFullResyncRunner(resyncService, properties);
	}

	@Bean
	@Profile("sync")
	PostOneShotFullResyncRunner postOneShotFullResyncRunner(
			PostFullResyncService resyncService,
			PostFullResyncProperties properties) {
		return new PostOneShotFullResyncRunner(resyncService, properties);
	}
}
