package com.houkago.server.content.post.asset;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import com.houkago.server.content.post.preparation.PreparedPostCandidate;

@Component
@Profile("asset-sync")
public class PostPublicAssetSnapshotPublisher {

	private static final String ASSETS_DIRECTORY_NAME = "assets";
	private static final String RELEASES_DIRECTORY_NAME = "releases";
	private static final String CURRENT_LINK_NAME = "current";
	private static final String POSTS_DIRECTORY_NAME = "posts";
	private static final Pattern GENERATION_ID_PATTERN = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]{0,127}");
	private static final Pattern MARKDOWN_LINK_PATTERN = Pattern.compile("!?\\[[^]]*]\\(([^)]+)\\)");
	private static final Pattern URI_SCHEME_PATTERN = Pattern.compile("[A-Za-z][A-Za-z0-9+.-]*:.*");

	public PostPublicAssetSnapshot stage(
			Path postsRoot,
			Path assetRoot,
			List<PreparedPostCandidate> candidates,
			String generationId) {
		Objects.requireNonNull(candidates, "candidates are required");
		String requiredGenerationId = requireGenerationId(generationId);
		Path sourceRoot = requireSourceRoot(postsRoot);
		Path publicationRoot = preparePublicationRoot(assetRoot, sourceRoot);
		Path releasesRoot = prepareManagedDirectory(publicationRoot.resolve(RELEASES_DIRECTORY_NAME), "releases root");
		Path releaseDirectory = releasesRoot.resolve(requiredGenerationId).normalize();
		Path stagingDirectory = releasesRoot.resolve(
				"." + requiredGenerationId + ".staging-" + UUID.randomUUID()).normalize();

		if (!releaseDirectory.getParent().equals(releasesRoot)
				|| !stagingDirectory.getParent().equals(releasesRoot)) {
			throw new IllegalArgumentException("generationId escapes releases root: " + generationId);
		}

		try {
			Files.createDirectory(stagingDirectory);
			Path stagingPostsRoot = Files.createDirectory(stagingDirectory.resolve(POSTS_DIRECTORY_NAME));
			SnapshotStats stats = copyPublicAssets(sourceRoot, stagingPostsRoot, candidates);
			validateSnapshot(stagingPostsRoot, stats.assetCount(), stats.totalBytes());
			String smokeAssetPath = findSmokeAssetPath(stagingPostsRoot);

			if (Files.exists(releaseDirectory, LinkOption.NOFOLLOW_LINKS)) {
				assertEquivalentSnapshots(stagingDirectory, releaseDirectory);
				deleteTree(stagingDirectory);
			} else {
				moveAtomically(stagingDirectory, releaseDirectory, false);
			}

			return new PostPublicAssetSnapshot(
					publicationRoot,
					releaseDirectory,
					requiredGenerationId,
					stats.publicPostCount(),
					stats.assetCount(),
					stats.totalBytes(),
					smokeAssetPath);
		} catch (IOException exception) {
			deleteTreeQuietly(stagingDirectory, exception);
			throw new PostPublicAssetPublicationException(
					"Failed to stage public asset snapshot " + requiredGenerationId,
					exception);
		} catch (RuntimeException exception) {
			deleteTreeQuietly(stagingDirectory, exception);
			throw exception;
		}
	}

	public void activate(PostPublicAssetSnapshot snapshot) {
		Objects.requireNonNull(snapshot, "snapshot is required");
		Path assetRoot = snapshot.assetRoot().toAbsolutePath().normalize();
		Path expectedRelease = assetRoot.resolve(RELEASES_DIRECTORY_NAME)
				.resolve(snapshot.generationId())
				.normalize();
		if (!expectedRelease.equals(snapshot.releaseDirectory().toAbsolutePath().normalize())) {
			throw new IllegalArgumentException("snapshot release does not belong to its asset root");
		}
		activate(assetRoot, snapshot.generationId());
	}

	public void activate(Path assetRoot, String generationId) {
		String requiredGenerationId = requireGenerationId(generationId);
		Path publicationRoot = requirePublicationRoot(assetRoot);
		Path releasesRoot = requireManagedDirectory(
				publicationRoot.resolve(RELEASES_DIRECTORY_NAME),
				"releases root");
		Path expectedRelease = releasesRoot.resolve(requiredGenerationId).normalize();
		if (!expectedRelease.getParent().equals(releasesRoot)) {
			throw new IllegalArgumentException("generationId escapes releases root: " + generationId);
		}
		requireManagedDirectory(expectedRelease, "snapshot release");
		Path postsRoot = requireManagedDirectory(expectedRelease.resolve(POSTS_DIRECTORY_NAME), "snapshot posts root");
		try {
			measureTree(postsRoot);
		} catch (IOException exception) {
			throw new PostPublicAssetPublicationException(
					"Failed to validate public asset snapshot " + requiredGenerationId,
					exception);
		}

		Path currentLink = publicationRoot.resolve(CURRENT_LINK_NAME);
		if (Files.exists(currentLink, LinkOption.NOFOLLOW_LINKS) && !Files.isSymbolicLink(currentLink)) {
			throw new PostPublicAssetPublicationException("Current snapshot path must be a symbolic link: " + currentLink);
		}

		Path temporaryLink = publicationRoot.resolve(".current-" + UUID.randomUUID());
		Path relativeTarget = publicationRoot.relativize(expectedRelease);
		try {
			Files.createSymbolicLink(temporaryLink, relativeTarget);
			moveAtomically(temporaryLink, currentLink, true);
		} catch (IOException exception) {
			tryDelete(temporaryLink, exception);
			throw new PostPublicAssetPublicationException(
					"Failed to activate public asset snapshot " + requiredGenerationId,
					exception);
		}
	}

	private static String findSmokeAssetPath(Path postsRoot) throws IOException {
		List<Path> assets = regularFilesRelativeTo(postsRoot);
		if (assets.isEmpty()) {
			return null;
		}
		Path first = assets.getFirst();
		if (first.getNameCount() < 2) {
			throw new PostPublicAssetPublicationException("Snapshot asset path is missing its slug boundary: " + first);
		}
		String slug = first.getName(0).toString();
		String relativePath = first.subpath(1, first.getNameCount()).toString().replace('\\', '/');
		return PostPublicAssetPath.encodedAssetPath(slug, relativePath);
	}

	private static SnapshotStats copyPublicAssets(
			Path sourceRoot,
			Path stagingPostsRoot,
			List<PreparedPostCandidate> candidates) throws IOException {
		List<PreparedPostCandidate> orderedCandidates = candidates.stream()
				.sorted(Comparator.comparing(PreparedPostCandidate::sourcePath))
				.toList();
		Set<String> publishedSlugs = new HashSet<>();
		long assetCount = 0;
		long totalBytes = 0;
		int publicPostCount = 0;

		for (PreparedPostCandidate candidate : orderedCandidates) {
			Objects.requireNonNull(candidate, "candidate is required");
			if (!candidate.metadata().isPubliclyVisible()) {
				continue;
			}

			String slug = PostPublicAssetPath.requirePathSegment("slug", candidate.metadata().slug());
			if (!publishedSlugs.add(slug)) {
				throw new IllegalArgumentException("Duplicate public post slug: " + slug);
			}
			publicPostCount++;

			Path postDirectory = resolvePostDirectory(sourceRoot, candidate.sourcePath());
			Path assetsDirectory = postDirectory.resolve(ASSETS_DIRECTORY_NAME).normalize();
			validateReferences(candidate, postDirectory, assetsDirectory);

			if (!Files.exists(assetsDirectory, LinkOption.NOFOLLOW_LINKS)) {
				continue;
			}
			if (Files.isSymbolicLink(assetsDirectory)
					|| !Files.isDirectory(assetsDirectory, LinkOption.NOFOLLOW_LINKS)) {
				throw new PostPublicAssetPublicationException(
						"Public post assets path must be a real directory: " + assetsDirectory);
			}

			Path realAssetsDirectory = assetsDirectory.toRealPath();
			Path realPostDirectory = postDirectory.toRealPath();
			if (!realAssetsDirectory.startsWith(realPostDirectory)) {
				throw new PostPublicAssetPublicationException(
						"Public post assets escape post directory: " + assetsDirectory);
			}

			Path destinationPostRoot = Files.createDirectory(stagingPostsRoot.resolve(slug));
			CopyStats copied = copyAssetTree(realAssetsDirectory, destinationPostRoot);
			assetCount += copied.assetCount();
			totalBytes += copied.totalBytes();
		}

		return new SnapshotStats(publicPostCount, assetCount, totalBytes);
	}

	private static Path resolvePostDirectory(Path sourceRoot, String sourcePath) throws IOException {
		Path sourceFile = sourceRoot.resolve(sourcePath).normalize();
		if (!sourceFile.startsWith(sourceRoot)) {
			throw new PostPublicAssetPublicationException("Post source path escapes posts root: " + sourcePath);
		}
		assertNoSymbolicLink(sourceRoot, sourceFile);
		if (!Files.isRegularFile(sourceFile, LinkOption.NOFOLLOW_LINKS)) {
			throw new PostPublicAssetPublicationException("Post source file is missing: " + sourceFile);
		}

		Path realSourceFile = sourceFile.toRealPath();
		if (!realSourceFile.startsWith(sourceRoot)) {
			throw new PostPublicAssetPublicationException("Post source file escapes posts root: " + sourceFile);
		}
		return realSourceFile.getParent();
	}

	private static void validateReferences(
			PreparedPostCandidate candidate,
			Path postDirectory,
			Path assetsDirectory) throws IOException {
		Matcher matcher = MARKDOWN_LINK_PATTERN.matcher(candidate.rawBody());
		while (matcher.find()) {
			validateReference(matcher.group(1), postDirectory, assetsDirectory, candidate.sourcePath());
		}
		String thumbnail = candidate.metadata().thumbnail();
		if (thumbnail != null && !thumbnail.isBlank()) {
			validateReference(thumbnail, postDirectory, assetsDirectory, candidate.sourcePath());
		}
	}

	private static void validateReference(
			String rawReference,
			Path postDirectory,
			Path assetsDirectory,
			String sourcePath) throws IOException {
		String reference = rawReference.trim().replaceAll("^<|>$", "");
		if (reference.isEmpty() || reference.startsWith("#") || URI_SCHEME_PATTERN.matcher(reference).matches()) {
			return;
		}
		if (reference.startsWith("/") || Path.of(reference).isAbsolute()) {
			throw new PostPublicAssetPublicationException(
					"Absolute post-local asset reference is not allowed in " + sourcePath + ": " + reference);
		}

		Path resolvedReference = postDirectory.resolve(reference).normalize();
		if (!resolvedReference.startsWith(assetsDirectory)) {
			throw new PostPublicAssetPublicationException(
					"Post-local asset reference must stay within assets/ in " + sourcePath + ": " + reference);
		}
		assertNoSymbolicLink(postDirectory, resolvedReference);
		if (!Files.isRegularFile(resolvedReference, LinkOption.NOFOLLOW_LINKS)) {
			throw new PostPublicAssetPublicationException(
					"Referenced post asset is missing in " + sourcePath + ": " + reference);
		}
		Path realReference = resolvedReference.toRealPath();
		Path realAssetsDirectory = assetsDirectory.toRealPath();
		if (!realReference.startsWith(realAssetsDirectory)) {
			throw new PostPublicAssetPublicationException(
					"Referenced post asset escapes assets/ in " + sourcePath + ": " + reference);
		}
	}

	private static CopyStats copyAssetTree(Path sourceAssetsRoot, Path destinationPostRoot) throws IOException {
		long[] assetCount = {0};
		long[] totalBytes = {0};

		Files.walkFileTree(sourceAssetsRoot, new SimpleFileVisitor<>() {
			@Override
			public FileVisitResult preVisitDirectory(Path directory, BasicFileAttributes attributes) throws IOException {
				if (Files.isSymbolicLink(directory)) {
					throw new PostPublicAssetPublicationException("Symbolic links are not allowed in public assets: " + directory);
				}
				Path relativePath = sourceAssetsRoot.relativize(directory);
				Path destination = destinationPostRoot.resolve(relativePath).normalize();
				assertDestinationInside(destinationPostRoot, destination);
				Files.createDirectories(destination);
				return FileVisitResult.CONTINUE;
			}

			@Override
			public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) throws IOException {
				if (attributes.isSymbolicLink() || Files.isSymbolicLink(file)) {
					throw new PostPublicAssetPublicationException("Symbolic links are not allowed in public assets: " + file);
				}
				if (!attributes.isRegularFile()) {
					throw new PostPublicAssetPublicationException("Only regular files can be public assets: " + file);
				}
				Path relativePath = sourceAssetsRoot.relativize(file);
				Path destination = destinationPostRoot.resolve(relativePath).normalize();
				assertDestinationInside(destinationPostRoot, destination);
				Files.copy(file, destination, StandardCopyOption.COPY_ATTRIBUTES);
				assetCount[0]++;
				totalBytes[0] += attributes.size();
				return FileVisitResult.CONTINUE;
			}
		});

		return new CopyStats(assetCount[0], totalBytes[0]);
	}

	private static void validateSnapshot(Path postsRoot, long expectedAssetCount, long expectedTotalBytes)
			throws IOException {
		CopyStats actual = measureTree(postsRoot);
		if (actual.assetCount() != expectedAssetCount || actual.totalBytes() != expectedTotalBytes) {
			throw new PostPublicAssetPublicationException(
					"Staged snapshot validation failed: expected " + expectedAssetCount + " files / "
							+ expectedTotalBytes + " bytes but found " + actual.assetCount() + " files / "
							+ actual.totalBytes() + " bytes");
		}
	}

	private static CopyStats measureTree(Path root) throws IOException {
		long[] assetCount = {0};
		long[] totalBytes = {0};
		Files.walkFileTree(root, new SimpleFileVisitor<>() {
			@Override
			public FileVisitResult preVisitDirectory(Path directory, BasicFileAttributes attributes) {
				if (Files.isSymbolicLink(directory)) {
					throw new PostPublicAssetPublicationException("Snapshot contains a symbolic link: " + directory);
				}
				return FileVisitResult.CONTINUE;
			}

			@Override
			public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) {
				if (attributes.isSymbolicLink() || Files.isSymbolicLink(file)) {
					throw new PostPublicAssetPublicationException("Snapshot contains a symbolic link: " + file);
				}
				if (!attributes.isRegularFile()) {
					throw new PostPublicAssetPublicationException("Snapshot contains a non-regular file: " + file);
				}
				assetCount[0]++;
				totalBytes[0] += attributes.size();
				return FileVisitResult.CONTINUE;
			}
		});
		return new CopyStats(assetCount[0], totalBytes[0]);
	}

	private static void assertEquivalentSnapshots(Path staged, Path existing) throws IOException {
		if (!Files.isDirectory(existing, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(existing)) {
			throw new PostPublicAssetPublicationException("Existing release is not a safe directory: " + existing);
		}
		List<Path> stagedFiles = regularFilesRelativeTo(staged);
		List<Path> existingFiles = regularFilesRelativeTo(existing);
		if (!stagedFiles.equals(existingFiles)) {
			throw new PostPublicAssetPublicationException("Generation already exists with different asset paths: "
					+ existing.getFileName());
		}
		for (Path relativePath : stagedFiles) {
			if (Files.mismatch(staged.resolve(relativePath), existing.resolve(relativePath)) != -1) {
				throw new PostPublicAssetPublicationException("Generation already exists with different asset bytes: "
						+ existing.getFileName());
			}
		}
	}

	private static List<Path> regularFilesRelativeTo(Path root) throws IOException {
		List<Path> files = new ArrayList<>();
		Files.walkFileTree(root, new SimpleFileVisitor<>() {
			@Override
			public FileVisitResult preVisitDirectory(Path directory, BasicFileAttributes attributes) {
				if (Files.isSymbolicLink(directory)) {
					throw new PostPublicAssetPublicationException("Snapshot contains a symbolic link: " + directory);
				}
				return FileVisitResult.CONTINUE;
			}

			@Override
			public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) {
				if (attributes.isSymbolicLink() || Files.isSymbolicLink(file)) {
					throw new PostPublicAssetPublicationException("Snapshot contains a symbolic link: " + file);
				}
				if (!attributes.isRegularFile()) {
					throw new PostPublicAssetPublicationException("Snapshot contains a non-regular file: " + file);
				}
				files.add(root.relativize(file));
				return FileVisitResult.CONTINUE;
			}
		});
		files.sort(Comparator.comparing(Path::toString));
		return List.copyOf(files);
	}

	private static Path requireSourceRoot(Path postsRoot) {
		Objects.requireNonNull(postsRoot, "postsRoot is required");
		Path normalizedRoot = postsRoot.toAbsolutePath().normalize();
		if (Files.isSymbolicLink(normalizedRoot)
				|| !Files.isDirectory(normalizedRoot, LinkOption.NOFOLLOW_LINKS)) {
			throw new IllegalArgumentException("postsRoot must be a real directory: " + postsRoot);
		}
		try {
			return normalizedRoot.toRealPath();
		} catch (IOException exception) {
			throw new PostPublicAssetPublicationException("Failed to resolve postsRoot " + postsRoot, exception);
		}
	}

	private static Path preparePublicationRoot(Path assetRoot, Path sourceRoot) {
		Objects.requireNonNull(assetRoot, "assetRoot is required");
		Path normalizedRoot = assetRoot.toAbsolutePath().normalize();
		if (Files.isSymbolicLink(normalizedRoot)) {
			throw new IllegalArgumentException("assetRoot must not be a symbolic link: " + assetRoot);
		}
		try {
			Files.createDirectories(normalizedRoot);
			Path realRoot = normalizedRoot.toRealPath();
			if (realRoot.startsWith(sourceRoot) || sourceRoot.startsWith(realRoot)) {
				throw new IllegalArgumentException("postsRoot and assetRoot must not overlap");
			}
			return realRoot;
		} catch (IOException exception) {
			throw new PostPublicAssetPublicationException("Failed to prepare assetRoot " + assetRoot, exception);
		}
	}

	private static Path prepareManagedDirectory(Path directory, String field) {
		if (Files.isSymbolicLink(directory)) {
			throw new PostPublicAssetPublicationException(field + " must not be a symbolic link: " + directory);
		}
		try {
			Files.createDirectories(directory);
			if (!Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)) {
				throw new PostPublicAssetPublicationException(field + " must be a directory: " + directory);
			}
			return directory.toRealPath();
		} catch (IOException exception) {
			throw new PostPublicAssetPublicationException("Failed to prepare " + field + " " + directory, exception);
		}
	}

	private static Path requirePublicationRoot(Path assetRoot) {
		Objects.requireNonNull(assetRoot, "assetRoot is required");
		Path normalizedRoot = assetRoot.toAbsolutePath().normalize();
		if (Files.isSymbolicLink(normalizedRoot)
				|| !Files.isDirectory(normalizedRoot, LinkOption.NOFOLLOW_LINKS)) {
			throw new PostPublicAssetPublicationException("assetRoot must be a real directory: " + assetRoot);
		}
		try {
			return normalizedRoot.toRealPath();
		} catch (IOException exception) {
			throw new PostPublicAssetPublicationException("Failed to resolve assetRoot " + assetRoot, exception);
		}
	}

	private static Path requireManagedDirectory(Path directory, String field) {
		if (Files.isSymbolicLink(directory)
				|| !Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)) {
			throw new PostPublicAssetPublicationException(field + " must be a real directory: " + directory);
		}
		try {
			return directory.toRealPath();
		} catch (IOException exception) {
			throw new PostPublicAssetPublicationException("Failed to resolve " + field + " " + directory, exception);
		}
	}

	private static void assertNoSymbolicLink(Path root, Path target) throws IOException {
		Path normalizedTarget = target.toAbsolutePath().normalize();
		if (!normalizedTarget.startsWith(root)) {
			throw new PostPublicAssetPublicationException("Path escapes allowed root: " + target);
		}
		Path current = root;
		for (Path segment : root.relativize(normalizedTarget)) {
			current = current.resolve(segment);
			if (Files.isSymbolicLink(current)) {
				throw new PostPublicAssetPublicationException("Symbolic links are not allowed in public asset paths: "
						+ current);
			}
		}
	}

	private static void assertDestinationInside(Path destinationRoot, Path destination) {
		if (!destination.startsWith(destinationRoot)) {
			throw new PostPublicAssetPublicationException("Asset destination escapes staging snapshot: " + destination);
		}
	}

	private static String requireGenerationId(String value) {
		if (value == null || !GENERATION_ID_PATTERN.matcher(value).matches()
				|| ".".equals(value) || "..".equals(value)) {
			throw new IllegalArgumentException("generationId must be a safe release path segment");
		}
		return value;
	}

	private static void moveAtomically(Path source, Path destination, boolean replaceExisting) throws IOException {
		try {
			if (replaceExisting) {
				Files.move(source, destination, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
			} else {
				Files.move(source, destination, StandardCopyOption.ATOMIC_MOVE);
			}
		} catch (AtomicMoveNotSupportedException exception) {
			throw new PostPublicAssetPublicationException(
					"Atomic filesystem move is required for public asset snapshots: " + source + " -> " + destination,
					exception);
		}
	}

	private static void deleteTree(Path root) throws IOException {
		if (!Files.exists(root, LinkOption.NOFOLLOW_LINKS)) {
			return;
		}
		Files.walkFileTree(root, new SimpleFileVisitor<>() {
			@Override
			public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) throws IOException {
				Files.delete(file);
				return FileVisitResult.CONTINUE;
			}

			@Override
			public FileVisitResult postVisitDirectory(Path directory, IOException exception) throws IOException {
				if (exception != null) {
					throw exception;
				}
				Files.delete(directory);
				return FileVisitResult.CONTINUE;
			}
		});
	}

	private static void deleteTreeQuietly(Path root, Throwable original) {
		try {
			deleteTree(root);
		} catch (IOException cleanupFailure) {
			original.addSuppressed(cleanupFailure);
		}
	}

	private static void tryDelete(Path path, Throwable original) {
		try {
			Files.deleteIfExists(path);
		} catch (IOException cleanupFailure) {
			original.addSuppressed(cleanupFailure);
		}
	}

	private record CopyStats(long assetCount, long totalBytes) {
	}

	private record SnapshotStats(int publicPostCount, long assetCount, long totalBytes) {
	}
}
