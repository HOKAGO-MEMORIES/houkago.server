package com.hokagomemories.houkagoserver.controller;

import com.hokagomemories.houkagoserver.dto.PostMetadata;
import com.hokagomemories.houkagoserver.service.GitHubService;
import com.hokagomemories.houkagoserver.service.JsonFileService;
import com.hokagomemories.houkagoserver.service.JsonGenerationService;
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
    private final JsonGenerationService jsonGenerationService;
    private final JsonFileService jsonFileService;

    @PostMapping("/generate-json")
    public ResponseEntity<List<String>> generateJson() {
        try {
            List<PostMetadata> blogPosts = gitHubService.getPostsList("blog");
            List<PostMetadata> psPosts = gitHubService.getPostsList("ps");

            List<String> fileNames = jsonGenerationService.generateFiles(blogPosts, psPosts);
            return ResponseEntity.ok(fileNames);
        } catch (IOException e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping("/files/{fileName}")
    public ResponseEntity<String> downloadFile(@PathVariable String fileName) {
        return jsonFileService.getJsonFile(fileName)
                .map(file -> ResponseEntity.ok()
                        .contentType(MediaType.APPLICATION_OCTET_STREAM)
                        .body(file))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
