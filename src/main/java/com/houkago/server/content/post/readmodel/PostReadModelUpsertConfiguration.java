package com.houkago.server.content.post.readmodel;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
@Profile("!test")
public class PostReadModelUpsertConfiguration {

	@Bean
	PostReadModelUpsertService postReadModelUpsertService(
			PostReadModelRepository repository,
			PostReadModelCandidateProcessor processor) {
		return new PostReadModelUpsertService(repository, processor);
	}

	@Bean
	PostReadModelRetirementService postReadModelRetirementService(
			PostReadModelRepository repository,
			PostReadModelAssembler assembler) {
		return new PostReadModelRetirementService(repository, assembler);
	}
}
