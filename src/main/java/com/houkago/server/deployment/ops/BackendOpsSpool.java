package com.houkago.server.deployment.ops;

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
import java.time.Duration;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

public class BackendOpsSpool {

	private static final FileAttribute<Set<PosixFilePermission>> JOB_FILE_PERMISSIONS =
			PosixFilePermissions.asFileAttribute(EnumSet.of(
					PosixFilePermission.OWNER_READ,
					PosixFilePermission.OWNER_WRITE,
					PosixFilePermission.GROUP_READ));

	private final ObjectMapper objectMapper;
	private final Clock clock;
	private final Duration workerGracePeriod;
	private final Path temporaryDirectory;
	private final Path incomingDirectory;
	private final List<Path> jobStateDirectories;

	public BackendOpsSpool(ObjectMapper objectMapper, Clock clock, BackendOpsWebhookProperties properties) {
		this.objectMapper = objectMapper;
		this.clock = clock;
		this.workerGracePeriod = requireGracePeriod(properties.getWorkerGracePeriod());
		String spoolRoot = properties.getSpoolRoot();
		if (spoolRoot == null || spoolRoot.isBlank()) {
			throw new IllegalArgumentException("ops spool root must not be blank when reconcile is enabled");
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

	public synchronized BackendOpsResult publish(BackendOpsRequest request) {
		Path finalPath = incomingDirectory.resolve(request.deliveryId() + ".json");
		Path temporaryPath = null;

		try {
			Files.createDirectories(temporaryDirectory);
			Files.createDirectories(incomingDirectory);
			if (jobExists(request.deliveryId())) {
				return result(BackendOpsResult.Status.DUPLICATE, request);
			}

			Instant receivedAt = Instant.now(clock);
			BackendOpsJob job = new BackendOpsJob(
					request.deliveryId(),
					request.revision(),
					receivedAt.toString(),
					receivedAt.plus(workerGracePeriod).toString());
			temporaryPath = Files.createTempFile(
					temporaryDirectory,
					"." + request.deliveryId() + "-",
					".tmp",
					JOB_FILE_PERMISSIONS);
			Files.write(temporaryPath, serialize(job), StandardOpenOption.TRUNCATE_EXISTING);

			try {
				Files.move(temporaryPath, finalPath, StandardCopyOption.ATOMIC_MOVE);
			}
			catch (FileAlreadyExistsException exception) {
				return result(BackendOpsResult.Status.DUPLICATE, request);
			}
			catch (AtomicMoveNotSupportedException exception) {
				throw new BackendOpsSpoolException(
						"The ops spool filesystem does not support atomic publication",
						exception);
			}

			temporaryPath = null;
			return result(BackendOpsResult.Status.ACCEPTED, request);
		}
		catch (BackendOpsSpoolException exception) {
			throw exception;
		}
		catch (IOException exception) {
			throw new BackendOpsSpoolException("Ops reconcile job could not be published", exception);
		}
		finally {
			deleteTemporaryFile(temporaryPath);
		}
	}

	private boolean jobExists(String deliveryId) {
		String fileName = deliveryId + ".json";
		return jobStateDirectories.stream()
				.map(directory -> directory.resolve(fileName))
				.anyMatch(Files::exists);
	}

	private byte[] serialize(BackendOpsJob job) {
		try {
			return objectMapper.writeValueAsBytes(job);
		}
		catch (JsonProcessingException exception) {
			throw new BackendOpsSpoolException("Ops reconcile job could not be serialized", exception);
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
			throw new BackendOpsSpoolException("Ops reconcile temporary file could not be removed", exception);
		}
	}

	private static Duration requireGracePeriod(Duration gracePeriod) {
		if (gracePeriod == null || gracePeriod.isNegative() || gracePeriod.isZero()) {
			throw new IllegalArgumentException("ops worker grace period must be positive");
		}
		return gracePeriod;
	}

	private static BackendOpsResult result(BackendOpsResult.Status status, BackendOpsRequest request) {
		return new BackendOpsResult(status, request.deliveryId(), request.revision());
	}
}
