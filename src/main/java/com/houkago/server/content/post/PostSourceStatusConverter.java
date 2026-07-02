package com.houkago.server.content.post;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class PostSourceStatusConverter implements AttributeConverter<PostSourceStatus, String> {

	@Override
	public String convertToDatabaseColumn(PostSourceStatus attribute) {
		return attribute == null ? null : attribute.databaseValue();
	}

	@Override
	public PostSourceStatus convertToEntityAttribute(String dbData) {
		return dbData == null ? null : PostSourceStatus.fromDatabaseValue(dbData);
	}
}
