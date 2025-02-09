package com.hokagomemories.houkagoserver.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hokagomemories.houkagoserver.config.GitHubApiConfig;
import com.hokagomemories.houkagoserver.dto.PostMetadata;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.springframework.stereotype.Service;

@Service
public class JsonGenerationService {
    private final ObjectMapper objectMapper;
    private final GitHubApiConfig gitHubApiConfig = new GitHubApiConfig();

    private static final String FILES_DIR = "/app/files";
    private final String BASE_URL = gitHubApiConfig.getGithubImageUrl();

    public JsonGenerationService() {
        this.objectMapper = new ObjectMapper();
    }

    public List<String> generateFiles(List<PostMetadata> blogPosts, List<PostMetadata> psPosts) throws IOException {
        Path filesDir = Paths.get(FILES_DIR);
        createDirectories(filesDir);

        return Stream.of(
                generateJsonFile("blogPosts.json", transformPosts(blogPosts, "blog")),
                generateJsonFile("psPosts.json", transformPosts(psPosts, "ps")),
                generateIndexTs(),
                generateIndexDts()
        ).collect(Collectors.toList());
    }

    private void createDirectories(Path filesDir) throws IOException {
        if (!Files.exists(filesDir)) {
            Files.createDirectories(filesDir);
        }
    }

    private String generateJsonFile(String fileName, Object content) throws IOException {
        Path filePath = Paths.get(FILES_DIR, fileName);
        objectMapper.writeValue(filePath.toFile(), content);
        return fileName;
    }

    private String generateIndexTs() throws IOException {
        String fileName = "index.ts";
        String content = """
                export { default as blogPosts } from './blogPosts.json'
                export { default as psPosts } from './psPosts.json'
                """;

        Files.writeString(Path.of(FILES_DIR, fileName), content);
        return fileName;
    }

    private String generateIndexDts() throws IOException {
        String fileName = "index.d.ts";
        String content = """
                import type { Post } from '../src/types/post.ts'
                
                export type Blog = Post & { body: string }
                export declare const blogPosts: Blog[]
                
                export type PS = Post & { body: string }
                export declare const psPosts: PS[]
                """;

        Files.writeString(Paths.get(FILES_DIR, fileName), content);
        return fileName;
    }


    private List<PostMetadata> transformPosts(List<PostMetadata> posts, String category) {
        return posts.stream()
                .map(post -> {
                    String content = transformMarkdownImagePaths(post.getBody(), category, post.getSlug());

                    String permalink = "/" + post.getSlug().replace(category + "/", "");

                    post.setBody(content);
                    post.setPermalink(permalink);

                    if (post.getThumbnail() != null) {
                        post.setThumbnail(transformThumbnailPath(post.getThumbnail(), category, post.getSlug()));
                    }

                    return post;
                })
                .collect(Collectors.toList());
    }

    private String transformMarkdownImagePaths(String content, String category, String slug) {
        return content.replaceAll(
                "!\\[[^]]*]\\((\\./|\\.\\./)?(assets/[^)]+)\\)",
                String.format("![](%s/%s/%s/$2)", BASE_URL, category, slug)
        );
    }

    private String transformThumbnailPath(String path, String category, String slug) {
        path = path.replaceAll("^\\./|^\\.\\./", "");
        return String.format("%s/%s/%s/%s", BASE_URL, category, slug, path);
    }
}
