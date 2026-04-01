package com.viglet.turing.persistence.repository.sn.searchrule;

import java.util.List;
import java.util.Optional;

import org.jetbrains.annotations.NotNull;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.viglet.turing.persistence.model.sn.TurSNSite;
import com.viglet.turing.persistence.model.sn.searchrule.TurSNSiteSearchRule;

/**
 * Repository for {@link TurSNSiteSearchRule} with caching on all query methods.
 * Cache is evicted on save and delete operations.
 *
 * @author Alexandre Oliveira
 * @since 2026.2.4
 */
public interface TurSNSiteSearchRuleRepository extends JpaRepository<TurSNSiteSearchRule, String> {

    String FIND_BY_SITE_WITH_DETAILS = "turSNSiteSearchRuleFindBySiteWithDetails";
    String FIND_BY_SITE_AND_ID = "turSNSiteSearchRuleFindBySiteAndId";
    String FIND_ENABLED_BY_SITE = "turSNSiteSearchRuleFindEnabledBySite";

    @Cacheable(FIND_BY_SITE_WITH_DETAILS)
    @Query("SELECT DISTINCT sr FROM TurSNSiteSearchRule sr " +
           "LEFT JOIN FETCH sr.conditions " +
           "LEFT JOIN FETCH sr.actions " +
           "WHERE sr.turSNSite = :site " +
           "ORDER BY sr.position")
    List<TurSNSiteSearchRule> findByTurSNSiteWithDetails(@Param("site") TurSNSite site);

    @Cacheable(FIND_BY_SITE_AND_ID)
    @Query("SELECT DISTINCT sr FROM TurSNSiteSearchRule sr " +
           "LEFT JOIN FETCH sr.conditions " +
           "LEFT JOIN FETCH sr.actions " +
           "WHERE sr.turSNSite = :site AND sr.id = :id")
    Optional<TurSNSiteSearchRule> findByTurSNSiteAndId(@Param("site") TurSNSite site,
                                                        @Param("id") String id);

    @Cacheable(FIND_ENABLED_BY_SITE)
    @Query("SELECT DISTINCT sr FROM TurSNSiteSearchRule sr " +
           "LEFT JOIN FETCH sr.conditions " +
           "LEFT JOIN FETCH sr.actions " +
           "WHERE sr.turSNSite = :site AND sr.enabled = 1 " +
           "ORDER BY sr.position")
    List<TurSNSiteSearchRule> findEnabledByTurSNSite(@Param("site") TurSNSite site);

    @CacheEvict(value = { FIND_BY_SITE_WITH_DETAILS, FIND_BY_SITE_AND_ID,
            FIND_ENABLED_BY_SITE }, allEntries = true)
    @NotNull
    @Override
    <S extends TurSNSiteSearchRule> S save(@NotNull S entity);

    @CacheEvict(value = { FIND_BY_SITE_WITH_DETAILS, FIND_BY_SITE_AND_ID,
            FIND_ENABLED_BY_SITE }, allEntries = true)
    @Override
    void delete(@NotNull TurSNSiteSearchRule entity);

    @CacheEvict(value = { FIND_BY_SITE_WITH_DETAILS, FIND_BY_SITE_AND_ID,
            FIND_ENABLED_BY_SITE }, allEntries = true)
    @Override
    void deleteById(@NotNull String id);
}
