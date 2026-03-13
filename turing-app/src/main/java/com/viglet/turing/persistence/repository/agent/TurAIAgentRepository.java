package com.viglet.turing.persistence.repository.agent;

import java.util.List;
import java.util.Optional;

import org.jetbrains.annotations.NotNull;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import com.viglet.turing.persistence.model.agent.TurAIAgent;

public interface TurAIAgentRepository extends JpaRepository<TurAIAgent, String> {
    @Override
    @Cacheable("turAIAgentfindAll")
    List<TurAIAgent> findAll();

    @Override
    @Cacheable("turAIAgentfindById")
    @NotNull
    Optional<TurAIAgent> findById(@NotNull String id);

    @CacheEvict(value = { "turAIAgentfindAll", "turAIAgentfindById" }, allEntries = true)
    @NotNull
    @Override
    <S extends TurAIAgent> S save(@NotNull S entity);

    @Modifying
    @Query("delete from TurAIAgent a where a.id = ?1")
    @CacheEvict(value = { "turAIAgentfindAll", "turAIAgentfindById" }, allEntries = true)
    void delete(String id);
}
