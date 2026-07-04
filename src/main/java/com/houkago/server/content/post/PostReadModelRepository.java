package com.houkago.server.content.post;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PostReadModelRepository extends JpaRepository<PostReadModel, Long> {

	Optional<PostReadModel> findBySlug(String slug);

	Optional<PostReadModel> findBySourcePath(String sourcePath);

	Page<PostReadModel> findBySourceStatusAndSyncStatusAndVisibilityOrderByPostDateDescSlugAsc(
			PostSourceStatus sourceStatus,
			PostSyncStatus syncStatus,
			PostVisibility visibility,
			Pageable pageable);

	Page<PostReadModel> findByCategoryAndSourceStatusAndSyncStatusAndVisibilityOrderByPostDateDescSlugAsc(
			String category,
			PostSourceStatus sourceStatus,
			PostSyncStatus syncStatus,
			PostVisibility visibility,
			Pageable pageable);
}
