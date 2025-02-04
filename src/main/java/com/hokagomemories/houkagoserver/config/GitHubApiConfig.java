package com.hokagomemories.houkagoserver.config;

import lombok.Getter;

@Getter
public class GitHubApiConfig {

    private final String githubApiUrl;
    private final String githubToken;
    private final String githubImageUrl;
    private final String[] allowedOrigins;

    public GitHubApiConfig() {
        githubApiUrl = validateEnv(loadGithubApiUrl());
        githubToken = validateEnv(loadGithubToken());
        githubImageUrl = validateEnv(loadGithubImageUrl());
        allowedOrigins = loadAllowedOrigins().split(",");
    }

    private String loadGithubApiUrl() {
        return System.getenv("GITHUB_API_URL");
    }

    private String loadGithubToken() {
        return System.getenv("GITHUB_TOKEN");
    }

    private String loadGithubImageUrl() {
        return System.getenv("GITHUB_IMAGE_URL");
    }

    private String loadAllowedOrigins() {
        return validateEnv(System.getenv("ALLOWED_ORIGINS"));
    }

    private String validateEnv(String env) {
        if (env == null) {
            throw new IllegalArgumentException("GitHub API URL or Token is missing in the .env file");
        }
        return env;
    }
}


