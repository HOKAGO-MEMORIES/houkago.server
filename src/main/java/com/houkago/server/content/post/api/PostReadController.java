package com.houkago.server.content.post.api;

import org.springframework.data.domain.Page;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.houkago.server.content.post.query.PostReadService;

@RestController
@RequestMapping("/api/posts")
@Profile("!test")
public class PostReadController {

	private final PostReadService postReadService;

	public PostReadController(PostReadService postReadService) {
		this.postReadService = postReadService;
	}

	@GetMapping
	public Page<PostListItemResponse> listPosts(
			@RequestParam(required = false) Integer page,
			@RequestParam(required = false) Integer size) {
		return postReadService.findPublicPosts(page, size);
	}

	@GetMapping("/{slug}")
	public PostDetailResponse getPost(@PathVariable String slug) {
		return postReadService.findPublicPostBySlug(slug)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
	}
}
