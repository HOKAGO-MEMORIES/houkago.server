package com.hokagomemories.houkagoserver.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hokagomemories.houkagoserver.dto.PostMetadata;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class JsonGenerationService {
    private static final String OUTPUT_DIR = ".posts";
    private static final String BASE_URL = "http://localhost:8080/api/posts";
    private final ObjectMapper objectMapper;

    public JsonGenerationService() {
        this.objectMapper = new ObjectMapper();
        createOutputDirectory();
    }

    private void createOutputDirectory() {
        File directory = new File(OUTPUT_DIR);
        if (!directory.exists()) {
            directory.mkdirs();
        }
    }

    public void generateJson(List<PostMetadata> blogPosts, List<PostMetadata> psPosts) throws IOException {
        List<PostMetadata> transformedBlogPosts = transformPosts(blogPosts, "blog");
        List<PostMetadata> transformedPsPosts = transformPosts(psPosts, "ps");

        objectMapper.writeValue(new File(OUTPUT_DIR + "/blogPosts.json"), transformedBlogPosts);
        objectMapper.writeValue(new File(OUTPUT_DIR + "/psPosts.json"), transformedPsPosts);
    }

    private List<PostMetadata> transformPosts(List<PostMetadata> posts, String category) {
        return posts.stream()
                .map(post -> {
                    String content = transformMarkdownImagePaths(post.getContent(), category, post.getSlug());

                    String permalink = "/" + post.getSlug().replace(category + "/", "");

                    post.setContent(content);
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
                "!\\[[^\\]]*\\]\\((\\./|\\.\\./)?(assets/[^)]+)\\)",
                String.format("![](%s/%s/%s/$2)", BASE_URL, category, slug)
        );
    }

    private String transformThumbnailPath(String path, String category, String slug) {
        path = path.replaceAll("^\\./|^\\.\\./", "");
        return String.format("%s/%s/%s/%s", BASE_URL, category, slug, path);
    }
}
