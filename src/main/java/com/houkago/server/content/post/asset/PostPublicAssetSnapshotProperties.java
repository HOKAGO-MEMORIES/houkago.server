package com.houkago.server.content.post.asset;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "houkago.assets.publication")
public class PostPublicAssetSnapshotProperties {

	private String postsRoot;
	private String assetRoot;
	private String generationId;
	private String action = "publish";

	public String getPostsRoot() {
		return postsRoot;
	}

	public void setPostsRoot(String postsRoot) {
		this.postsRoot = postsRoot;
	}

	public String getAssetRoot() {
		return assetRoot;
	}

	public void setAssetRoot(String assetRoot) {
		this.assetRoot = assetRoot;
	}

	public String getGenerationId() {
		return generationId;
	}

	public void setGenerationId(String generationId) {
		this.generationId = generationId;
	}

	public String getAction() {
		return action;
	}

	public void setAction(String action) {
		this.action = action;
	}
}
