package com.hokagomemories.houkagoserver.controller;

import com.hokagomemories.houkagoserver.dto.PostMetadata;
import com.hokagomemories.houkagoserver.service.GitHubService;
import com.hokagomemories.houkagoserver.service.JsonGenerationService;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.core.io.Resource;

import java.util.List;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class BlogController {

    private final GitHubService gitHubService;
    private final JsonGenerationService jsonGenerationService;

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
    public ResponseEntity<Resource> downloadFile(@PathVariable String fileName) {
        try {
            String FILES_DIR = "/app/files/";
            Path filePath = Paths.get(FILES_DIR + fileName);
            Resource resource = new FileSystemResource(filePath.toFile());

            if (!resource.exists()) {
                return ResponseEntity.notFound().build();
            }

            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileName + "\"")
                    .body(resource);
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }
}
