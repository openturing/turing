package com.viglet.turing.persistence.repository.agent;

import java.util.List;
import java.util.Optional;

import org.jetbrains.annotations.NotNull;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.viglet.turing.persistence.model.agent.TurAIAgent;

public interface TurAIAgentRepository extends JpaRepository<TurAIAgent, String> {

    /**
     * Loads an agent with its 4 {@code @ManyToMany(LAZY)} catalogs and 3
     * {@code @ManyToOne(LAZY)} singletons eagerly via LEFT JOIN FETCH.
     * Required because the associations are LAZY and accessing a lazy proxy
     * on a detached entity throws
     * {@code LazyInitializationException (no session)} even with
     * {@code hibernate.enable_lazy_load_no_trans=true} when the session has
     * already closed. Joining at load time materializes everything as
     * concrete objects that don't need a session for later access.
     *
     * <p>The 3 ManyToOnes ({@code turEmbeddingModelInstance},
     * {@code turStoreInstance}, {@code defaultPersona}) are accessed by every
     * chat turn — each as a separate lazy load before this was added, costing
     * ~50-100ms per round-trip with the temporary session opened by
     * {@code enable_lazy_load_no_trans}. Folding them into the same query
     * removes 3 DB hits per chat turn.
     *
     * <p>{@code DISTINCT} is needed because the cartesian product of the four
     * collection joins inflates row count — Hibernate deduplicates by entity
     * identity but it has to be told to. The ManyToOne joins don't multiply
     * row count further (single row per association) so they're safe to add.
     */
    @Override
    @Query("SELECT DISTINCT a FROM TurAIAgent a"
            + " LEFT JOIN FETCH a.llmInstances"
            + " LEFT JOIN FETCH a.mcpServers"
            + " LEFT JOIN FETCH a.customTools"
            + " LEFT JOIN FETCH a.personas"
            + " LEFT JOIN FETCH a.turEmbeddingModelInstance"
            + " LEFT JOIN FETCH a.turStoreInstance"
            + " LEFT JOIN FETCH a.defaultPersona"
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

    @Modifying
    @Query("delete from TurAIAgent a where a.id = ?1")
    void delete(String id);
}
