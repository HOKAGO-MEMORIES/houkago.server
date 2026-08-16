package com.houkago.server.content.post.asset;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import com.houkago.server.content.post.readmodel.PostReadModelCandidatePreflight;
import com.houkago.server.content.post.source.PostSourceCandidateLoader;

@Configuration
@Profile("asset-sync")
@EnableConfigurationProperties(PostPublicAssetSnapshotProperties.class)
public class PostPublicAssetSnapshotExecutionConfiguration {

	@Bean
	PostPublicAssetSnapshotRunner postPublicAssetSnapshotRunner(
			PostSourceCandidateLoader candidateLoader,
			PostReadModelCandidatePreflight candidatePreflight,
			PostPublicAssetSnapshotPublisher publisher,
			PostPublicAssetSnapshotProperties properties) {
		return new PostPublicAssetSnapshotRunner(candidateLoader, candidatePreflight, publisher, properties);
	}
}
