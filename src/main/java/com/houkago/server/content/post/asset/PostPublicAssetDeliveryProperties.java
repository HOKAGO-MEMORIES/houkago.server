package com.houkago.server.content.post.asset;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "houkago.assets")
public class PostPublicAssetDeliveryProperties {

	private String publicOrigin;

	public String getPublicOrigin() {
		return publicOrigin;
	}

	public void setPublicOrigin(String publicOrigin) {
		this.publicOrigin = publicOrigin;
	}
}
