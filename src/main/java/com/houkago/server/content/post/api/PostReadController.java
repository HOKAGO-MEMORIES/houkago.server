package com.houkago.server.content.post.api;

import java.util.List;
import java.util.Optional;

import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.houkago.server.content.post.asset.PostPublicAssetUrl;
import com.houkago.server.content.post.query.PostReadDetail;
import com.houkago.server.content.post.query.PostReadListItem;
import com.houkago.server.content.post.query.PostReadNavigationItem;
import com.houkago.server.content.post.query.PostReadService;

@RestController
@RequestMapping("/api/posts")
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class PostReadController {

	private static final int MAX_SEARCH_QUERY_LENGTH = 100;

	private final PostReadService postReadService;
	private final PostPublicAssetUrl publicAssetUrl;

	public PostReadController(PostReadService postReadService, PostPublicAssetUrl publicAssetUrl) {
		this.postReadService = postReadService;
		this.publicAssetUrl = publicAssetUrl;
	}

	@GetMapping
	public Page<PostListItemResponse> listPosts(
			@RequestParam(required = false) Integer page,
			@RequestParam(required = false) Integer size,
			@RequestParam(required = false) Boolean featured,
			@RequestParam(required = false) String category,
			@RequestParam(required = false) Optional<String> tag,
			@RequestParam(required = false) Optional<String> q) {
		if (tag.isPresent() && tag.get().isBlank()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "tag must not be blank");
		}
		String searchQuery = normalizeSearchQuery(q);
		return postReadService.findPublicPosts(page, size, featured, category, tag.orElse(null), searchQuery)
				.map(PostReadController::toListItemResponse);
	}

	@GetMapping("/{slug}")
	public PostDetailResponse getPost(@PathVariable String slug) {
		return postReadService.findPublicPostBySlug(slug)
				.map(this::toDetailResponse)
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

	private PostDetailResponse toDetailResponse(PostReadDetail post) {
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
				post.platform(),
				post.problemId(),
				post.rawBody(),
				publicAssetUrl.baseUrl(post.slug()),
				toNavigationResponse(post.newerPost()),
				toNavigationResponse(post.olderPost()));
	}

	private static PostNavigationResponse toNavigationResponse(PostReadNavigationItem post) {
		if (post == null) {
			return null;
		}
		return new PostNavigationResponse(post.slug(), post.title(), post.postDate());
	}

	private static List<String> copyTags(List<String> tags) {
		return tags == null ? List.of() : List.copyOf(tags);
	}

	private static String normalizeSearchQuery(Optional<String> query) {
		if (query.isEmpty()) {
			return null;
		}

		String normalized = query.get().trim();
		if (normalized.isEmpty()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "q must not be blank");
		}
		if (normalized.length() > MAX_SEARCH_QUERY_LENGTH) {
			throw new ResponseStatusException(
					HttpStatus.BAD_REQUEST,
					"q must not exceed " + MAX_SEARCH_QUERY_LENGTH + " characters");
		}
		return normalized;
	}
}
