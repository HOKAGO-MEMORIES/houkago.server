package com.houkago.server.content.post.metadata;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.RecordComponent;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.houkago.server.content.post.policy.PostSourceStatus;
import com.houkago.server.content.post.policy.PostSyncStatus;
import com.houkago.server.content.post.policy.PostVisibility;
import com.houkago.server.content.post.policy.PostVisibilityPolicy;

class PostMetadataMapperTest {

	private final PostMetadataMapper mapper = new PostMetadataMapper();

	@Test
	void mapsValidPublishedMetadata() {
		PostMetadataMapping result = mapper.map(validInputBuilder()
				.status("published")
				.build());

		assertThat(result.title()).isEqualTo("A post");
		assertThat(result.slug()).isEqualTo("a-post");
		assertThat(result.date()).isEqualTo(LocalDate.of(2026, 7, 2));
		assertThat(result.description()).isEqualTo("A useful post.");
		assertThat(result.category()).isEqualTo("blog");
		assertThat(result.sourceStatus()).isEqualTo(PostSourceStatus.PUBLISHED);
		assertThat(result.syncStatus()).isEqualTo(PostSyncStatus.ACTIVE);
		assertThat(result.visibility()).isEqualTo(PostVisibility.PUBLIC);
	}

	@Test
	void rejectsMissingRequiredField() {
		assertThatThrownBy(() -> mapper.map(validInputBuilder().title(null).build()))
				.isInstanceOf(InvalidPostMetadataException.class)
				.hasMessageContaining("title")
				.hasMessageContaining("required");
	}

	@Test
	void rejectsBlankRequiredField() {
		assertThatThrownBy(() -> mapper.map(validInputBuilder().slug("  ").build()))
				.isInstanceOf(InvalidPostMetadataException.class)
				.hasMessageContaining("slug")
				.hasMessageContaining("blank");
	}

	@Test
	void rejectsInvalidCategory() {
		assertThatThrownBy(() -> mapper.map(validInputBuilder().category("memo").build()))
				.isInstanceOf(InvalidPostMetadataException.class)
				.hasMessageContaining("category");
	}

	@Test
	void rejectsInvalidStatus() {
		assertThatThrownBy(() -> mapper.map(validInputBuilder().status("private").build()))
				.isInstanceOf(InvalidPostMetadataException.class)
				.hasMessageContaining("status");
	}

	@Test
	void acceptsValidDate() {
		PostMetadataMapping result = mapper.map(validInputBuilder().date("2026-07-02").build());

		assertThat(result.date()).isEqualTo(LocalDate.of(2026, 7, 2));
	}

	@Test
	void rejectsInvalidDate() {
		assertThatThrownBy(() -> mapper.map(validInputBuilder().date("2026-02-30").build()))
				.isInstanceOf(InvalidPostMetadataException.class)
				.hasMessageContaining("date")
				.hasMessageContaining("YYYY-MM-DD");
	}

	@Test
	void acceptsValidUpdatedDate() {
		PostMetadataMapping result = mapper.map(validInputBuilder().updated("2026-07-03").build());

		assertThat(result.updated()).isEqualTo(LocalDate.of(2026, 7, 3));
	}

	@Test
	void rejectsInvalidUpdatedDate() {
		assertThatThrownBy(() -> mapper.map(validInputBuilder().updated("2026/07/03").build()))
				.isInstanceOf(InvalidPostMetadataException.class)
				.hasMessageContaining("updated")
				.hasMessageContaining("YYYY-MM-DD");
	}

	@Test
	void acceptsNullUpdatedDate() {
		PostMetadataMapping result = mapper.map(validInputBuilder().updated(null).build());

		assertThat(result.updated()).isNull();
	}

	@Test
	void draftIsNotPubliclyVisible() {
		PostMetadataMapping result = mapper.map(validInputBuilder().status("draft").build());

		assertThat(result.sourceStatus()).isEqualTo(PostSourceStatus.DRAFT);
		assertThat(result.visibility()).isEqualTo(PostVisibility.PRIVATE);
		assertThat(result.isPubliclyVisible()).isFalse();
	}

	@Test
	void archivedIsNotPubliclyVisible() {
		PostMetadataMapping result = mapper.map(validInputBuilder().status("archived").build());

		assertThat(result.sourceStatus()).isEqualTo(PostSourceStatus.ARCHIVED);
		assertThat(result.visibility()).isEqualTo(PostVisibility.PRIVATE);
		assertThat(result.isPubliclyVisible()).isFalse();
	}

	@Test
	void publishedIsPubliclyVisible() {
		PostMetadataMapping result = mapper.map(validInputBuilder().status("published").build());

		assertThat(result.isPubliclyVisible()).isTrue();
	}

	@Test
	void deletedPostIsNotPubliclyVisible() {
		assertThat(PostVisibilityPolicy.isPubliclyVisible(
				PostSourceStatus.PUBLISHED,
				PostSyncStatus.DELETED,
				PostVisibility.PUBLIC)).isFalse();
	}

	@Test
	void nullTagsBecomeEmptyList() {
		PostMetadataMapping result = mapper.map(validInputBuilder().tags(null).build());

		assertThat(result.tags()).isEmpty();
	}

	@Test
	void featuredNullBecomesFalse() {
		PostMetadataMapping result = mapper.map(validInputBuilder().featured(null).build());

		assertThat(result.featured()).isFalse();
	}

	@Test
	void metadataInputDoesNotContainCommitHashOrChecksum() {
		List<String> componentNames = Arrays.stream(PostMetadataInput.class.getRecordComponents())
				.map(RecordComponent::getName)
				.toList();

		assertThat(componentNames).doesNotContain("commitHash", "commit_hash", "checksum");
	}

	@Test
	void metadataInputDoesNotContainSourceHash() {
		List<String> componentNames = Arrays.stream(PostMetadataInput.class.getRecordComponents())
				.map(RecordComponent::getName)
				.toList();

		assertThat(componentNames).doesNotContain("sourceHash", "source_hash");
	}

	private static TestInputBuilder validInputBuilder() {
		return new TestInputBuilder()
				.title("A post")
				.slug("a-post")
				.date("2026-07-02")
				.description("A useful post.")
				.category("blog")
				.status("published")
				.tags(List.of("blog", "java"))
				.updated(null)
				.thumbnail("./assets/thumbnail.png")
				.series("Houkago")
				.featured(false)
				.platform(null)
				.problemId(null);
	}

	private static class TestInputBuilder {

		private String title;
		private String slug;
		private String date;
		private String description;
		private String category;
		private String status;
		private List<String> tags;
		private String updated;
		private String thumbnail;
		private String series;
		private Boolean featured;
		private String platform;
		private String problemId;

		TestInputBuilder title(String title) {
			this.title = title;
			return this;
		}

		TestInputBuilder slug(String slug) {
			this.slug = slug;
			return this;
		}

		TestInputBuilder date(String date) {
			this.date = date;
			return this;
		}

		TestInputBuilder description(String description) {
			this.description = description;
			return this;
		}

		TestInputBuilder category(String category) {
			this.category = category;
			return this;
		}

		TestInputBuilder status(String status) {
			this.status = status;
			return this;
		}

		TestInputBuilder tags(List<String> tags) {
			this.tags = tags;
			return this;
		}

		TestInputBuilder updated(String updated) {
			this.updated = updated;
			return this;
		}

		TestInputBuilder thumbnail(String thumbnail) {
			this.thumbnail = thumbnail;
			return this;
		}

		TestInputBuilder series(String series) {
			this.series = series;
			return this;
		}

		TestInputBuilder featured(Boolean featured) {
			this.featured = featured;
			return this;
		}

		TestInputBuilder platform(String platform) {
			this.platform = platform;
			return this;
		}

		TestInputBuilder problemId(String problemId) {
			this.problemId = problemId;
			return this;
		}

		PostMetadataInput build() {
			return new PostMetadataInput(
					title,
					slug,
					date,
					description,
					category,
					status,
					tags,
					updated,
					thumbnail,
					series,
					featured,
					platform,
					problemId);
		}
	}
}
