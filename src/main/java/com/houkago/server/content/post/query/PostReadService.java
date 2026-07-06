package com.houkago.server.content.post.query;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import com.houkago.server.content.post.policy.PostSourceStatus;
import com.houkago.server.content.post.policy.PostSyncStatus;
import com.houkago.server.content.post.policy.PostVisibility;
import com.houkago.server.content.post.readmodel.PostReadModel;
import com.houkago.server.content.post.readmodel.PostReadModelRepository;
import com.houkago.server.content.post.readmodel.PostReadSummaryProjection;

@Service
@Profile("!test")
public class PostReadService {

	private static final int DEFAULT_PAGE = 0;
	private static final int DEFAULT_SIZE = 20;
	private static final int MAX_SIZE = 50;
	private static final TypeReference<List<String>> TAG_LIST_TYPE = new TypeReference<>() {
	};

	private final PostReadModelRepository repository;
	private final ObjectMapper objectMapper;

	public PostReadService(PostReadModelRepository repository, ObjectMapper objectMapper) {
		this.repository = repository;
		this.objectMapper = objectMapper;
	}

	@Transactional(readOnly = true)
	public Page<PostReadListItem> findPublicPosts(Integer page, Integer size) {
		Pageable pageable = PageRequest.of(normalizePage(page), normalizeSize(size));
		return repository.findPublicPostSummaries(
				PostSourceStatus.PUBLISHED,
				PostSyncStatus.ACTIVE,
				PostVisibility.PUBLIC,
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
				parseTags(projection.tagsJson()),
				projection.thumbnail(),
				projection.series(),
				projection.featured());
	}

	private PostReadDetail toDetail(PostReadModel post) {
		return new PostReadDetail(
				post.getSlug(),
				post.getTitle(),
				post.getDescription(),
				post.getCategory(),
				post.getPostDate(),
				post.getPostUpdatedDate(),
				parseTags(post.getTagsJson()),
				post.getThumbnail(),
				post.getSeries(),
				post.isFeatured(),
				post.getRawBody());
	}

	private List<String> parseTags(String tagsJson) {
		if (tagsJson == null || tagsJson.isBlank()) {
			return List.of();
		}

		try {
			return objectMapper.readValue(tagsJson, TAG_LIST_TYPE);
		} catch (JsonProcessingException exception) {
			throw new IllegalStateException("Failed to parse post tags JSON", exception);
		}
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
}
