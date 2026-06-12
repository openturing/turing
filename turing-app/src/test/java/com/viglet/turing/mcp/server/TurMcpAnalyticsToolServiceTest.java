/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.mcp.server;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.viglet.turing.persistence.model.sn.TurSNSite;
import com.viglet.turing.persistence.repository.sn.TurSNSiteRepository;
import com.viglet.turing.persistence.repository.sn.metric.TurSNSiteMetricAccessRepository;
import com.viglet.turing.persistence.repository.sn.metric.TurSNSiteMetricAccessTerm;

/**
 * Unit tests for {@link TurMcpAnalyticsToolService} — the T252 search-log
 * analytics MCP tools.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
@ExtendWith(MockitoExtension.class)
class TurMcpAnalyticsToolServiceTest {

    @Mock
    private TurSNSiteRepository turSNSiteRepository;
    @Mock
    private TurSNSiteMetricAccessRepository metricAccessRepository;

    @InjectMocks
    private TurMcpAnalyticsToolService service;

    private TurSNSite site(String name) {
        TurSNSite site = new TurSNSite();
        site.setName(name);
        return site;
    }

    private TurSNSiteMetricAccessTerm term(String t, long total, double avg) {
        return new TurSNSiteMetricAccessTerm(t, total, avg);
    }

    @Test
    void searchMetrics_siteNotFound_returnsError() {
        when(turSNSiteRepository.findByNameIgnoreCase("nope")).thenReturn(Optional.empty());
        assertTrue(service.searchMetrics("nope", "all-time", 10).startsWith("Error"));
    }

    @Test
    void searchMetrics_allTime_usesTopTermsAndCount() {
        TurSNSite wiki = site("wiki");
        when(turSNSiteRepository.findByNameIgnoreCase("wiki")).thenReturn(Optional.of(wiki));
        when(metricAccessRepository.topTerms(eq(wiki), any()))
                .thenReturn(List.of(term("login", 42, 7)));
        when(metricAccessRepository.countTerms(wiki)).thenReturn(120);

        String result = service.searchMetrics("wiki", "all-time", 10);

        assertTrue(result.contains("login"), result);
        assertTrue(result.contains("120"), result);
        assertTrue(result.contains("\"searches\":42"), result);
    }

    @Test
    void searchMetrics_period_usesBetweenDates() {
        TurSNSite wiki = site("wiki");
        when(turSNSiteRepository.findByNameIgnoreCase("wiki")).thenReturn(Optional.of(wiki));
        when(metricAccessRepository.topTermsBetweenDates(eq(wiki), any(Instant.class), any(Instant.class), any()))
                .thenReturn(List.of(term("vpn", 5, 2)));
        when(metricAccessRepository.countTermsByPeriod(eq(wiki), any(Instant.class), any(Instant.class)))
                .thenReturn(7);

        String result = service.searchMetrics("wiki", "this-week", 10);

        assertTrue(result.contains("vpn"), result);
        verify(metricAccessRepository).topTermsBetweenDates(eq(wiki), any(Instant.class), any(Instant.class), any());
    }

    @Test
    void topFailedSearches_returnsZeroResultTerms() {
        TurSNSite wiki = site("wiki");
        when(turSNSiteRepository.findByNameIgnoreCase("wiki")).thenReturn(Optional.of(wiki));
        when(metricAccessRepository.topZeroResultTerms(eq(wiki), any()))
                .thenReturn(List.of(term("foobar widget", 9, 0)));

        String result = service.topFailedSearches("wiki", 10);

        assertTrue(result.contains("foobar widget"), result);
        assertTrue(result.contains("failedSearches"), result);
    }

    @Test
    void topFailedSearches_emptyMessage() {
        TurSNSite wiki = site("wiki");
        when(turSNSiteRepository.findByNameIgnoreCase("wiki")).thenReturn(Optional.of(wiki));
        when(metricAccessRepository.topZeroResultTerms(eq(wiki), any())).thenReturn(List.of());
        assertTrue(service.topFailedSearches("wiki", 10).startsWith("No zero-result searches"));
    }

    @Test
    void contentGaps_keepsOnlyLowCoverageTerms() {
        TurSNSite wiki = site("wiki");
        when(turSNSiteRepository.findByNameIgnoreCase("wiki")).thenReturn(Optional.of(wiki));
        lenient().when(metricAccessRepository.topTerms(eq(wiki), any()))
                .thenReturn(List.of(
                        term("well covered", 100, 25),  // avg 25 → not a gap
                        term("thin topic", 80, 1)));     // avg 1 → gap

        String result = service.contentGaps("wiki", 10);

        assertTrue(result.contains("thin topic"), result);
        assertFalse(result.contains("well covered"), result);
    }

    @Test
    void contentGaps_noneBelowThreshold() {
        TurSNSite wiki = site("wiki");
        when(turSNSiteRepository.findByNameIgnoreCase("wiki")).thenReturn(Optional.of(wiki));
        when(metricAccessRepository.topTerms(eq(wiki), any()))
                .thenReturn(List.of(term("well covered", 100, 25)));
        assertTrue(service.contentGaps("wiki", 10).startsWith("No content gaps"));
    }
}
