package com.hokagomemories.houkagoserver.config;

import io.github.cdimascio.dotenv.Dotenv;
import lombok.Getter;

@Getter
public class GitHubApiConfig {

    private static final Dotenv dotenv = Dotenv.load();
    private final String githubApiUrl;
    private final String githubToken;

    public GitHubApiConfig() {
        githubApiUrl = validateEnv(loadGithubApiUrl());
        githubToken = validateEnv(loadGithubToken());
    }

    private String loadGithubApiUrl() {
        return dotenv.get("GITHUB_API_URL");
    }

    private String loadGithubToken() {
        return dotenv.get("GITHUB_TOKEN");
    }

    private String validateEnv(String env) {
        if (env == null) {
            throw new IllegalArgumentException("GitHub API URL or Token is missing in the .env file");
        }
        return env;
    }
}


