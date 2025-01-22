package com.hokagomemories.houkagoserver.controller;

import com.hokagomemories.houkagoserver.dto.PostMetadata;
import com.hokagomemories.houkagoserver.service.FileService;
import com.hokagomemories.houkagoserver.service.GitHubService;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
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

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<String> handleIllegalArgumentException(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(e.getMessage());
    }

    @ExceptionHandler(IOException.class)
    public ResponseEntity<String> handleIOException(IOException e) {
        return ResponseEntity.internalServerError().body("Server error occurred");
    }
}
