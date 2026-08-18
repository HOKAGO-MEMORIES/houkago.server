package com.houkago.server.content.post.sync;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import com.houkago.server.content.post.preparation.PostCandidatePreflight;
import com.houkago.server.content.post.readmodel.PostReadModelRetirementService;
import com.houkago.server.content.post.readmodel.PostReadModelUpsertService;
import com.houkago.server.content.post.source.PostSourceCandidateLoader;

@Configuration(proxyBeanMethods = false)
@Profile("!test & !asset-sync")
public class PostManualFullResyncConfiguration {

	@Bean
	PostManualFullResyncService postManualFullResyncService(
			PostSourceCandidateLoader candidateLoader,
			PostCandidatePreflight candidatePreflight,
			PostReadModelUpsertService upsertService,
			PostReadModelRetirementService retirementService) {
		return new PostManualFullResyncService(candidateLoader, candidatePreflight, upsertService, retirementService);
	}
}
