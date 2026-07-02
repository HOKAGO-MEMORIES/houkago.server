package com.houkago.server.content.post;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class PostSyncStatusConverter implements AttributeConverter<PostSyncStatus, String> {

	@Override
	public String convertToDatabaseColumn(PostSyncStatus attribute) {
		return attribute == null ? null : attribute.databaseValue();
	}

	@Override
	public PostSyncStatus convertToEntityAttribute(String dbData) {
		return dbData == null ? null : PostSyncStatus.fromDatabaseValue(dbData);
	}
}
