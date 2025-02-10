package com.hokagomemories.houkagoserver.service;

import com.hokagomemories.houkagoserver.config.GitHubApiConfig;
import com.hokagomemories.houkagoserver.dto.GitHubContent;
import com.hokagomemories.houkagoserver.dto.PostMetadata;
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
        String apiUrl = gitHubApiConfig.getApiUrl() + "/contents/" + category;
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
        String apiUrl = gitHubApiConfig.getApiUrl() + "/contents/" + path;
        HttpEntity<String> entity = new HttpEntity<>(createHeaders());

        ResponseEntity<GitHubContent> response = restTemplate.exchange(
                apiUrl,
                HttpMethod.GET,
                entity,
                GitHubContent.class
        );

        String encodedContent = Objects.requireNonNull(response.getBody()).getContent();
        String content = decodeBase64Content(encodedContent);
        return parsePostContent(content, slug);
    }

    private String decodeBase64Content(String encodedContent) {
        String cleanedContent = encodedContent.replaceAll("\\s", "");
        byte[] decodedBytes = Base64.getDecoder().decode(cleanedContent);
        return new String(decodedBytes);
    }

    private String normalizeImagePath(String path) {
        // ./assets/image.png -> assets/image.png
        // ../assets/image.png -> assets/image.png
        // assets/image.png -> assets/image.png
        if (path.startsWith("./")) {
            return path.substring(2);
        }
        if (path.startsWith("../")) {
            return path.substring(3);
        }
        return path;
    }

    private HttpHeaders createHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "token " + gitHubApiConfig.getToken());
        headers.set("Accept", "application/vnd.github.v3+json");
        return headers;
    }

    private PostMetadata parsePostContent(String content, String slug) {
        Pattern pattern = Pattern.compile("---\\n(.*?)\\n---", Pattern.DOTALL);
        Matcher matcher = pattern.matcher(content);

        PostMetadata.PostMetadataBuilder builder = PostMetadata.builder()
                .slug(slug);

        String bodyContent = content;

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
                    case "category":
                        builder.category(value);
                        break;
                    case "thumbnail":
                        builder.thumbnail(normalizeImagePath(value));
                        break;
                }
            }

            bodyContent = content.substring(matcher.end()).trim();
        }

        builder.body(bodyContent);
        return builder.build();
    }
}
