package com.viglet.turing.persistence.repository.mcp;

import java.util.List;
import java.util.Optional;

import org.jetbrains.annotations.NotNull;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.viglet.turing.persistence.model.mcp.TurMcpServer;

public interface TurMcpServerRepository extends JpaRepository<TurMcpServer, String> {
    @Override
    @Cacheable("turMcpServerfindAll")
    List<TurMcpServer> findAll();

    @Override
    @Cacheable("turMcpServerfindById")
    @NotNull
    Optional<TurMcpServer> findById(@NotNull String id);

    /** Agent-import fallback by title. @since 2026.2.8 */
    Optional<TurMcpServer> findByTitleIgnoreCase(String title);

    @CacheEvict(value = { "turMcpServerfindAll", "turMcpServerfindById" }, allEntries = true)
    @NotNull
    @Override
    <S extends TurMcpServer> S save(@NotNull S entity);

    @Modifying
    @Query("delete from TurMcpServer ms where ms.id = ?1")
    @CacheEvict(value = { "turMcpServerfindAll", "turMcpServerfindById" }, allEntries = true)
    void delete(String id);

    /**
     * T275 / §XIV.5.1 — instances visible to a tenant: its own ({@code tenantId = :tenantId})
     * plus the platform-provided global pool ({@code tenantId IS NULL}).
     */
    @Query("select e from TurMcpServer e where e.tenantId = :tenantId or e.tenantId is null")
    java.util.List<TurMcpServer> findVisibleToTenant(@Param("tenantId") String tenantId);
}
