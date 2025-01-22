package com.hokagomemories.houkagoserver;

import com.hokagomemories.houkagoserver.dto.PostMetadata;
import com.hokagomemories.houkagoserver.service.GitHubService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import io.github.cdimascio.dotenv.Dotenv;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class GitHubServiceIntegrationTest {

    private GitHubService gitHubService;
    private static final Dotenv dotenv = Dotenv.load();

    @BeforeEach
    void setUp() {
        // 테스트 실행 전 .env 파일 존재 여부 확인
        assertNotNull(dotenv.get("GITHUB_API_URL"), "GITHUB_API_URL is not set in .env file");
        assertNotNull(dotenv.get("GITHUB_TOKEN"), "GITHUB_TOKEN is not set in .env file");

        gitHubService = new GitHubService();
    }

    @Test
    void getPostsList_ShouldReturnBlogPosts() throws IOException {
        // When
        List<PostMetadata> posts = gitHubService.getPostsList("blog");

        // Then
        assertNotNull(posts);
        assertFalse(posts.isEmpty());

        // Verify post structure
        PostMetadata firstPost = posts.getFirst();
        assertNotNull(firstPost.getTitle());
        assertNotNull(firstPost.getDate());
        assertNotNull(firstPost.getSlug());
        assertNotNull(firstPost.getContent());
    }

    @Test
    void getPostsList_ShouldReturnPsPosts() throws IOException {
        // When
        List<PostMetadata> posts = gitHubService.getPostsList("ps");

        // Then
        assertNotNull(posts);
        assertFalse(posts.isEmpty());

        // Verify post structure
        PostMetadata firstPost = posts.getFirst();
        assertNotNull(firstPost.getTitle());
        assertNotNull(firstPost.getDate());
        assertNotNull(firstPost.getSlug());
        assertNotNull(firstPost.getContent());
    }

    @Test
    void getPost_WithValidSlug_ShouldReturnPost() throws IOException {
        // Given
        List<PostMetadata> posts = gitHubService.getPostsList("blog");
        String validSlug = posts.getFirst().getSlug();

        // When
        PostMetadata post = gitHubService.getPost("blog", validSlug);

        // Then
        assertNotNull(post);
        assertEquals(validSlug, post.getSlug());
        assertNotNull(post.getTitle());
        assertNotNull(post.getDate());
        assertNotNull(post.getContent());
    }

    @Test
    void getPost_WithInvalidSlug_ShouldThrowFileNotFoundException() {
        // When & Then
        assertThrows(FileNotFoundException.class, () ->
                gitHubService.getPost("blog", "non-existent-post")
        );
    }

    @Test
    void getImage_WithValidPath_ShouldReturnImageBytes() throws IOException {
        // Given
        List<PostMetadata> posts = gitHubService.getPostsList("blog");
        PostMetadata postWithImage = posts.stream()
                .filter(post -> post.getThumbnail() != null)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No posts with images found"));

        // When
        byte[] imageBytes = gitHubService.getImage("blog", postWithImage.getSlug(), postWithImage.getThumbnail());

        // Then
        assertNotNull(imageBytes);
        assertTrue(imageBytes.length > 0);
    }

    @Test
    void getImage_WithInvalidPath_ShouldThrowFileNotFoundException() {
        // When & Then
        assertThrows(FileNotFoundException.class, () ->
                gitHubService.getImage("blog", "valid-post", "non-existent-image.jpg")
        );
    }

    @Test
    void parsePostContent_ShouldExtractMetadataCorrectly() throws IOException {
        // Given
        List<PostMetadata> posts = gitHubService.getPostsList("blog");
        String validSlug = posts.getFirst().getSlug();

        // When
        PostMetadata post = gitHubService.getPost("blog", validSlug);

        // Then
        assertNotNull(post);
        // Verify all required metadata fields
        assertNotNull(post.getTitle());
        assertNotNull(post.getDate());
        assertNotNull(post.getDesc());
        assertNotNull(post.getSlug());
        assertNotNull(post.getContent());
        // Content should contain both metadata and actual content
        assertTrue(post.getContent().contains("---"));
        assertTrue(post.getContent().length() > post.getTitle().length());
    }

    @Test
    void checkGitHubApiRateLimit() throws IOException {
        // This test verifies we're not hitting rate limits
        // Run multiple queries in succession
        for (int i = 0; i < 5; i++) {
            List<PostMetadata> posts = gitHubService.getPostsList("blog");
            assertNotNull(posts);
            assertFalse(posts.isEmpty());

            // Small delay to avoid hitting rate limits
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
