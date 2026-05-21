package com.viglet.turing.persistence.repository.agent;

import java.util.List;
import java.util.Optional;

import org.jetbrains.annotations.NotNull;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.viglet.turing.persistence.model.agent.TurAIAgent;

public interface TurAIAgentRepository extends JpaRepository<TurAIAgent, String> {
    @Override
    @Cacheable("turAIAgentfindAll")
    List<TurAIAgent> findAll();

    /**
     * Loads an agent with its 4 {@code @ManyToMany(LAZY)} catalogs eagerly via
     * LEFT JOIN FETCH. Required because the collections are LAZY but the
     * cached entity (Spring {@code @Cacheable}) is returned detached on cache
     * hits, and accessing a lazy proxy on a detached entity throws
     * {@code LazyInitializationException (no session)} even with
     * {@code hibernate.enable_lazy_load_no_trans=true} when the session has
     * already closed. Joining at load time materializes the collections as
     * concrete sets that don't need a session for later iteration.
     *
     * <p>{@code DISTINCT} is needed because the cartesian product of the four
     * collection joins inflates row count — Hibernate deduplicates by entity
     * identity but it has to be told to.
     */
    @Override
    @Cacheable("turAIAgentfindById")
    @Query("SELECT DISTINCT a FROM TurAIAgent a"
            + " LEFT JOIN FETCH a.llmInstances"
            + " LEFT JOIN FETCH a.mcpServers"
            + " LEFT JOIN FETCH a.customTools"
            + " LEFT JOIN FETCH a.personas"
            + " WHERE a.id = :id")
    @NotNull
    Optional<TurAIAgent> findById(@NotNull @Param("id") String id);

    /** Agent-import fallback by title. @since 2026.2.8 */
    Optional<TurAIAgent> findByTitleIgnoreCase(String title);

    /**
     * Used by the MCP-server delete path to find every agent whose
     * {@code mcpServers} catalog references this server, so the API can
     * remove the join-table row before the server itself is deleted.
     *
     * @since 2026.2.8
     */
    @Query("SELECT a FROM TurAIAgent a JOIN a.mcpServers m WHERE m.id = ?1")
    List<TurAIAgent> findByMcpServerId(String mcpServerId);

    /** Used by the LLM delete path. @since 2026.2.8 */
    @Query("SELECT a FROM TurAIAgent a JOIN a.llmInstances l WHERE l.id = ?1")
    List<TurAIAgent> findByLlmInstanceId(String llmInstanceId);

    /** Used by the custom-tool delete path. @since 2026.2.8 */
    @Query("SELECT a FROM TurAIAgent a JOIN a.customTools t WHERE t.id = ?1")
    List<TurAIAgent> findByCustomToolId(String customToolId);

    /** Used by the persona delete path. @since 2026.2.8 */
    @Query("SELECT a FROM TurAIAgent a JOIN a.personas p WHERE p.id = ?1")
    List<TurAIAgent> findByPersonaId(String personaId);

    @CacheEvict(value = { "turAIAgentfindAll", "turAIAgentfindById" }, allEntries = true)
    @NotNull
    @Override
    <S extends TurAIAgent> S save(@NotNull S entity);

    @Modifying
    @Query("delete from TurAIAgent a where a.id = ?1")
    @CacheEvict(value = { "turAIAgentfindAll", "turAIAgentfindById" }, allEntries = true)
    void delete(String id);
}
