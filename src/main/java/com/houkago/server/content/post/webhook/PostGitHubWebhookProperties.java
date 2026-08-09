package com.houkago.server.content.post.webhook;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "houkago.webhook.github.posts")
public class PostGitHubWebhookProperties {

	private boolean enabled;
	private String secret;
	private String repositoryFullName;
	private String ref = "refs/heads/main";
	private String spoolRoot;

	public boolean isEnabled() {
		return enabled;
	}

	public void setEnabled(boolean enabled) {
		this.enabled = enabled;
	}

	public String getSecret() {
		return secret;
	}

	public void setSecret(String secret) {
		this.secret = secret;
	}

	public String getRepositoryFullName() {
		return repositoryFullName;
	}

	public void setRepositoryFullName(String repositoryFullName) {
		this.repositoryFullName = repositoryFullName;
	}

	public String getRef() {
		return ref;
	}

	public void setRef(String ref) {
		this.ref = ref;
	}

	public String getSpoolRoot() {
		return spoolRoot;
	}

	public void setSpoolRoot(String spoolRoot) {
		this.spoolRoot = spoolRoot;
	}
}
