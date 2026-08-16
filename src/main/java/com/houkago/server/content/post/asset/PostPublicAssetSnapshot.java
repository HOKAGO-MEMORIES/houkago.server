package com.houkago.server.content.post.asset;

import java.nio.file.Path;

public final class PostPublicAssetSnapshot {

	private final Path assetRoot;
	private final Path releaseDirectory;
	private final String generationId;
	private final int publicPostCount;
	private final long assetCount;
	private final long totalBytes;

	PostPublicAssetSnapshot(
			Path assetRoot,
			Path releaseDirectory,
			String generationId,
			int publicPostCount,
			long assetCount,
			long totalBytes) {
		this.assetRoot = assetRoot;
		this.releaseDirectory = releaseDirectory;
		this.generationId = generationId;
		this.publicPostCount = publicPostCount;
		this.assetCount = assetCount;
		this.totalBytes = totalBytes;
	}

	public Path assetRoot() {
		return assetRoot;
	}

	public Path releaseDirectory() {
		return releaseDirectory;
	}

	public String generationId() {
		return generationId;
	}

	public int publicPostCount() {
		return publicPostCount;
	}

	public long assetCount() {
		return assetCount;
	}

	public long totalBytes() {
		return totalBytes;
	}
}
