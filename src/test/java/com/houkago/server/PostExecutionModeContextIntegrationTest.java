package com.houkago.server;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import javax.sql.DataSource;

import jakarta.persistence.EntityManagerFactory;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

import com.houkago.server.content.post.api.PostReadController;
import com.houkago.server.content.post.asset.PostPublicAssetSnapshotPublisher;
import com.houkago.server.content.post.asset.PostPublicAssetSnapshotRunner;
import com.houkago.server.content.post.asset.PostPublicAssetUrl;
import com.houkago.server.content.post.preparation.PostCandidatePreflight;
import com.houkago.server.content.post.preparation.PostCandidatePreparer;
import com.houkago.server.content.post.query.PostReadService;
import com.houkago.server.content.post.readmodel.PostReadModelAssembler;
import com.houkago.server.content.post.readmodel.PostReadModelCandidateProcessor;
import com.houkago.server.content.post.readmodel.PostReadModelRepository;
import com.houkago.server.content.post.readmodel.PostReadModelRetirementService;
import com.houkago.server.content.post.readmodel.PostReadModelUpsertService;
import com.houkago.server.content.post.source.PostSourceCandidateLoader;
import com.houkago.server.content.post.sync.PostFullResyncService;
import com.houkago.server.content.post.sync.PostOneShotFullResyncRunner;
import com.houkago.server.content.post.webhook.PostGitHubWebhookController;
import com.houkago.server.deployment.webhook.BackendDeployController;

@Testcontainers
class PostExecutionModeContextIntegrationTest {

	@Container
	static final MySQLContainer mysql = new MySQLContainer("mysql:8.4.0");

	@TempDir
	Path temporaryDirectory;

	@Test
	void appAndSyncModesRetainTheirDatabaseBackedBoundaries() throws IOException {
		Path postsRoot = writePublicPost(temporaryDirectory.resolve("posts"));

		try (ConfigurableApplicationContext context = startApplication(
				WebApplicationType.SERVLET,
				new String[] {"docker"},
				databaseArguments())) {
			assertDatabaseBoundary(context);
			assertCommonPreparationBoundary(context);
			assertThat(context.getBeansOfType(PostReadService.class)).hasSize(1);
			assertThat(context.getBeansOfType(PostReadController.class)).hasSize(1);
			assertThat(context.getBeansOfType(PostPublicAssetUrl.class)).hasSize(1);
			assertThat(context.getBeansOfType(PostFullResyncService.class)).hasSize(1);
			assertThat(context.getBeansOfType(PostPublicAssetSnapshotPublisher.class)).isEmpty();
			assertThat(context.getBeansOfType(PostPublicAssetSnapshotRunner.class)).isEmpty();
		}

		String[] syncArguments = concat(
				databaseArguments(),
				"--houkago.resync.enabled=true",
				"--houkago.resync.posts-root=" + postsRoot,
				"--houkago.resync.commit-hash=context-matrix-sync");
		try (ConfigurableApplicationContext context = startApplication(
				WebApplicationType.NONE,
				new String[] {"docker", "sync"},
				syncArguments)) {
			assertDatabaseBoundary(context);
			assertCommonPreparationBoundary(context);
			assertThat(context.getBeansOfType(PostFullResyncService.class)).hasSize(1);
			assertThat(context.getBeansOfType(PostOneShotFullResyncRunner.class)).hasSize(1);
			assertThat(context.getBeansOfType(PostReadService.class)).isEmpty();
			assertThat(context.getBeansOfType(PostReadController.class)).isEmpty();
			assertThat(context.getBeansOfType(PostPublicAssetUrl.class)).isEmpty();
			assertThat(context.getBeansOfType(PostPublicAssetSnapshotPublisher.class)).isEmpty();
			assertThat(context.getBeansOfType(PostPublicAssetSnapshotRunner.class)).isEmpty();
			assertThat(context.getBeansOfType(PostGitHubWebhookController.class)).isEmpty();
			assertThat(context.getBeansOfType(BackendDeployController.class)).isEmpty();
		}
	}

	@Test
	void assetSyncStartsWithoutDatabaseEnvironmentAndPublishesAssets() throws IOException {
		assertAssetSyncBoundary("without-db-env", new String[0]);
	}

	@Test
	void assetSyncIgnoresUnreachableDatabaseConfiguration() throws IOException {
		assertAssetSyncBoundary(
				"unreachable-db",
				new String[] {
					"--spring.datasource.url=jdbc:mysql://127.0.0.1:1/unreachable",
					"--spring.datasource.username=unreachable",
					"--spring.datasource.password=unreachable"
				});
	}

	private void assertAssetSyncBoundary(String generationId, String[] databaseConfiguration) throws IOException {
		Path postsRoot = writePublicPost(temporaryDirectory.resolve("posts-" + generationId));
		Path assetRoot = temporaryDirectory.resolve("assets-" + generationId);
		String[] assetArguments = concat(
				databaseConfiguration,
				"--houkago.assets.publication.posts-root=" + postsRoot,
				"--houkago.assets.publication.asset-root=" + assetRoot,
				"--houkago.assets.publication.generation-id=" + generationId,
				"--houkago.assets.publication.action=publish");

		try (ConfigurableApplicationContext context = startApplication(
				WebApplicationType.NONE,
				new String[] {"docker", "asset-sync"},
				assetArguments)) {
			assertThat(context.getBeansOfType(DataSource.class)).isEmpty();
			assertThat(context.getBeansOfType(EntityManagerFactory.class)).isEmpty();
			assertThat(context.getBeansOfType(PostReadModelRepository.class)).isEmpty();
			assertThat(context.getBeansOfType(PostReadModelAssembler.class)).isEmpty();
			assertThat(context.getBeansOfType(PostReadModelCandidateProcessor.class)).isEmpty();
			assertThat(context.getBeansOfType(PostReadModelUpsertService.class)).isEmpty();
			assertThat(context.getBeansOfType(PostReadModelRetirementService.class)).isEmpty();
			assertThat(context.getBeansOfType(PostFullResyncService.class)).isEmpty();
			assertThat(context.getBeansOfType(PostReadService.class)).isEmpty();
			assertThat(context.getBeansOfType(PostReadController.class)).isEmpty();
			assertThat(context.getBeansOfType(PostPublicAssetUrl.class)).isEmpty();
			assertCommonPreparationBoundary(context);
			assertThat(context.getBeansOfType(PostPublicAssetSnapshotPublisher.class)).hasSize(1);
			assertThat(context.getBeansOfType(PostPublicAssetSnapshotRunner.class)).hasSize(1);
			assertThat(context.getBeansOfType(PostGitHubWebhookController.class)).isEmpty();
			assertThat(context.getBeansOfType(BackendDeployController.class)).isEmpty();
		}

		assertThat(assetRoot.resolve("current")).isSymbolicLink();
		assertThat(assetRoot.resolve("releases")
				.resolve(generationId)
				.resolve("posts")
				.resolve("no-db-post")
				.resolve("example.txt"))
				.hasContent("asset-content");
	}

	private ConfigurableApplicationContext startApplication(
			WebApplicationType webApplicationType,
			String[] profiles,
			String[] arguments) {
		return new SpringApplicationBuilder(Application.class)
				.profiles(profiles)
				.web(webApplicationType)
				.registerShutdownHook(false)
				.run(concat(
						arguments,
						"--server.port=0",
						"--spring.flyway.enabled=true",
						"--houkago.assets.public-origin=https://assets.example.test"));
	}

	private String[] databaseArguments() {
		return new String[] {
			"--spring.datasource.url=" + mysql.getJdbcUrl(),
			"--spring.datasource.username=" + mysql.getUsername(),
			"--spring.datasource.password=" + mysql.getPassword(),
			"--spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver"
		};
	}

	private static void assertDatabaseBoundary(ConfigurableApplicationContext context) {
		assertThat(context.getBeansOfType(DataSource.class)).hasSize(1);
		assertThat(context.getBeansOfType(EntityManagerFactory.class)).hasSize(1);
		assertThat(context.getBeansOfType(PostReadModelRepository.class)).hasSize(1);
		assertThat(context.getBeansOfType(PostReadModelAssembler.class)).hasSize(1);
		assertThat(context.getBeansOfType(PostReadModelCandidateProcessor.class)).hasSize(1);
		assertThat(context.getBeansOfType(PostReadModelUpsertService.class)).hasSize(1);
		assertThat(context.getBeansOfType(PostReadModelRetirementService.class)).hasSize(1);
	}

	private static void assertCommonPreparationBoundary(ConfigurableApplicationContext context) {
		assertThat(context.getBeansOfType(PostSourceCandidateLoader.class)).hasSize(1);
		assertThat(context.getBeansOfType(PostCandidatePreparer.class)).hasSize(1);
		assertThat(context.getBeansOfType(PostCandidatePreflight.class)).hasSize(1);
	}

	private static Path writePublicPost(Path postsRoot) throws IOException {
		Path postDirectory = postsRoot.resolve("blog/no-db-post");
		Path assetsDirectory = postDirectory.resolve("assets");
		Files.createDirectories(assetsDirectory);
		Files.writeString(
				postDirectory.resolve("index.md"),
				"""
						---
						title: No DB Post
						slug: no-db-post
						date: 2026-08-18
						description: Asset sync context fixture.
						category: blog
						status: published
						tags:
						  - spring
						---
						![fixture](./assets/example.txt)
						""",
				StandardCharsets.UTF_8);
		Files.writeString(assetsDirectory.resolve("example.txt"), "asset-content", StandardCharsets.UTF_8);
		return postsRoot;
	}

	private static String[] concat(String[] values, String... additions) {
		String[] result = new String[values.length + additions.length];
		System.arraycopy(values, 0, result, 0, values.length);
		System.arraycopy(additions, 0, result, values.length, additions.length);
		return result;
	}
}
