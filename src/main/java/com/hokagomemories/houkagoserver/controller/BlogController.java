package com.hokagomemories.houkagoserver.controller;

import com.hokagomemories.houkagoserver.dto.PostMetadata;
import com.hokagomemories.houkagoserver.service.GitHubService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class BlogController {

    private final GitHubService gitHubService;

    @GetMapping("/posts/{category}")
    public ResponseEntity<List<PostMetadata>> getPostsList(
            @PathVariable String category) throws Exception {
        if (!category.matches("^(blog|ps)$")) {
            throw new IllegalArgumentException("Invalid category: " + category);
        }
        List<PostMetadata> posts = gitHubService.getPostsList(category);
        return ResponseEntity.ok(posts);
    }


}
