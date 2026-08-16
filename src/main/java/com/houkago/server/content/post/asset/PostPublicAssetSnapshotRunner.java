package com.houkago.server.content.post.asset;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.ExitCodeGenerator;

import com.houkago.server.content.post.readmodel.PostReadModelCandidatePreflight;
import com.houkago.server.content.post.readmodel.PostReadModelPreparedCandidate;
import com.houkago.server.content.post.source.ParsedPostCandidate;
import com.houkago.server.content.post.source.PostSourceCandidateLoader;

public class PostPublicAssetSnapshotRunner implements ApplicationRunner, ExitCodeGenerator {

	private static final Logger log = LoggerFactory.getLogger(PostPublicAssetSnapshotRunner.class);

	private final PostSourceCandidateLoader candidateLoader;
	private final PostReadModelCandidatePreflight candidatePreflight;
	private final PostPublicAssetSnapshotPublisher publisher;
	private final PostPublicAssetSnapshotProperties properties;
	private int exitCode;

	public PostPublicAssetSnapshotRunner(
			PostSourceCandidateLoader candidateLoader,
			PostReadModelCandidatePreflight candidatePreflight,
			PostPublicAssetSnapshotPublisher publisher,
			PostPublicAssetSnapshotProperties properties) {
		this.candidateLoader = Objects.requireNonNull(candidateLoader, "candidateLoader is required");
		this.candidatePreflight = Objects.requireNonNull(candidatePreflight, "candidatePreflight is required");
		this.publisher = Objects.requireNonNull(publisher, "publisher is required");
		this.properties = Objects.requireNonNull(properties, "properties are required");
	}

	@Override
	public void run(ApplicationArguments arguments) {
		String generationId = normalizedGenerationId();
		String action = normalizedAction();
		try {
			Path assetRoot = Path.of(requireText(
					"houkago.assets.publication.asset-root",
					properties.getAssetRoot()));
			String requiredGenerationId = requireText(
					"houkago.assets.publication.generation-id",
					properties.getGenerationId());
			PublicationAction publicationAction = PublicationAction.from(action);
			if (publicationAction == PublicationAction.ACTIVATE) {
				activate(assetRoot, requiredGenerationId);
				return;
			}

			Path postsRoot = Path.of(requireText(
					"houkago.assets.publication.posts-root",
					properties.getPostsRoot()));
			PostPublicAssetSnapshot snapshot = stage(postsRoot, assetRoot, requiredGenerationId);
			if (publicationAction == PublicationAction.PUBLISH) {
				activate(assetRoot, requiredGenerationId);
				log.info("event=post_public_asset_snapshot status=SUCCESS generationId={} publicPostCount={} "
							+ "assetCount={} totalBytes={}",
						snapshot.generationId(),
						snapshot.publicPostCount(),
						snapshot.assetCount(),
						snapshot.totalBytes());
			}
		} catch (RuntimeException exception) {
			exitCode = 1;
			log.error("event=post_public_asset_snapshot status=FAILED action={} generationId={} errorType={}",
					action,
					generationId,
					exception.getClass().getSimpleName());
			throw exception;
		}
	}

	private PostPublicAssetSnapshot stage(Path postsRoot, Path assetRoot, String generationId) {
		List<ParsedPostCandidate> candidates = candidateLoader.load(postsRoot);
		List<PostReadModelPreparedCandidate> preparedCandidates = candidatePreflight.prepareAll(candidates);
		PostPublicAssetSnapshot snapshot = publisher.stage(
				postsRoot,
				assetRoot,
				preparedCandidates,
				generationId);
		log.info("event=post_public_asset_snapshot phase=ASSET_STAGE_SUCCESS status=SUCCESS generationId={} "
					+ "publicPostCount={} assetCount={} totalBytes={} smokeAssetPath={}",
				snapshot.generationId(),
				snapshot.publicPostCount(),
				snapshot.assetCount(),
				snapshot.totalBytes(),
				snapshot.smokeAssetPath() == null ? "NONE" : snapshot.smokeAssetPath());
		return snapshot;
	}

	private void activate(Path assetRoot, String generationId) {
		publisher.activate(assetRoot, generationId);
		log.info("event=post_public_asset_snapshot phase=ASSET_ACTIVATE_SUCCESS status=SUCCESS generationId={}",
				generationId);
	}

	@Override
	public int getExitCode() {
		return exitCode;
	}

	private String normalizedGenerationId() {
		String generationId = properties.getGenerationId();
		return generationId == null || generationId.isBlank() ? "UNSET" : generationId.trim();
	}

	private String normalizedAction() {
		String action = properties.getAction();
		return action == null || action.isBlank() ? "UNSET" : action.trim().toLowerCase();
	}

	private static String requireText(String field, String value) {
		if (value == null || value.isBlank()) {
			throw new IllegalArgumentException(field + " is required for public asset publication");
		}
		return value.trim();
	}

	private enum PublicationAction {
		PUBLISH,
		STAGE,
		ACTIVATE;

		private static PublicationAction from(String value) {
			return switch (value) {
				case "publish" -> PUBLISH;
				case "stage" -> STAGE;
				case "activate" -> ACTIVATE;
				default -> throw new IllegalArgumentException(
						"houkago.assets.publication.action must be publish, stage, or activate");
			};
		}
	}
}
