package com.houkago.server.content.post.policy;

public enum PostVisibility {
	PUBLIC("public"),
	PRIVATE("private");

	private final String databaseValue;

	PostVisibility(String databaseValue) {
		this.databaseValue = databaseValue;
	}

	public String databaseValue() {
		return databaseValue;
	}

	public static PostVisibility fromDatabaseValue(String value) {
		for (PostVisibility visibility : values()) {
			if (visibility.databaseValue.equals(value)) {
				return visibility;
			}
		}
		throw new IllegalArgumentException("Unknown post visibility: " + value);
	}
}
