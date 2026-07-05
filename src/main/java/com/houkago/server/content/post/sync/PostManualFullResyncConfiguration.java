package com.houkago.server.content.post.sync;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import com.houkago.server.content.post.readmodel.PostReadModelUpsertService;
import com.houkago.server.content.post.source.PostMarkdownParser;
import com.houkago.server.content.post.source.PostSourceCandidateLoader;
import com.houkago.server.content.post.source.PostSourceFileReader;
import com.houkago.server.content.post.source.PostSourceScanner;

@Configuration
@Profile("!test")
@EnableConfigurationProperties(PostManualFullResyncProperties.class)
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

	@Bean
	PostManualFullResyncRunner postManualFullResyncRunner(
			PostManualFullResyncService resyncService,
			PostManualFullResyncProperties properties) {
		return new PostManualFullResyncRunner(resyncService, properties);
	}
}
