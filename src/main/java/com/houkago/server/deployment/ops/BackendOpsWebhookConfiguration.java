package com.houkago.server.deployment.ops;

import java.time.Clock;

import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.fasterxml.jackson.databind.ObjectMapper;

@Configuration(proxyBeanMethods = false)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@EnableConfigurationProperties(BackendOpsWebhookProperties.class)
@ConditionalOnProperty(prefix = "houkago.ops.webhook", name = "enabled", havingValue = "true")
public class BackendOpsWebhookConfiguration {

	@Bean
	BackendOpsVerifier backendOpsVerifier(ObjectMapper objectMapper, BackendOpsWebhookProperties properties) {
		return new BackendOpsVerifier(objectMapper, properties);
	}

	@Bean
	BackendOpsSpool backendOpsSpool(ObjectMapper objectMapper, BackendOpsWebhookProperties properties) {
		return new BackendOpsSpool(objectMapper, Clock.systemUTC(), properties);
	}

	@Bean
	BackendOpsReceiver backendOpsReceiver(BackendOpsVerifier verifier, BackendOpsSpool spool) {
		return new BackendOpsReceiver(verifier, spool);
	}
}
