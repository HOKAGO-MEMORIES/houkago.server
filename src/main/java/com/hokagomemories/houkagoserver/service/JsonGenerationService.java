package com.hokagomemories.houkagoserver.service;

import com.hokagomemories.houkagoserver.config.GitHubApiConfig;
import com.hokagomemories.houkagoserver.dto.PostMetadata;
import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class JsonGenerationService {
    private final GitHubApiConfig gitHubApiConfig;
    private final JsonFileService jsonFileService;

    public List<String> generateFiles(List<PostMetadata> blogPosts, List<PostMetadata> psPosts) throws IOException {
        return Stream.of(
                jsonFileService.saveJsonFile("blogPosts.json", transformPosts(blogPosts, "blog")),
                jsonFileService.saveJsonFile("psPosts.json", transformPosts(psPosts, "ps")),
                jsonFileService.saveJsonFile("index.ts", generateIndexTs()),
                jsonFileService.saveJsonFile("index.d.ts", generateIndexDts())
        ).collect(Collectors.toList());
    }

    private String generateIndexTs() throws IOException {
        return """
                export { default as blogPosts } from './blogPosts.json'
                export { default as psPosts } from './psPosts.json'
                """;
    }

    private String generateIndexDts() throws IOException {
        return """
                import type { Post } from '../src/types/post.ts'
                
                export type Blog = Post & { body: string }
                export declare const blogPosts: Blog[]
                
                export type PS = Post & { body: string }
                export declare const psPosts: PS[]
                """;
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
                String.format("![](%s/%s/%s/$2)", gitHubApiConfig.getImageUrl(), category, slug)
        );
    }

    private String transformThumbnailPath(String path, String category, String slug) {
        path = path.replaceAll("^\\./|^\\.\\./", "");
        return String.format("%s/%s/%s/%s", gitHubApiConfig.getImageUrl(), category, slug, path);
    }
}
