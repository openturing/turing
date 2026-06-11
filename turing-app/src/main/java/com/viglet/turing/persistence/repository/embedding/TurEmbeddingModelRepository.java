package com.viglet.turing.persistence.repository.embedding;

import java.util.List;
import java.util.Optional;

import org.jetbrains.annotations.NotNull;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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

	/** Agent-import fallback by modelName. @since 2026.2.8 */
	Optional<TurEmbeddingModel> findByModelNameIgnoreCase(String modelName);

	@CacheEvict(value = { "turEmbeddingModelfindAll", "turEmbeddingModelfindById" }, allEntries = true)
	@NotNull
	@Override
	<S extends TurEmbeddingModel> S save(@NotNull S entity);

	@Modifying
	@Query("delete from TurEmbeddingModel em where em.id = ?1")
	@CacheEvict(value = { "turEmbeddingModelfindAll", "turEmbeddingModelfindById" }, allEntries = true)
	void delete(String id);

    /**
     * T275 / §XIV.5.1 — instances visible to a tenant: its own ({@code tenantId = :tenantId})
     * plus the platform-provided global pool ({@code tenantId IS NULL}).
     */
    @Query("select e from TurEmbeddingModel e where e.tenantId = :tenantId or e.tenantId is null")
    java.util.List<TurEmbeddingModel> findVisibleToTenant(@Param("tenantId") String tenantId);
}
