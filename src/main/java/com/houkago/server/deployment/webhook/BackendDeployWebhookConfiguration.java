package com.houkago.server.deployment.webhook;

import java.time.Clock;

import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.fasterxml.jackson.databind.ObjectMapper;

@Configuration(proxyBeanMethods = false)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@EnableConfigurationProperties(BackendDeployWebhookProperties.class)
@ConditionalOnProperty(prefix = "houkago.deploy.webhook", name = "enabled", havingValue = "true")
public class BackendDeployWebhookConfiguration {

	@Bean
	BackendDeployVerifier backendDeployVerifier(
			ObjectMapper objectMapper,
			BackendDeployWebhookProperties properties) {
		return new BackendDeployVerifier(objectMapper, properties);
	}

	@Bean
	BackendDeploySpool backendDeploySpool(
			ObjectMapper objectMapper,
			BackendDeployWebhookProperties properties) {
		return new BackendDeploySpool(objectMapper, Clock.systemUTC(), properties);
	}

	@Bean
	BackendDeployReceiver backendDeployReceiver(
			BackendDeployVerifier verifier,
			BackendDeploySpool spool) {
		return new BackendDeployReceiver(verifier, spool);
	}
}
