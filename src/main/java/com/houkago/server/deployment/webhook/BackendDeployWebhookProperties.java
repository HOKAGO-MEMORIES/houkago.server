package com.houkago.server.deployment.webhook;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "houkago.deploy.webhook")
public class BackendDeployWebhookProperties {

	private boolean enabled;
	private String secret;
	private String imageRepository;
	private String spoolRoot;
	private Duration workerGracePeriod = Duration.ofSeconds(5);

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

	public String getImageRepository() {
		return imageRepository;
	}

	public void setImageRepository(String imageRepository) {
		this.imageRepository = imageRepository;
	}

	public String getSpoolRoot() {
		return spoolRoot;
	}

	public void setSpoolRoot(String spoolRoot) {
		this.spoolRoot = spoolRoot;
	}

	public Duration getWorkerGracePeriod() {
		return workerGracePeriod;
	}

	public void setWorkerGracePeriod(Duration workerGracePeriod) {
		this.workerGracePeriod = workerGracePeriod;
	}
}
