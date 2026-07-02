package com.houkago.server.content.post;

public enum PostSourceStatus {
	DRAFT("draft"),
	PUBLISHED("published"),
	ARCHIVED("archived");

	private final String databaseValue;

	PostSourceStatus(String databaseValue) {
		this.databaseValue = databaseValue;
	}

	public String databaseValue() {
		return databaseValue;
	}

	public static PostSourceStatus fromDatabaseValue(String value) {
		for (PostSourceStatus status : values()) {
			if (status.databaseValue.equals(value)) {
				return status;
			}
		}
		throw new IllegalArgumentException("Unknown post source status: " + value);
	}
}
