package com.houkago.server.content.post;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
@Profile("!test")
public class PostManualFullResyncConfiguration {

	@Bean
	PostMarkdownParser postMarkdownParser() {
		return new PostMarkdownParser();
	}

	@Bean
	PostSourceScanner postSourceScanner() {
		return new PostSourceScanner();
	}

	@Bean
	PostSourceFileReader postSourceFileReader(PostMarkdownParser parser) {
		return new PostSourceFileReader(parser);
	}

	@Bean
	PostSourceCandidateLoader postSourceCandidateLoader(
			PostSourceScanner scanner,
			PostSourceFileReader reader) {
		return new PostSourceCandidateLoader(scanner, reader);
	}

	@Bean
	PostManualFullResyncService postManualFullResyncService(
			PostSourceCandidateLoader candidateLoader,
			PostReadModelUpsertService upsertService) {
		return new PostManualFullResyncService(candidateLoader, upsertService);
	}
}
