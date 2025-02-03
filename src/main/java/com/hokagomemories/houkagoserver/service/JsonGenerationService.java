package com.hokagomemories.houkagoserver.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hokagomemories.houkagoserver.config.GitHubApiConfig;
import com.hokagomemories.houkagoserver.dto.PostMetadata;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class JsonGenerationService {
    private final ObjectMapper objectMapper;
    private final GitHubApiConfig gitHubApiConfig = new GitHubApiConfig();

    private static final String OUTPUT_DIR = "/.posts";
    private final String BASE_URL = gitHubApiConfig.getGithubImageUrl();

    public JsonGenerationService() {
        this.objectMapper = new ObjectMapper();
    }

    public void generateFiles(String blogRoot, List<PostMetadata> blogPosts, List<PostMetadata> psPosts) throws IOException {
        String normalizeRoot = normalizePath(blogRoot);
        String outputDir = normalizeRoot + OUTPUT_DIR;
        createDirectories(outputDir);
        System.out.println("Directory created at: " + outputDir);

        File blogFile = new File(outputDir + "/blogPosts.json");
        File psFile = new File(outputDir + "/psPosts.json");

        objectMapper.writeValue(blogFile, transformPosts(blogPosts, "blog"));
        objectMapper.writeValue(psFile, transformPosts(psPosts, "ps"));

        System.out.println("Files generated at: " + blogFile.getAbsolutePath());
        System.out.println("Files generated at: " + psFile.getAbsolutePath());

        generateIndexTs(outputDir);
        generateIndexDts(outputDir);
    }

    private String normalizePath(String path) {
        path = path.replace("\\", "/");

        if (path.startsWith("/app/")) {
            path = path.substring(5);
        }

        if (path.contains("/vercel/path0")) {
            path = path.replace("/vercel/path0", "");
        }

        return path;
    }

    private void createDirectories(String outputDir) throws IOException {
        Files.createDirectories(Path.of(outputDir));
    }

    private void generateIndexTs(String outputDir) throws IOException {
        String content = """
                export { default as blogPosts } from './blogPosts.json'
                export { default as psPosts } from './psPosts.json'
                """;

        Files.writeString(Path.of(outputDir + "/index.ts"), content);
    }

    private void generateIndexDts(String outputDir) throws IOException {
        String content = """
                import type { Post } from '../src/types/post.ts'
                
                export type Blog = Post & { body: string }
                export declare const blogPosts: Blog[]
                
                export type PS = Post & { body: string }
                export declare const psPosts: PS[]
                """;

        Files.writeString(Path.of(outputDir + "/index.d.ts"), content);
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
