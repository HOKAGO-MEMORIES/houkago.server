package com.hokagomemories.houkagoserver.service;

import com.hokagomemories.houkagoserver.config.GitHubApiConfig;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.io.FileNotFoundException;
import java.io.IOException;

@Service
public class GitHubService {

    private final GitHubApiConfig gitHubApiConfig;
    private final RestTemplate restTemplate;

    public GitHubService() {
        this.gitHubApiConfig = new GitHubApiConfig();
        this.restTemplate = new RestTemplate();
    }

    public String getFileContent(String path) throws IOException {
        String apiUrl = gitHubApiConfig.getGithubApiUrl() + "/contents/" + path;
        String token = gitHubApiConfig.getGithubToken();

        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "token " + token);
        HttpEntity<String> entity = new HttpEntity<>(headers);

        ResponseEntity<String> response = restTemplate.exchange(apiUrl, HttpMethod.GET, entity, String.class);

        if (response.getStatusCode() == HttpStatus.NOT_FOUND) {
            throw new FileNotFoundException("파일이 없습니다.");
        }

        return response.getBody();
    }
}
