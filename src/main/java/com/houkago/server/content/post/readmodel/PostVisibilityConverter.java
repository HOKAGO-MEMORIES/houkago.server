package com.houkago.server.content.post.readmodel;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import com.houkago.server.content.post.policy.PostVisibility;

@Converter(autoApply = true)
public class PostVisibilityConverter implements AttributeConverter<PostVisibility, String> {

	@Override
	public String convertToDatabaseColumn(PostVisibility attribute) {
		return attribute == null ? null : attribute.databaseValue();
	}

	@Override
	public PostVisibility convertToEntityAttribute(String dbData) {
		return dbData == null ? null : PostVisibility.fromDatabaseValue(dbData);
	}
}
