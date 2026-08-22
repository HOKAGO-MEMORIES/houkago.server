package com.houkago.server.content.post.readmodel;

import java.util.Arrays;

import com.fasterxml.jackson.databind.ObjectMapper;

public final class PostReadModelTestFixture {

	private static final PostTagsJsonCodec TAGS_JSON_CODEC = new PostTagsJsonCodec(new ObjectMapper());

	private PostReadModelTestFixture() {
	}

	public static PostReadModel withRawBody(PostReadModel post, String rawBody) {
		post.setRawBody(rawBody);
		return post;
	}

	public static PostReadModel withFeatured(PostReadModel post) {
		post.setFeatured(true);
		return post;
	}

	public static PostReadModel withCategory(PostReadModel post, String category) {
		post.setCategory(category);
		return post;
	}

	public static PostReadModel withTitle(PostReadModel post, String title) {
		post.setTitle(title);
		return post;
	}

	public static PostReadModel withDescription(PostReadModel post, String description) {
		post.setDescription(description);
		return post;
	}

	public static PostReadModel withTags(PostReadModel post, String... tags) {
		post.setTagsJson(TAGS_JSON_CODEC.encode(Arrays.asList(tags)));
		return post;
	}

	public static PostReadModel withProblemMetadata(PostReadModel post, String platform, String problemId) {
		post.setPlatform(platform);
		post.setProblemId(problemId);
		return post;
	}
}
