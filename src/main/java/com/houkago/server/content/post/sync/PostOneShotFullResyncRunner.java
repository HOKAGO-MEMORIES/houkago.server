package com.houkago.server.content.post.sync;

import java.nio.file.Path;
import java.time.Instant;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.ExitCodeGenerator;

public class PostOneShotFullResyncRunner implements ApplicationRunner, ExitCodeGenerator {

	private static final Logger log = LoggerFactory.getLogger(PostOneShotFullResyncRunner.class);

	private final PostManualFullResyncService resyncService;
	private final PostManualFullResyncProperties properties;
	private int exitCode;

	public PostOneShotFullResyncRunner(
			PostManualFullResyncService resyncService,
			PostManualFullResyncProperties properties) {
		this.resyncService = Objects.requireNonNull(resyncService, "resyncService is required");
		this.properties = Objects.requireNonNull(properties, "properties is required");
	}

	@Override
	public void run(ApplicationArguments args) {
		String logCommitHash = normalizedCommitHash();
		try {
			Path postsRoot = Path.of(requireText("houkago.resync.posts-root", properties.getPostsRoot()));
			String commitHash = requireText("houkago.resync.commit-hash", properties.getCommitHash());
			PostManualFullResyncResult result = resyncService.resync(postsRoot, commitHash, Instant.now());

			log.info("event=post_full_resync status=SUCCESS commitHash={} CREATED={} UPDATED={} TOUCHED={} "
						+ "DELETED={} candidateCount={} totalUpsertedCount={}",
					result.commitHash(),
					result.createdCount(),
					result.updatedCount(),
					result.touchedCount(),
					result.deletedCount(),
					result.candidateCount(),
					result.totalUpsertedCount());
		} catch (RuntimeException exception) {
			exitCode = 1;
			log.error("event=post_full_resync status=FAILED commitHash={} errorType={}",
					logCommitHash,
					exception.getClass().getSimpleName());
			throw exception;
		}
	}

	@Override
	public int getExitCode() {
		return exitCode;
	}

	private String normalizedCommitHash() {
		String commitHash = properties.getCommitHash();
		if (commitHash == null || commitHash.isBlank()) {
			return "UNSET";
		}
		return commitHash.trim();
	}

	private static String requireText(String field, String value) {
		if (value == null || value.isBlank()) {
			throw new IllegalArgumentException(field + " is required for one-shot resync");
		}
		return value.trim();
	}
}
