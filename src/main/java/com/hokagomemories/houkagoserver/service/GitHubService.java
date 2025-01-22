package com.hokagomemories.houkagoserver.service;

import com.hokagomemories.houkagoserver.config.GitHubApiConfig;
import com.hokagomemories.houkagoserver.dto.GitHubContent;
import com.hokagomemories.houkagoserver.dto.PostMetadata;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.util.List;

@Service
public class GitHubService {

    private final GitHubApiConfig gitHubApiConfig;
    private final RestTemplate restTemplate;

    public GitHubService() {
        this.gitHubApiConfig = new GitHubApiConfig();
        this.restTemplate = new RestTemplate();
    }

    public List<PostMetadata> getPostsList(String category) throws IOException {
        String apiUrl = gitHubApiConfig.getGithubApiUrl() + "/contents/" + category;
        HttpEntity<String> entity = new HttpEntity<>(createHeaders());

        ResponseEntity<GitHubContent[]> response = restTemplate.exchange(
                apiUrl,
                HttpMethod.GET,
                entity,
                GitHubContent[].class
        );

        List<PostMetadata> posts = new ArrayList<>();
        for (GitHubContent content : Objects.requireNonNull(response.getBody())) {
            if (content.getType().equals("dir")) {
                PostMetadata post = getPost(category, content.getName());
                posts.add(post);
            }
        }

        return posts;
    }

    public PostMetadata getPost(String category, String slug) throws IOException {
        String path = category + "/" + slug + "/" + slug + ".mdx";
        String apiUrl = gitHubApiConfig.getGithubApiUrl() + "/contents/" + path;
        HttpEntity<String> entity = new HttpEntity<>(createHeaders());

        ResponseEntity<GitHubContent> response = restTemplate.exchange(
                apiUrl,
                HttpMethod.GET,
                entity,
                GitHubContent.class
        );

        GitHubContent content = response.getBody();
        if (content == null || content.getContent() == null) {
            throw new FileNotFoundException("Post content not found: " + path);
        }

        String decodedContent = new String(Base64.getDecoder().decode(Objects.requireNonNull(content).getContent()));
        return parsePostContent(decodedContent, slug);
    }

    public byte[] getImage(String category, String slug, String filename) throws IOException {
        String path = category + "/" + slug + "/assets/" + filename;
        String apiUrl = gitHubApiConfig.getGithubApiUrl() + "/contents/" + path;
        HttpEntity<String> entity = new HttpEntity<>(createHeaders());

        ResponseEntity<GitHubContent> response = restTemplate.exchange(
                apiUrl,
                HttpMethod.GET,
                entity,
                GitHubContent.class
        );

        GitHubContent content = response.getBody();
        if (content == null || content.getContent() == null) {
            throw new FileNotFoundException("Image not found: " + path);
        }
        return Base64.getDecoder().decode(Objects.requireNonNull(content).getContent());
    }

    private HttpHeaders createHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "token " + gitHubApiConfig.getGithubToken());
        headers.set("Accept", "application/vnd.github.v3+json");
        return headers;
    }

    private PostMetadata parsePostContent(String content, String slug) {
        Pattern pattern = Pattern.compile("---\\n(.*?)\\n---", Pattern.DOTALL);
        Matcher matcher = pattern.matcher(content);

        PostMetadata.PostMetadataBuilder builder = PostMetadata.builder()
                .slug(slug)
                .content(content);

        if (matcher.find()) {
            String frontMatter = matcher.group(1);
            Pattern kvPattern = Pattern.compile("(\\w+)\\s*:\\s*(.+)");
            Matcher kvMatcher = kvPattern.matcher(frontMatter);

            while (kvMatcher.find()) {
                String key = kvMatcher.group(1);
                String value = kvMatcher.group(2).trim();

                switch (key) {
                    case "title":
                        builder.title(value);
                        break;
                    case "date":
                        builder.date(value);
                        break;
                    case "desc":
                        builder.desc(value);
                        break;
                    case "from":
                        builder.from(value);
                        break;
                    case "thumbnail":
                        builder.thumbnail(value);
                        break;
                }
            }
        }

        return builder.build();
    }
}
