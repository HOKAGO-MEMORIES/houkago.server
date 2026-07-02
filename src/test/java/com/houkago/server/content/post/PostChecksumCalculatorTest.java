package com.houkago.server.content.post;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.RecordComponent;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

class PostChecksumCalculatorTest {

	private final PostChecksumCalculator calculator = new PostChecksumCalculator();

	@Test
	void sameInputGivesSameChecksum() {
		PostChecksumInput input = sampleInputBuilder().build();

		assertThat(calculator.calculate(input)).isEqualTo(calculator.calculate(input));
	}

	@Test
	void rawBodyChangeChangesChecksum() {
		assertChecksumChanges(sampleInputBuilder().rawBody("first body").build(),
				sampleInputBuilder().rawBody("second body").build());
	}

	@Test
	void titleChangeChangesChecksum() {
		assertChecksumChanges(sampleInputBuilder().title("First").build(),
				sampleInputBuilder().title("Second").build());
	}

	@Test
	void descriptionChangeChangesChecksum() {
		assertChecksumChanges(sampleInputBuilder().description("First description").build(),
				sampleInputBuilder().description("Second description").build());
	}

	@Test
	void statusChangeChangesChecksum() {
		assertChecksumChanges(sampleInputBuilder().sourceStatus(PostSourceStatus.PUBLISHED).build(),
				sampleInputBuilder().sourceStatus(PostSourceStatus.DRAFT).build());
	}

	@Test
	void tagsContentChangeChangesChecksum() {
		assertChecksumChanges(sampleInputBuilder().tags(List.of("java", "spring")).build(),
				sampleInputBuilder().tags(List.of("java", "mysql")).build());
	}

	@Test
	void tagsOrderDoesNotChangeChecksum() {
		assertChecksumSame(sampleInputBuilder().tags(List.of("spring", "java")).build(),
				sampleInputBuilder().tags(List.of("java", "spring")).build());
	}

	@Test
	void commitHashIsNotPartOfChecksum() {
		assertThat(componentNames()).doesNotContain("commitHash", "commit_hash");
	}

	@Test
	void sourcePathIsNotPartOfChecksum() {
		assertThat(componentNames()).doesNotContain("sourcePath", "source_path");
	}

	@Test
	void visibilityIsNotPartOfChecksum() {
		assertThat(componentNames()).doesNotContain("visibility");
	}

	@Test
	void syncStatusIsNotPartOfChecksum() {
		assertThat(componentNames()).doesNotContain("syncStatus", "sync_status");
	}

	@Test
	void nullOptionalFieldsAreHandledDeterministically() {
		PostChecksumInput input = sampleInputBuilder()
				.tags(null)
				.updated(null)
				.thumbnail(null)
				.series(null)
				.platform(null)
				.problemId(null)
				.build();

		assertThat(calculator.calculate(input)).isEqualTo(calculator.calculate(input));
	}

	@Test
	void nullAndBlankOptionalStringsAreDifferent() {
		assertChecksumChanges(sampleInputBuilder().thumbnail(null).build(),
				sampleInputBuilder().thumbnail("").build());
	}

	@Test
	void stringsAreNotTrimmedBeforeChecksum() {
		assertChecksumChanges(sampleInputBuilder().title("A post").build(),
				sampleInputBuilder().title(" A post ").build());
	}

	@Test
	void lineEndingCrlfAndLfProduceSameChecksum() {
		assertChecksumSame(sampleInputBuilder().rawBody("a\r\nb\r\n").build(),
				sampleInputBuilder().rawBody("a\nb\n").build());
	}

	@Test
	void standaloneCrAndLfProduceSameChecksum() {
		assertChecksumSame(sampleInputBuilder().rawBody("a\rb\r").build(),
				sampleInputBuilder().rawBody("a\nb\n").build());
	}

	@Test
	void outputIsSha256LowercaseHex() {
		String checksum = calculator.calculate(sampleInputBuilder().build());

		assertThat(checksum).matches("[0-9a-f]{64}");
	}

	@Test
	void checksumInputDoesNotContainExcludedFields() {
		assertThat(componentNames()).doesNotContain(
				"commitHash",
				"sourcePath",
				"checksum",
				"visibility",
				"syncStatus",
				"createdAt",
				"updatedAt",
				"syncedAt");
	}

	@Test
	void canBuildInputFromMetadataMapping() {
		PostMetadataMapping metadata = new PostMetadataMapping(
				"A post",
				"a-post",
				LocalDate.of(2026, 7, 2),
				"A useful post.",
				"blog",
				PostSourceStatus.PUBLISHED,
				PostSyncStatus.ACTIVE,
				PostVisibility.PUBLIC,
				List.of("java", "spring"),
				LocalDate.of(2026, 7, 3),
				"./assets/thumbnail.png",
				"Houkago",
				true,
				null,
				null);

		PostChecksumInput input = PostChecksumInput.from(metadata, "raw body");

		assertThat(input.rawBody()).isEqualTo("raw body");
		assertThat(input.sourceStatus()).isEqualTo(PostSourceStatus.PUBLISHED);
		assertThat(calculator.calculate(input)).matches("[0-9a-f]{64}");
	}

	private void assertChecksumChanges(PostChecksumInput first, PostChecksumInput second) {
		assertThat(calculator.calculate(first)).isNotEqualTo(calculator.calculate(second));
	}

	private void assertChecksumSame(PostChecksumInput first, PostChecksumInput second) {
		assertThat(calculator.calculate(first)).isEqualTo(calculator.calculate(second));
	}

	private static List<String> componentNames() {
		return Arrays.stream(PostChecksumInput.class.getRecordComponents())
				.map(RecordComponent::getName)
				.toList();
	}

	private static TestInputBuilder sampleInputBuilder() {
		return new TestInputBuilder()
				.rawBody("## Hello\n\nBody")
				.title("A post")
				.slug("a-post")
				.date(LocalDate.of(2026, 7, 2))
				.description("A useful post.")
				.category("blog")
				.sourceStatus(PostSourceStatus.PUBLISHED)
				.tags(List.of("java", "spring"))
				.updated(LocalDate.of(2026, 7, 3))
				.thumbnail("./assets/thumbnail.png")
				.series("Houkago")
				.featured(true)
				.platform(null)
				.problemId(null);
	}

	private static class TestInputBuilder {

		private String rawBody;
		private String title;
		private String slug;
		private LocalDate date;
		private String description;
		private String category;
		private PostSourceStatus sourceStatus;
		private List<String> tags;
		private LocalDate updated;
		private String thumbnail;
		private String series;
		private boolean featured;
		private String platform;
		private String problemId;

		TestInputBuilder rawBody(String rawBody) {
			this.rawBody = rawBody;
			return this;
		}

		TestInputBuilder title(String title) {
			this.title = title;
			return this;
		}

		TestInputBuilder slug(String slug) {
			this.slug = slug;
			return this;
		}

		TestInputBuilder date(LocalDate date) {
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

		TestInputBuilder sourceStatus(PostSourceStatus sourceStatus) {
			this.sourceStatus = sourceStatus;
			return this;
		}

		TestInputBuilder tags(List<String> tags) {
			this.tags = tags;
			return this;
		}

		TestInputBuilder updated(LocalDate updated) {
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

		TestInputBuilder featured(boolean featured) {
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

		PostChecksumInput build() {
			return new PostChecksumInput(
					rawBody,
					title,
					slug,
					date,
					description,
					category,
					sourceStatus,
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
