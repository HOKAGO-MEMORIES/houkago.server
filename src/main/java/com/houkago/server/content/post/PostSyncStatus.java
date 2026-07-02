package com.houkago.server.content.post;

public enum PostSyncStatus {
	ACTIVE("active"),
	DELETED("deleted");

	private final String databaseValue;

	PostSyncStatus(String databaseValue) {
		this.databaseValue = databaseValue;
	}

	public String databaseValue() {
		return databaseValue;
	}

	public static PostSyncStatus fromDatabaseValue(String value) {
		for (PostSyncStatus status : values()) {
			if (status.databaseValue.equals(value)) {
				return status;
			}
		}
		throw new IllegalArgumentException("Unknown post sync status: " + value);
	}
}
