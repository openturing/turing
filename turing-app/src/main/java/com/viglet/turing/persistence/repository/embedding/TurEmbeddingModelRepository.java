package com.viglet.turing.persistence.repository.embedding;

import java.util.List;
import java.util.Optional;

import org.jetbrains.annotations.NotNull;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import com.viglet.turing.persistence.model.embedding.TurEmbeddingModel;

/**
 * Repository for Embedding Model.
 *
 * @author Alexandre Oliveira
 * @since 2026.1.14
 */
public interface TurEmbeddingModelRepository extends JpaRepository<TurEmbeddingModel, String> {

	@Override
	@Cacheable("turEmbeddingModelfindAll")
	List<TurEmbeddingModel> findAll();

	@Override
	@Cacheable("turEmbeddingModelfindById")
	@NotNull
	Optional<TurEmbeddingModel> findById(@NotNull String id);

	@CacheEvict(value = { "turEmbeddingModelfindAll", "turEmbeddingModelfindById" }, allEntries = true)
	@NotNull
	@Override
	<S extends TurEmbeddingModel> S save(@NotNull S entity);

	@Modifying
	@Query("delete from TurEmbeddingModel em where em.id = ?1")
	@CacheEvict(value = { "turEmbeddingModelfindAll", "turEmbeddingModelfindById" }, allEntries = true)
	void delete(String id);
}
