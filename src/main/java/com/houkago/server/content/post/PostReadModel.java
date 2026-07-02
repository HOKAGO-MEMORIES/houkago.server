package com.houkago.server.content.post;

import java.time.Instant;
import java.time.LocalDate;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;

@Entity
@Table(name = "post_read_models")
public class PostReadModel {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, length = 200)
	private String slug;

	@Column(nullable = false)
	private String title;

	@Column(nullable = false, columnDefinition = "text")
	private String description;

	@Column(nullable = false, length = 64)
	private String category;

	@Column(name = "tags_json", columnDefinition = "text")
	private String tagsJson;

	@Column(name = "post_date", nullable = false)
	private LocalDate postDate;

	@Column(name = "post_updated_date")
	private LocalDate postUpdatedDate;

	@Column(length = 512)
	private String thumbnail;

	private String series;

	@Column(nullable = false)
	private boolean featured;

	@Column(length = 64)
	private String platform;

	@Column(name = "problem_id", length = 128)
	private String problemId;

	@Column(name = "source_repository")
	private String sourceRepository;

	@Column(name = "source_path", nullable = false, length = 512)
	private String sourcePath;

	@Column(name = "source_url", length = 1024)
	private String sourceUrl;

	@Lob
	@Column(name = "raw_body", nullable = false, columnDefinition = "longtext")
	private String rawBody;

	@Column(name = "commit_hash", length = 64)
	private String commitHash;

	@Column(nullable = false, length = 128)
	private String checksum;

	@Column(name = "source_status", nullable = false, length = 32)
	private PostSourceStatus sourceStatus;

	@Column(name = "sync_status", nullable = false, length = 32)
	private PostSyncStatus syncStatus;

	@Column(nullable = false, length = 32)
	private PostVisibility visibility;

	@CreationTimestamp
	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@UpdateTimestamp
	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	@Column(name = "synced_at", nullable = false)
	private Instant syncedAt;

	protected PostReadModel() {
	}

	public Long getId() {
		return id;
	}

	public String getSlug() {
		return slug;
	}

	public void setSlug(String slug) {
		this.slug = slug;
	}

	public String getTitle() {
		return title;
	}

	public void setTitle(String title) {
		this.title = title;
	}

	public String getDescription() {
		return description;
	}

	public void setDescription(String description) {
		this.description = description;
	}

	public String getCategory() {
		return category;
	}

	public void setCategory(String category) {
		this.category = category;
	}

	public String getTagsJson() {
		return tagsJson;
	}

	public void setTagsJson(String tagsJson) {
		this.tagsJson = tagsJson;
	}

	public LocalDate getPostDate() {
		return postDate;
	}

	public void setPostDate(LocalDate postDate) {
		this.postDate = postDate;
	}

	public LocalDate getPostUpdatedDate() {
		return postUpdatedDate;
	}

	public void setPostUpdatedDate(LocalDate postUpdatedDate) {
		this.postUpdatedDate = postUpdatedDate;
	}

	public String getThumbnail() {
		return thumbnail;
	}

	public void setThumbnail(String thumbnail) {
		this.thumbnail = thumbnail;
	}

	public String getSeries() {
		return series;
	}

	public void setSeries(String series) {
		this.series = series;
	}

	public boolean isFeatured() {
		return featured;
	}

	public void setFeatured(boolean featured) {
		this.featured = featured;
	}

	public String getPlatform() {
		return platform;
	}

	public void setPlatform(String platform) {
		this.platform = platform;
	}

	public String getProblemId() {
		return problemId;
	}

	public void setProblemId(String problemId) {
		this.problemId = problemId;
	}

	public String getSourceRepository() {
		return sourceRepository;
	}

	public void setSourceRepository(String sourceRepository) {
		this.sourceRepository = sourceRepository;
	}

	public String getSourcePath() {
		return sourcePath;
	}

	public void setSourcePath(String sourcePath) {
		this.sourcePath = sourcePath;
	}

	public String getSourceUrl() {
		return sourceUrl;
	}

	public void setSourceUrl(String sourceUrl) {
		this.sourceUrl = sourceUrl;
	}

	public String getRawBody() {
		return rawBody;
	}

	public void setRawBody(String rawBody) {
		this.rawBody = rawBody;
	}

	public String getCommitHash() {
		return commitHash;
	}

	public void setCommitHash(String commitHash) {
		this.commitHash = commitHash;
	}

	public String getChecksum() {
		return checksum;
	}

	public void setChecksum(String checksum) {
		this.checksum = checksum;
	}

	public PostSourceStatus getSourceStatus() {
		return sourceStatus;
	}

	public void setSourceStatus(PostSourceStatus sourceStatus) {
		this.sourceStatus = sourceStatus;
	}

	public PostSyncStatus getSyncStatus() {
		return syncStatus;
	}

	public void setSyncStatus(PostSyncStatus syncStatus) {
		this.syncStatus = syncStatus;
	}

	public PostVisibility getVisibility() {
		return visibility;
	}

	public void setVisibility(PostVisibility visibility) {
		this.visibility = visibility;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}

	public Instant getSyncedAt() {
		return syncedAt;
	}

	public void setSyncedAt(Instant syncedAt) {
		this.syncedAt = syncedAt;
	}
}
