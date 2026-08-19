package com.houkago.server.content.post.readmodel;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

class PostTagsJsonCodecTest {

	private final PostTagsJsonCodec codec = new PostTagsJsonCodec(new ObjectMapper());

	@Test
	void roundTripsSupportedTagValues() {
		assertRoundTrip(List.of());
		assertRoundTrip(List.of("java"));
		assertRoundTrip(List.of("java", "spring"));
		assertRoundTrip(List.of("한글", "그래프"));
		assertRoundTrip(List.of("quote\"", "backslash\\", "line\nbreak", "tab\tvalue"));
	}

	@Test
	void encodesTheExistingCompactJsonArrayFormat() {
		assertThat(codec.encode(List.of("java", "algorithm")))
				.isEqualTo("[\"java\",\"algorithm\"]");
	}

	@Test
	void decodesRepresentativeExistingStoredJson() {
		assertThat(codec.decode("[\"java\", \"algorithm\", \"그래프\"]"))
				.containsExactly("java", "algorithm", "그래프");
	}

	@Test
	void nullAndBlankStorageValuesDecodeAsEmptyTags() {
		assertThat(codec.decode(null)).isEmpty();
		assertThat(codec.decode("   ")).isEmpty();
	}

	private void assertRoundTrip(List<String> tags) {
		assertThat(codec.decode(codec.encode(tags))).isEqualTo(tags);
	}
}
