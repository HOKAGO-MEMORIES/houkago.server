package com.houkago.server.content.post.sync;

import java.nio.file.Path;
import java.time.Instant;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;

public class PostFullResyncRunner implements ApplicationRunner {

	private static final Logger log = LoggerFactory.getLogger(PostFullResyncRunner.class);

	private final PostFullResyncService resyncService;
	private final PostFullResyncProperties properties;

	public PostFullResyncRunner(
			PostFullResyncService resyncService,
			PostFullResyncProperties properties) {
		this.resyncService = Objects.requireNonNull(resyncService, "resyncService is required");
		this.properties = Objects.requireNonNull(properties, "properties is required");
	}

	@Override
	public void run(ApplicationArguments args) {
		if (!properties.isEnabled()) {
			return;
		}

		Path postsRoot = Path.of(requireText("houkago.resync.posts-root", properties.getPostsRoot()));
		String commitHash = requireText("houkago.resync.commit-hash", properties.getCommitHash());
		PostFullResyncResult result = resyncService.resync(postsRoot, commitHash, Instant.now());

		log.info("Post full resync completed: candidateCount={}, createdCount={}, updatedCount={}, "
						+ "touchedCount={}, totalUpsertedCount={}, deletedCount={}, commitHash={}",
				result.candidateCount(),
				result.createdCount(),
				result.updatedCount(),
				result.touchedCount(),
				result.totalUpsertedCount(),
				result.deletedCount(),
				result.commitHash());
	}

	private static String requireText(String field, String value) {
		if (value == null || value.isBlank()) {
			throw new IllegalArgumentException(field + " is required when houkago.resync.enabled=true");
		}
		return value.trim();
	}
}
