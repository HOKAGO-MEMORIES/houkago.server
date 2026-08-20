package com.houkago.server.content.post.webhook;

import java.time.Clock;

import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.fasterxml.jackson.databind.ObjectMapper;

@Configuration(proxyBeanMethods = false)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@EnableConfigurationProperties(PostGitHubWebhookProperties.class)
@ConditionalOnProperty(prefix = "houkago.webhook.github.posts", name = "enabled", havingValue = "true")
public class PostGitHubWebhookConfiguration {

	@Bean
	PostGitHubWebhookVerifier postGitHubWebhookVerifier(
			ObjectMapper objectMapper,
			PostGitHubWebhookProperties properties) {
		return new PostGitHubWebhookVerifier(objectMapper, properties);
	}

	@Bean
	PostGitHubWebhookSpool postGitHubWebhookSpool(
			ObjectMapper objectMapper,
			PostGitHubWebhookProperties properties) {
		return new PostGitHubWebhookSpool(objectMapper, Clock.systemUTC(), properties);
	}

	@Bean
	PostGitHubWebhookReceiver postGitHubWebhookReceiver(
			PostGitHubWebhookVerifier verifier,
			PostGitHubWebhookSpool spool) {
		return new PostGitHubWebhookReceiver(verifier, spool);
	}
}
