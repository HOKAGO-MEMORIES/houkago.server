package com.houkago.server.content.post.webhook;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Clock;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

public class PostGitHubWebhookSpool {

	private static final FileAttribute<Set<PosixFilePermission>> JOB_FILE_PERMISSIONS =
			PosixFilePermissions.asFileAttribute(EnumSet.of(
					PosixFilePermission.OWNER_READ,
					PosixFilePermission.OWNER_WRITE,
					PosixFilePermission.GROUP_READ));

	private final ObjectMapper objectMapper;
	private final Clock clock;
	private final Path temporaryDirectory;
	private final Path incomingDirectory;
	private final List<Path> jobStateDirectories;

	public PostGitHubWebhookSpool(
			ObjectMapper objectMapper,
			Clock clock,
			PostGitHubWebhookProperties properties) {
		this.objectMapper = objectMapper;
		this.clock = clock;
		String spoolRoot = properties.getSpoolRoot();
		if (spoolRoot == null || spoolRoot.isBlank()) {
			throw new IllegalArgumentException("spool root must not be blank when the webhook is enabled");
		}
		Path normalizedSpoolRoot = Path.of(spoolRoot).toAbsolutePath().normalize();
		this.temporaryDirectory = normalizedSpoolRoot.resolve(".tmp");
		this.incomingDirectory = normalizedSpoolRoot.resolve("incoming");
		this.jobStateDirectories = List.of(
				incomingDirectory,
				normalizedSpoolRoot.resolve("processing"),
				normalizedSpoolRoot.resolve("succeeded"),
				normalizedSpoolRoot.resolve("failed"));
	}

	public synchronized PostGitHubWebhookResult publish(PostGitHubWebhookRequest request) {
		Path finalPath = incomingDirectory.resolve(request.deliveryId() + ".json");
		Path temporaryPath = null;

		try {
			Files.createDirectories(temporaryDirectory);
			Files.createDirectories(incomingDirectory);
			if (jobExists(request.deliveryId())) {
				return result(PostGitHubWebhookResult.Status.DUPLICATE, request);
			}

			PostGitHubWebhookJob job = new PostGitHubWebhookJob(
					request.deliveryId(),
					request.commitSha(),
					Instant.now(clock).toString());
			byte[] jobJson = serialize(job);
			temporaryPath = Files.createTempFile(
				temporaryDirectory,
				"." + request.deliveryId() + "-",
				".tmp",
				JOB_FILE_PERMISSIONS);
			Files.write(temporaryPath, jobJson, StandardOpenOption.TRUNCATE_EXISTING);

			try {
				Files.move(temporaryPath, finalPath, StandardCopyOption.ATOMIC_MOVE);
			}
			catch (FileAlreadyExistsException exception) {
				return result(PostGitHubWebhookResult.Status.DUPLICATE, request);
			}
			catch (AtomicMoveNotSupportedException exception) {
				throw new PostGitHubWebhookSpoolException(
						"The webhook spool filesystem does not support atomic publication",
						exception);
			}

			temporaryPath = null;
			return result(PostGitHubWebhookResult.Status.ACCEPTED, request);
		}
		catch (PostGitHubWebhookSpoolException exception) {
			throw exception;
		}
		catch (IOException exception) {
			throw new PostGitHubWebhookSpoolException("Webhook job could not be published", exception);
		}
		finally {
			deleteTemporaryFile(temporaryPath);
		}
	}

	private boolean jobExists(String deliveryId) {
		String jobFileName = deliveryId + ".json";
		return jobStateDirectories.stream()
				.map(directory -> directory.resolve(jobFileName))
				.anyMatch(Files::exists);
	}

	private byte[] serialize(PostGitHubWebhookJob job) {
		try {
			return objectMapper.writeValueAsBytes(job);
		}
		catch (JsonProcessingException exception) {
			throw new PostGitHubWebhookSpoolException("Webhook job could not be serialized", exception);
		}
	}

	private void deleteTemporaryFile(Path temporaryPath) {
		if (temporaryPath == null) {
			return;
		}
		try {
			Files.deleteIfExists(temporaryPath);
		}
		catch (IOException exception) {
			throw new PostGitHubWebhookSpoolException("Webhook temporary file could not be removed", exception);
		}
	}

	private static PostGitHubWebhookResult result(
			PostGitHubWebhookResult.Status status,
			PostGitHubWebhookRequest request) {
		return new PostGitHubWebhookResult(status, request.deliveryId(), request.commitSha());
	}
}
