package com.houkago.server.content.post;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "houkago.resync")
public class PostManualFullResyncProperties {

	private boolean enabled;
	private String postsRoot;
	private String commitHash;

	public boolean isEnabled() {
		return enabled;
	}

	public void setEnabled(boolean enabled) {
		this.enabled = enabled;
	}

	public String getPostsRoot() {
		return postsRoot;
	}

	public void setPostsRoot(String postsRoot) {
		this.postsRoot = postsRoot;
	}

	public String getCommitHash() {
		return commitHash;
	}

	public void setCommitHash(String commitHash) {
		this.commitHash = commitHash;
	}
}
