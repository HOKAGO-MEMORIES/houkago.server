package com.houkago.server.content.post.readmodel;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.houkago.server.content.post.policy.PostSourceStatus;
import com.houkago.server.content.post.policy.PostSyncStatus;
import com.houkago.server.content.post.policy.PostVisibility;

public interface PostReadModelRepository extends JpaRepository<PostReadModel, Long> {

	Optional<PostReadModel> findBySlug(String slug);

	Optional<PostReadModel> findBySourcePath(String sourcePath);

	List<PostReadModel> findBySyncStatusAndSourcePathNotIn(
			PostSyncStatus syncStatus,
			Collection<String> sourcePaths);

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

	@Query(
			value = """
					select new com.houkago.server.content.post.readmodel.PostReadSummaryProjection(
						p.slug,
						p.title,
						p.description,
						p.category,
						p.postDate,
						p.postUpdatedDate,
						p.tagsJson,
						p.thumbnail,
						p.series,
						p.featured
					)
					from PostReadModel p
					where p.sourceStatus = :sourceStatus
					and p.syncStatus = :syncStatus
					and p.visibility = :visibility
					and (:featured is null or p.featured = :featured)
					and (:category is null or p.category = :category)
					and (:tag is null or function('json_contains', p.tagsJson, function('json_quote', :tag)) = 1)
					and (
						:searchTerm is null
						or function('instr', p.title, :searchTerm) > 0
						or function('instr', p.description, :searchTerm) > 0
						or function('instr', p.rawBody, :searchTerm) > 0
					)
					order by p.postDate desc, p.id desc
					""",
			countQuery = """
					select count(p)
					from PostReadModel p
					where p.sourceStatus = :sourceStatus
					and p.syncStatus = :syncStatus
					and p.visibility = :visibility
					and (:featured is null or p.featured = :featured)
					and (:category is null or p.category = :category)
					and (:tag is null or function('json_contains', p.tagsJson, function('json_quote', :tag)) = 1)
					and (
						:searchTerm is null
						or function('instr', p.title, :searchTerm) > 0
						or function('instr', p.description, :searchTerm) > 0
						or function('instr', p.rawBody, :searchTerm) > 0
					)
					""")
	Page<PostReadSummaryProjection> findPublicPostSummaries(
			PostSourceStatus sourceStatus,
			PostSyncStatus syncStatus,
			PostVisibility visibility,
			Boolean featured,
			String category,
			String tag,
			String searchTerm,
			Pageable pageable);

	@Query("""
			select p
			from PostReadModel p
			where p.slug = :slug
			and p.sourceStatus = :sourceStatus
			and p.syncStatus = :syncStatus
			and p.visibility = :visibility
			""")
	Optional<PostReadModel> findPublicPostBySlug(
			String slug,
			PostSourceStatus sourceStatus,
			PostSyncStatus syncStatus,
			PostVisibility visibility);
}
