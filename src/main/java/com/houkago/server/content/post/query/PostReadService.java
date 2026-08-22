package com.houkago.server.content.post.query;

import java.util.Locale;
import java.util.Optional;

import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.houkago.server.content.post.policy.PostSourceStatus;
import com.houkago.server.content.post.policy.PostSyncStatus;
import com.houkago.server.content.post.policy.PostVisibility;
import com.houkago.server.content.post.readmodel.PostReadModel;
import com.houkago.server.content.post.readmodel.PostReadModelRepository;
import com.houkago.server.content.post.readmodel.PostReadNavigationProjection;
import com.houkago.server.content.post.readmodel.PostReadSummaryProjection;
import com.houkago.server.content.post.readmodel.PostTagsJsonCodec;

@Service
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class PostReadService {

	private static final int DEFAULT_PAGE = 0;
	private static final int DEFAULT_SIZE = 20;
	private static final int MAX_SIZE = 50;
	private static final Pageable SINGLE_RESULT = PageRequest.of(0, 1);
	private final PostReadModelRepository repository;
	private final PostTagsJsonCodec tagsJsonCodec;

	public PostReadService(PostReadModelRepository repository, PostTagsJsonCodec tagsJsonCodec) {
		this.repository = repository;
		this.tagsJsonCodec = tagsJsonCodec;
	}

	@Transactional(readOnly = true)
	public Page<PostReadListItem> findPublicPosts(
			Integer page,
			Integer size,
			Boolean featured,
			String category,
			String tag,
			String searchQuery) {
		Pageable pageable = PageRequest.of(normalizePage(page), normalizeSize(size));
		return repository.findPublicPostSummaries(
				PostSourceStatus.PUBLISHED,
				PostSyncStatus.ACTIVE,
				PostVisibility.PUBLIC,
				featured,
				category,
				tag,
				normalizeSearchTerm(searchQuery),
				pageable)
				.map(this::toListItem);
	}

	@Transactional(readOnly = true)
	public Optional<PostReadDetail> findPublicPostBySlug(String slug) {
		if (slug == null || slug.isBlank()) {
			return Optional.empty();
		}
		return repository.findPublicPostBySlug(
				slug,
				PostSourceStatus.PUBLISHED,
				PostSyncStatus.ACTIVE,
				PostVisibility.PUBLIC)
				.map(this::toDetail);
	}

	private PostReadListItem toListItem(PostReadSummaryProjection projection) {
		return new PostReadListItem(
				projection.slug(),
				projection.title(),
				projection.description(),
				projection.category(),
				projection.postDate(),
				projection.updated(),
				tagsJsonCodec.decode(projection.tagsJson()),
				projection.thumbnail(),
				projection.series(),
				projection.featured());
	}

	private PostReadDetail toDetail(PostReadModel post) {
		PostReadNavigationItem olderPost = repository.findClosestOlderPublicPost(
				post.getPostDate(),
				post.getId(),
				PostSourceStatus.PUBLISHED,
				PostSyncStatus.ACTIVE,
				PostVisibility.PUBLIC,
				SINGLE_RESULT)
				.stream()
				.findFirst()
				.map(this::toNavigationItem)
				.orElse(null);
		PostReadNavigationItem newerPost = repository.findClosestNewerPublicPost(
				post.getPostDate(),
				post.getId(),
				PostSourceStatus.PUBLISHED,
				PostSyncStatus.ACTIVE,
				PostVisibility.PUBLIC,
				SINGLE_RESULT)
				.stream()
				.findFirst()
				.map(this::toNavigationItem)
				.orElse(null);

		return new PostReadDetail(
				post.getSlug(),
				post.getTitle(),
				post.getDescription(),
				post.getCategory(),
				post.getPostDate(),
				post.getPostUpdatedDate(),
				tagsJsonCodec.decode(post.getTagsJson()),
				post.getThumbnail(),
				post.getSeries(),
				post.isFeatured(),
				post.getPlatform(),
				post.getProblemId(),
				post.getRawBody(),
				newerPost,
				olderPost);
	}

	private PostReadNavigationItem toNavigationItem(PostReadNavigationProjection post) {
		return new PostReadNavigationItem(post.slug(), post.title(), post.postDate());
	}

	private static int normalizePage(Integer page) {
		if (page == null || page < 0) {
			return DEFAULT_PAGE;
		}
		return page;
	}

	private static int normalizeSize(Integer size) {
		if (size == null) {
			return DEFAULT_SIZE;
		}
		if (size < 1) {
			return DEFAULT_SIZE;
		}
		return Math.min(size, MAX_SIZE);
	}

	private static String normalizeSearchTerm(String searchQuery) {
		return searchQuery == null ? null : searchQuery.toLowerCase(Locale.ROOT);
	}
}
