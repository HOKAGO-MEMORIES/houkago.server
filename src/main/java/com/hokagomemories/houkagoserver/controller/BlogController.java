package com.hokagomemories.houkagoserver.controller;

import com.hokagomemories.houkagoserver.dto.PostMetadata;
import com.hokagomemories.houkagoserver.service.FileService;
import com.hokagomemories.houkagoserver.service.GitHubService;
import com.hokagomemories.houkagoserver.service.JsonGenerationService;
import java.io.FileNotFoundException;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class BlogController {

    private final GitHubService gitHubService;
    private final FileService fileService;
    private final JsonGenerationService jsonGenerationService;

    @PostMapping("/generate-json")
    public ResponseEntity<String> generateJson() throws IOException {
        List<PostMetadata> blogPosts = gitHubService.getPostsList("blog");
        List<PostMetadata> psPosts = gitHubService.getPostsList("ps");

        jsonGenerationService.generateJson(blogPosts, psPosts);

        return ResponseEntity.ok("JSON files generate successfully");
    }

    @GetMapping("/posts/{category}")
    public ResponseEntity<List<PostMetadata>> getPostsList(
            @PathVariable String category) throws IOException {
        if (!category.matches("^(blog|ps)$")) {
            throw new IllegalArgumentException("Invalid category: " + category);
        }
        List<PostMetadata> posts = gitHubService.getPostsList(category);
        return ResponseEntity.ok(posts);
    }

    @GetMapping("/posts/{category}/{slug}")
    public ResponseEntity<PostMetadata> getPost(
            @PathVariable String category,
            @PathVariable String slug) throws IOException {
        PostMetadata post = gitHubService.getPost(category, slug);
        return ResponseEntity.ok(post);
    }

    @GetMapping("/posts/{category}/{slug}/assets/{filename}")
    public ResponseEntity<byte[]> getImage(
            @PathVariable String category,
            @PathVariable String slug,
            @PathVariable String filename) throws IOException {
        byte[] image = gitHubService.getImage(category, slug, filename);
        String contentType = fileService.getContentType(filename);

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .body(image);
    }

    @ExceptionHandler(FileNotFoundException.class)
    public ResponseEntity<String> handleFileNotFoundException(FileNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ex.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<String> handleIllegalArgumentException(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(e.getMessage());
    }

    @ExceptionHandler(IOException.class)
    public ResponseEntity<String> handleIOException(IOException e) {
        return ResponseEntity.internalServerError().body("Server error occurred");
    }
}
