package com.houkago.server.content.post.api;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.houkago.server.content.post.query.PostReadDetail;
import com.houkago.server.content.post.query.PostReadListItem;
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
		return postReadService.findPublicPosts(page, size)
				.map(PostReadController::toListItemResponse);
	}

	@GetMapping("/{slug}")
	public PostDetailResponse getPost(@PathVariable String slug) {
		return postReadService.findPublicPostBySlug(slug)
				.map(PostReadController::toDetailResponse)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
	}

	private static PostListItemResponse toListItemResponse(PostReadListItem post) {
		return new PostListItemResponse(
				post.slug(),
				post.title(),
				post.description(),
				post.category(),
				post.postDate(),
				post.updated(),
				copyTags(post.tags()),
				post.thumbnail(),
				post.series(),
				post.featured());
	}

	private static PostDetailResponse toDetailResponse(PostReadDetail post) {
		return new PostDetailResponse(
				post.slug(),
				post.title(),
				post.description(),
				post.category(),
				post.postDate(),
				post.updated(),
				copyTags(post.tags()),
				post.thumbnail(),
				post.series(),
				post.featured(),
				post.rawBody());
	}

	private static List<String> copyTags(List<String> tags) {
		return tags == null ? List.of() : List.copyOf(tags);
	}
}
