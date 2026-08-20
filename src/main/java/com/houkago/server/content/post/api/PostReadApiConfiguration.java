package com.houkago.server.content.post.api;

import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.houkago.server.content.post.asset.PostPublicAssetDeliveryProperties;
import com.houkago.server.content.post.asset.PostPublicAssetUrl;

@Configuration(proxyBeanMethods = false)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@EnableConfigurationProperties(PostPublicAssetDeliveryProperties.class)
public class PostReadApiConfiguration {

	@Bean
	PostPublicAssetUrl postPublicAssetUrl(PostPublicAssetDeliveryProperties properties) {
		return new PostPublicAssetUrl(properties.getPublicOrigin());
	}
}
