package com.houkago.server.content.post.readmodel;

import java.util.List;
import java.util.Objects;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

@Component
@Profile("!asset-sync")
public class PostTagsJsonCodec {

	private static final TypeReference<List<String>> TAG_LIST_TYPE = new TypeReference<>() {
	};

	private final ObjectMapper objectMapper;

	public PostTagsJsonCodec(ObjectMapper objectMapper) {
		this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper is required");
	}

	public String encode(List<String> tags) {
		try {
			return objectMapper.writeValueAsString(tags == null ? List.of() : tags);
		} catch (JsonProcessingException exception) {
			throw new IllegalStateException("Failed to serialize post tags JSON", exception);
		}
	}

	public List<String> decode(String tagsJson) {
		if (tagsJson == null || tagsJson.isBlank()) {
			return List.of();
		}

		try {
			List<String> tags = objectMapper.readValue(tagsJson, TAG_LIST_TYPE);
			return tags == null ? List.of() : List.copyOf(tags);
		} catch (JsonProcessingException exception) {
			throw new IllegalStateException("Failed to parse post tags JSON", exception);
		}
	}
}
