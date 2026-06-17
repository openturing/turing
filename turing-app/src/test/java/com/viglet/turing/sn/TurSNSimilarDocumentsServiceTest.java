/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.sn;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.viglet.turing.commons.se.similar.TurSESimilarResult;
import com.viglet.turing.commons.sn.field.TurSNFieldName;
import com.viglet.turing.persistence.model.sn.TurSNSite;
import com.viglet.turing.persistence.model.sn.locale.TurSNSiteLocale;
import com.viglet.turing.plugins.se.TurSearchEnginePlugin;
import com.viglet.turing.plugins.se.TurSearchEnginePluginFactory;
import com.viglet.turing.sn.ranking.TurSNHybridRankingService;
import com.viglet.turing.sn.snapshot.TurSNSiteSearchSnapshot;
import com.viglet.turing.sn.snapshot.TurSNSiteSearchSnapshotService;

/**
 * T384 / §XX.4 — unit coverage for the public similar-documents orchestrator:
 * mode auto-selection, the vector-neighbor path, lexical MLT, and the fail-open
 * degradation from VECTOR to MLT.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@ExtendWith(MockitoExtension.class)
class TurSNSimilarDocumentsServiceTest {

    private static final String SITE = "catalog";
    private static final String SEED = "doc-1";

    @Mock
    private TurSNSiteSearchSnapshotService snapshotService;
    @Mock
    private TurSearchEnginePluginFactory pluginFactory;
    @Mock
    private TurSNHybridRankingService hybridRankingService;
    @Mock
    private TurSearchEnginePlugin plugin;

    private TurSNSimilarDocumentsService service;

    @BeforeEach
    void setUp() {
        service = new TurSNSimilarDocumentsService(snapshotService, pluginFactory,
                hybridRankingService);
        lenient().when(pluginFactory.getPluginForSite(any())).thenReturn(plugin);
    }

    private TurSNSite site() {
        TurSNSite site = new TurSNSite();
        site.setId("site-1");
        site.setName(SITE);
        return site;
    }

    private TurSNSiteLocale locale(Locale language) {
        TurSNSiteLocale snLocale = new TurSNSiteLocale();
        snLocale.setLanguage(language);
        return snLocale;
    }

    private void snapshotExists(Locale language) {
        TurSNSiteSearchSnapshot snapshot = new TurSNSiteSearchSnapshot(site(), language, "solr",
                false, List.of(), java.util.Set.of(), Map.of(), Map.of(),
                List.of(locale(language)), List.of());
        when(snapshotService.getSnapshot(eq(SITE), any())).thenReturn(Optional.of(snapshot));
    }

    private static Map<String, Object> doc(String id, String title) {
        return Map.of(TurSNFieldName.ID, id, TurSNFieldName.TITLE, title,
                TurSNFieldName.TYPE, "Page", TurSNFieldName.URL, "/c/" + id);
    }

    // ---- guards -----------------------------------------------------------

    @Test
    void blankIdReturnsEmpty() {
        assertThat(service.findSimilar(SITE, "  ", 10, Locale.ENGLISH, null)).isEmpty();
        verify(snapshotService, never()).getSnapshot(anyString(), any());
    }

    @Test
    void missingSiteReturnsEmpty() {
        when(snapshotService.getSnapshot(eq(SITE), any())).thenReturn(Optional.empty());
        assertThat(service.findSimilar(SITE, SEED, 10, Locale.ENGLISH, null)).isEmpty();
    }

    // ---- lexical MLT path -------------------------------------------------

    @Test
    void nonHybridSiteUsesLexicalMlt() {
        snapshotExists(Locale.ENGLISH);
        when(hybridRankingService.isEnabled(any())).thenReturn(false);
        when(plugin.getSimilarDocuments(any(), eq(SEED), eq(10)))
                .thenReturn(List.of(TurSESimilarResult.builder().id("doc-2").title("Other").build()));

        List<TurSESimilarResult> results = service.findSimilar(SITE, SEED, 10, Locale.ENGLISH, null);

        assertThat(results).extracting(TurSESimilarResult::getId).containsExactly("doc-2");
        verify(hybridRankingService, never()).findNeighbors(any(), any(), anyInt());
    }

    @Test
    void explicitMltModeSkipsVectorEvenOnHybridSite() {
        snapshotExists(Locale.ENGLISH);
        lenient().when(hybridRankingService.isEnabled(any())).thenReturn(true);
        when(plugin.getSimilarDocuments(any(), eq(SEED), eq(5)))
                .thenReturn(List.of(TurSESimilarResult.builder().id("doc-9").build()));

        List<TurSESimilarResult> results = service.findSimilar(SITE, SEED, 5, Locale.ENGLISH,
                TurSNSimilarMode.MLT);

        assertThat(results).extracting(TurSESimilarResult::getId).containsExactly("doc-9");
        verify(hybridRankingService, never()).findNeighbors(any(), any(), anyInt());
    }

    // ---- vector path ------------------------------------------------------

    @Test
    void hybridSiteUsesVectorNeighborsByDefault() {
        snapshotExists(Locale.ENGLISH);
        when(hybridRankingService.isEnabled(any())).thenReturn(true);
        when(plugin.getDocumentsByIds(any(), eq(List.of(SEED))))
                .thenReturn(List.of(doc(SEED, "Data Science")));
        when(hybridRankingService.findNeighbors(any(), any(), eq(10)))
                .thenReturn(List.of("doc-2", "doc-3"));
        when(plugin.getDocumentsByIds(any(), eq(List.of("doc-2", "doc-3"))))
                .thenReturn(List.of(doc("doc-2", "Stats"), doc("doc-3", "ML")));

        List<TurSESimilarResult> results = service.findSimilar(SITE, SEED, 10, Locale.ENGLISH, null);

        assertThat(results).extracting(TurSESimilarResult::getId).containsExactly("doc-2", "doc-3");
        assertThat(results).extracting(TurSESimilarResult::getTitle).containsExactly("Stats", "ML");
        verify(plugin, never()).getSimilarDocuments(any(), anyString(), anyInt());
    }

    @Test
    void vectorPathExcludesSeedFromHydratedResults() {
        snapshotExists(Locale.ENGLISH);
        when(hybridRankingService.isEnabled(any())).thenReturn(true);
        when(plugin.getDocumentsByIds(any(), eq(List.of(SEED))))
                .thenReturn(List.of(doc(SEED, "Data Science")));
        when(hybridRankingService.findNeighbors(any(), any(), eq(10)))
                .thenReturn(List.of(SEED, "doc-2"));
        // Defensive: even if the seed slips into the hydrated set it must be dropped.
        when(plugin.getDocumentsByIds(any(), eq(List.of(SEED, "doc-2"))))
                .thenReturn(List.of(doc(SEED, "Data Science"), doc("doc-2", "Stats")));

        List<TurSESimilarResult> results = service.findSimilar(SITE, SEED, 10, Locale.ENGLISH, null);

        assertThat(results).extracting(TurSESimilarResult::getId).containsExactly("doc-2");
    }

    @Test
    void vectorPathFailsOpenToLexicalWhenNoNeighbors() {
        snapshotExists(Locale.ENGLISH);
        when(hybridRankingService.isEnabled(any())).thenReturn(true);
        when(plugin.getDocumentsByIds(any(), eq(List.of(SEED))))
                .thenReturn(List.of(doc(SEED, "Data Science")));
        when(hybridRankingService.findNeighbors(any(), any(), eq(10))).thenReturn(List.of());
        when(plugin.getSimilarDocuments(any(), eq(SEED), eq(10)))
                .thenReturn(List.of(TurSESimilarResult.builder().id("lex-1").build()));

        List<TurSESimilarResult> results = service.findSimilar(SITE, SEED, 10, Locale.ENGLISH, null);

        assertThat(results).extracting(TurSESimilarResult::getId).containsExactly("lex-1");
    }

    @Test
    void vectorPathFailsOpenWhenSeedDocumentIsMissing() {
        snapshotExists(Locale.ENGLISH);
        when(hybridRankingService.isEnabled(any())).thenReturn(true);
        when(plugin.getDocumentsByIds(any(), eq(List.of(SEED)))).thenReturn(List.of());
        when(plugin.getSimilarDocuments(any(), eq(SEED), eq(10)))
                .thenReturn(List.of(TurSESimilarResult.builder().id("lex-1").build()));

        List<TurSESimilarResult> results = service.findSimilar(SITE, SEED, 10, Locale.ENGLISH, null);

        assertThat(results).extracting(TurSESimilarResult::getId).containsExactly("lex-1");
        verify(hybridRankingService, never()).findNeighbors(any(), any(), anyInt());
    }

    // ---- rows clamping ----------------------------------------------------

    @Test
    void rowsAreClampedToUpperBound() {
        snapshotExists(Locale.ENGLISH);
        when(hybridRankingService.isEnabled(any())).thenReturn(false);
        when(plugin.getSimilarDocuments(any(), eq(SEED), eq(50))).thenReturn(List.of());

        service.findSimilar(SITE, SEED, 999, Locale.ENGLISH, null);

        verify(plugin).getSimilarDocuments(any(), eq(SEED), eq(50));
    }

    @Test
    void nonPositiveRowsFallBackToDefault() {
        snapshotExists(Locale.ENGLISH);
        when(hybridRankingService.isEnabled(any())).thenReturn(false);
        when(plugin.getSimilarDocuments(any(), eq(SEED), eq(10))).thenReturn(List.of());

        service.findSimilar(SITE, SEED, 0, Locale.ENGLISH, null);

        verify(plugin).getSimilarDocuments(any(), eq(SEED), eq(10));
    }
}
