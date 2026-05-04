/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 */
package com.viglet.turing.persistence.adapter.sn;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import com.viglet.turing.domain.sn.TurSNSiteMetricAccessDomain;
import com.viglet.turing.domain.sn.TurSNSiteMetricAccessTermDomain;
import com.viglet.turing.persistence.model.sn.TurSNSite;
import com.viglet.turing.persistence.model.sn.metric.TurSNSiteMetricAccess;
import com.viglet.turing.persistence.repository.sn.metric.TurSNSiteMetricAccessRepository;
import com.viglet.turing.persistence.repository.sn.metric.TurSNSiteMetricAccessTerm;

/** Unit tests for {@link TurSNSiteMetricAccessRepositoryAdapter}. */
@ExtendWith(MockitoExtension.class)
class TurSNSiteMetricAccessRepositoryAdapterTest {

    @Mock
    private TurSNSiteMetricAccessRepository turSNSiteMetricAccessRepository;

    private TurSNSiteMetricAccessRepositoryAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new TurSNSiteMetricAccessRepositoryAdapter(turSNSiteMetricAccessRepository,
                Mappers.getMapper(TurSNSiteMetricAccessDomainMapper.class));
    }

    @Test
    void findBySnSiteIdProjectsScalarsAndTargetingRules() {
        TurSNSiteMetricAccess entity = buildAccess("a-1", "site-1", "java", Locale.US, 42L);
        entity.setTargetingRules(new HashSet<>(List.of("ROLE_ADMIN", "DEPARTMENT_ENG")));
        when(turSNSiteMetricAccessRepository.findByTurSNSite(any())).thenReturn(List.of(entity));

        TurSNSiteMetricAccessDomain domain = adapter.findBySnSiteId("site-1").getFirst();

        assertThat(domain.id()).isEqualTo("a-1");
        assertThat(domain.term()).isEqualTo("java");
        assertThat(domain.snSiteId()).isEqualTo("site-1");
        assertThat(domain.numFound()).isEqualTo(42L);
        assertThat(domain.targetingRules()).containsExactlyInAnyOrder("ROLE_ADMIN",
                "DEPARTMENT_ENG");
        assertThat(domain.language()).isEqualTo(Locale.US);
    }

    @Test
    void findLatestSearchesProjectsTermAndAccessDate() {
        TurSNSiteMetricAccessTerm projection = new TurSNSiteMetricAccessTerm("java",
                Instant.parse("2026-05-04T10:00:00Z"));
        Pageable pageable = PageRequest.of(0, 5);
        when(turSNSiteMetricAccessRepository.findLatestSearches(any(), eq("en_US"), eq("alice"),
                eq(pageable))).thenReturn(List.of(projection));

        TurSNSiteMetricAccessTermDomain domain = adapter
                .findLatestSearches("site-1", "en_US", "alice", pageable).getFirst();

        assertThat(domain.term()).isEqualTo("java");
        assertThat(domain.accessDate()).isEqualTo(Instant.parse("2026-05-04T10:00:00Z"));
        assertThat(domain.total()).isZero();
    }

    @Test
    void topTermsProjectsAggregatedStats() {
        TurSNSiteMetricAccessTerm projection = new TurSNSiteMetricAccessTerm("java", 12L, 100.0);
        when(turSNSiteMetricAccessRepository.topTerms(any(), any())).thenReturn(List.of(projection));

        TurSNSiteMetricAccessTermDomain domain = adapter.topTerms("site-1", PageRequest.of(0, 5))
                .getFirst();

        assertThat(domain.term()).isEqualTo("java");
        assertThat(domain.total()).isEqualTo(12L);
        assertThat(domain.numFound()).isEqualTo(100.0);
        assertThat(domain.accessDate()).isNull();
    }

    @Test
    void countTermsByPeriodDelegatesToRepository() {
        when(turSNSiteMetricAccessRepository.countTermsByPeriod(any(), any(), any())).thenReturn(7);

        int result = adapter.countTermsByPeriod("site-1", Instant.EPOCH, Instant.MAX);

        assertThat(result).isEqualTo(7);
        verify(turSNSiteMetricAccessRepository).countTermsByPeriod(
                argThat(s -> "site-1".equals(s.getId())), eq(Instant.EPOCH), eq(Instant.MAX));
    }

    private static TurSNSiteMetricAccess buildAccess(String id, String siteId, String term,
            Locale language, long numFound) {
        TurSNSiteMetricAccess entity = new TurSNSiteMetricAccess();
        entity.setId(id);
        entity.setUserId("alice");
        entity.setAccessDate(Instant.now());
        entity.setTerm(term);
        entity.setLanguage(language);
        entity.setNumFound(numFound);
        TurSNSite site = new TurSNSite();
        site.setId(siteId);
        entity.setTurSNSite(site);
        return entity;
    }
}
