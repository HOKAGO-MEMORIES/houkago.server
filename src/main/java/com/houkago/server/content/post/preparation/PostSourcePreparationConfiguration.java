package com.houkago.server.content.post.preparation;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import com.houkago.server.content.post.source.PostMarkdownParser;
import com.houkago.server.content.post.source.PostSourceCandidateLoader;
import com.houkago.server.content.post.source.PostSourceFileReader;
import com.houkago.server.content.post.source.PostSourceScanner;

@Configuration(proxyBeanMethods = false)
@Profile("!test")
public class PostSourcePreparationConfiguration {

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
}
