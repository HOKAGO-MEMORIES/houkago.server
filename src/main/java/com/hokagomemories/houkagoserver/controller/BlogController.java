package com.hokagomemories.houkagoserver.controller;

import com.hokagomemories.houkagoserver.dto.PostMetadata;
import com.hokagomemories.houkagoserver.service.GitHubService;
import com.hokagomemories.houkagoserver.service.JsonGenerationService;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class BlogController {

    private final GitHubService gitHubService;
    private final JsonGenerationService jsonGenerationService;

    @PostMapping("/generate-json")
    public ResponseEntity<String> generateJson(@RequestBody Map<String, String> request) throws IOException {
        String blogRoot = request.get("blogRoot");
        if (blogRoot == null || blogRoot.isBlank()) {
            return ResponseEntity.badRequest().body("Missing 'blogRoot' in request body");
        }

        List<PostMetadata> blogPosts = gitHubService.getPostsList("blog");
        List<PostMetadata> psPosts = gitHubService.getPostsList("ps");

        jsonGenerationService.generateFiles(blogRoot, blogPosts, psPosts);

        return ResponseEntity.ok("JSON files generate successfully");
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
