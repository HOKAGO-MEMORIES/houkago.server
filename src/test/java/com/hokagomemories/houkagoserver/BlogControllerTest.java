package com.hokagomemories.houkagoserver;

import com.hokagomemories.houkagoserver.controller.BlogController;
import com.hokagomemories.houkagoserver.dto.PostMetadata;
import com.hokagomemories.houkagoserver.service.FileService;
import com.hokagomemories.houkagoserver.service.GitHubService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BlogControllerTest {

    @Mock
    private GitHubService gitHubService;

    @Mock
    private FileService fileService;

    @InjectMocks
    private BlogController blogController;

    private PostMetadata samplePost;
    private List<PostMetadata> samplePosts;
    private byte[] sampleImage;

    @BeforeEach
    void setUp() {
        samplePost = PostMetadata.builder()
                .title("Test Post")
                .date("2024-01-23")
                .desc("Test Description")
                .from("Test Source")
                .slug("test-post")
                .thumbnail("thumbnail.jpg")
                .content("Test Content")
                .build();

        samplePosts = Arrays.asList(samplePost);
        sampleImage = "Test Image".getBytes();
    }

    @Test
    void getPostsList_ValidCategory_ReturnsPostsList() throws IOException {
        // Given
        when(gitHubService.getPostsList("blog")).thenReturn(samplePosts);

        // When
        ResponseEntity<List<PostMetadata>> response = blogController.getPostsList("blog");

        // Then
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(samplePosts, response.getBody());
        verify(gitHubService).getPostsList("blog");
    }

    @Test
    void getPostsList_InvalidCategory_ThrowsException() {
        // When & Then
        assertThrows(IllegalArgumentException.class,
                () -> blogController.getPostsList("invalid"));
    }

    @Test
    void getPost_ValidRequest_ReturnsPost() throws IOException {
        // Given
        when(gitHubService.getPost("blog", "test-post")).thenReturn(samplePost);

        // When
        ResponseEntity<PostMetadata> response = blogController.getPost("blog", "test-post");

        // Then
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(samplePost, response.getBody());
        verify(gitHubService).getPost("blog", "test-post");
    }

    @Test
    void getImage_ValidRequest_ReturnsImage() throws IOException {
        // Given
        when(gitHubService.getImage("blog", "test-post", "image.jpg")).thenReturn(sampleImage);
        when(fileService.getContentType("image.jpg")).thenReturn("image/jpeg");

        // When
        ResponseEntity<byte[]> response = blogController.getImage("blog", "test-post", "image.jpg");

        // Then
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(MediaType.IMAGE_JPEG, response.getHeaders().getContentType());
        assertArrayEquals(sampleImage, response.getBody());
        verify(gitHubService).getImage("blog", "test-post", "image.jpg");
        verify(fileService).getContentType("image.jpg");
    }

    @Test
    void handleFileNotFoundException_ReturnsNotFound() {
        // Given
        FileNotFoundException ex = new FileNotFoundException("File not found");

        // When
        ResponseEntity<String> response = blogController.handleFileNotFoundException(ex);

        // Then
        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertEquals("File not found", response.getBody());
    }

    @Test
    void handleIllegalArgumentException_ReturnsBadRequest() {
        // Given
        IllegalArgumentException ex = new IllegalArgumentException("Invalid argument");

        // When
        ResponseEntity<String> response = blogController.handleIllegalArgumentException(ex);

        // Then
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("Invalid argument", response.getBody());
    }

    @Test
    void handleIOException_ReturnsInternalServerError() {
        // Given
        IOException ex = new IOException("IO error");

        // When
        ResponseEntity<String> response = blogController.handleIOException(ex);

        // Then
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertEquals("Server error occurred", response.getBody());
    }

    @Test
    void getPost_FileNotFound_ThrowsException() throws IOException {
        // Given
        when(gitHubService.getPost("blog", "non-existent"))
                .thenThrow(new FileNotFoundException("Post not found"));

        // When & Then
        assertThrows(FileNotFoundException.class,
                () -> blogController.getPost("blog", "non-existent"));
    }

    @Test
    void getImage_IOError_ThrowsException() throws IOException {
        // Given
        when(gitHubService.getImage(anyString(), anyString(), anyString()))
                .thenThrow(new IOException("Network error"));

        // When & Then
        assertThrows(IOException.class,
                () -> blogController.getImage("blog", "test-post", "image.jpg"));
    }
}
