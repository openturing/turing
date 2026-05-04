/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 */
package com.viglet.turing.persistence.adapter.sn;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Sort;

import com.viglet.turing.domain.sn.TurSNRankingConditionDomain;
import com.viglet.turing.domain.sn.TurSNRankingExpressionDomain;
import com.viglet.turing.persistence.model.sn.TurSNSite;
import com.viglet.turing.persistence.model.sn.ranking.TurSNRankingCondition;
import com.viglet.turing.persistence.model.sn.ranking.TurSNRankingExpression;
import com.viglet.turing.persistence.repository.sn.ranking.TurSNRankingExpressionRepository;

/** Unit tests for {@link TurSNRankingExpressionRepositoryAdapter}. */
@ExtendWith(MockitoExtension.class)
class TurSNRankingExpressionRepositoryAdapterTest {

    @Mock
    private TurSNRankingExpressionRepository turSNRankingExpressionRepository;

    private TurSNRankingExpressionRepositoryAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new TurSNRankingExpressionRepositoryAdapter(turSNRankingExpressionRepository,
                Mappers.getMapper(TurSNRankingExpressionDomainMapper.class));
    }

    @Test
    void findByIdProjectsScalarsAndConditions() {
        TurSNRankingExpression entity = buildExpression("re-1", "freshness", 1.5f, "site-1");
        TurSNRankingCondition condition = new TurSNRankingCondition();
        condition.setId("rc-1");
        condition.setAttribute("publishedAt");
        condition.setCondition(0);
        condition.setValue("recent");
        Set<TurSNRankingCondition> conditions = new HashSet<>();
        conditions.add(condition);
        entity.setTurSNRankingConditions(conditions);
        when(turSNRankingExpressionRepository.findById("re-1")).thenReturn(Optional.of(entity));

        TurSNRankingExpressionDomain domain = adapter.findById("re-1").orElseThrow();

        assertThat(domain.name()).isEqualTo("freshness");
        assertThat(domain.weight()).isEqualTo(1.5f);
        assertThat(domain.snSiteId()).isEqualTo("site-1");
        assertThat(domain.conditions()).extracting(TurSNRankingConditionDomain::id)
                .containsExactly("rc-1");
    }

    @Test
    void findBySnSiteIdQueriesByStubParentWithSort() {
        when(turSNRankingExpressionRepository.findByTurSNSite(any(Sort.class), any()))
                .thenReturn(Set.of(buildExpression("re-1", "freshness", 1.0f, "site-1")));

        Set<TurSNRankingExpressionDomain> result = adapter.findBySnSiteId("site-1");

        assertThat(result).hasSize(1);
        verify(turSNRankingExpressionRepository).findByTurSNSite(any(Sort.class),
                argThat(s -> "site-1".equals(s.getId())));
    }

    private static TurSNRankingExpression buildExpression(String id, String name, float weight,
            String siteId) {
        TurSNRankingExpression entity = new TurSNRankingExpression();
        entity.setId(id);
        entity.setName(name);
        entity.setWeight(weight);
        TurSNSite site = new TurSNSite();
        site.setId(siteId);
        entity.setTurSNSite(site);
        return entity;
    }
}
