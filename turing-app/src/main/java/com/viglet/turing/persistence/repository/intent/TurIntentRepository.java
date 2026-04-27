package com.viglet.turing.persistence.repository.intent;

import java.util.List;
import java.util.Optional;

import org.jetbrains.annotations.NotNull;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import com.viglet.turing.persistence.model.intent.TurIntent;

/**
 * @author Alexandre Oliveira
 * @since 2026.1.17
 */
public interface TurIntentRepository extends JpaRepository<TurIntent, String> {
    String FIND_ALL = "turIntentFindAll";
    String FIND_BY_ID = "turIntentFindById";

    @Override
    @Cacheable(FIND_ALL)
    @NotNull
    List<TurIntent> findAll();

    @Override
    @Cacheable(FIND_BY_ID)
    @NotNull
    Optional<TurIntent> findById(@NotNull String id);

    @CacheEvict(value = {FIND_ALL, FIND_BY_ID}, allEntries = true)
    @NotNull
    @Override
    <S extends TurIntent> S save(@NotNull S entity);

    @Modifying
    @Query("delete from TurIntent i where i.id = ?1")
    @CacheEvict(value = {FIND_ALL, FIND_BY_ID}, allEntries = true)
    void delete(String id);

    List<TurIntent> findByEnabledOrderBySortOrderAsc(int enabled);

    List<TurIntent> findByTurAIAgent_IdOrderByTitleAsc(String agentId);

    List<TurIntent> findByTurAIAgent_IdAndEnabledOrderBySortOrderAsc(String agentId, int enabled);
}
