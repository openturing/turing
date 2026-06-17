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
import static org.mockito.ArgumentMatchers.anyDouble;
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

import com.viglet.turing.commons.sn.bean.TurSNDuplicateCluster;
import com.viglet.turing.commons.sn.bean.TurSNDuplicateClusterMember;
import com.viglet.turing.commons.sn.field.TurSNFieldName;
import com.viglet.turing.persistence.model.sn.TurSNSite;
import com.viglet.turing.persistence.model.sn.locale.TurSNSiteLocale;
import com.viglet.turing.plugins.se.TurSearchEnginePlugin;
import com.viglet.turing.plugins.se.TurSearchEnginePluginFactory;
import com.viglet.turing.se.result.TurSEResult;
import com.viglet.turing.sn.ranking.TurSNHybridRankingService;
import com.viglet.turing.sn.ranking.TurSNScoredNeighbor;
import com.viglet.turing.sn.snapshot.TurSNSiteSearchSnapshot;
import com.viglet.turing.sn.snapshot.TurSNSiteSearchSnapshotService;

/**
 * T390 / §XX.10 — unit coverage for multi-source duplicate clustering: the
 * vector path collapsing near-identical documents (with provenance and a
 * deterministic canonical), the non-duplicate single-member result, and the
 * fail-safe degradation on non-hybrid sites / missing seeds.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@ExtendWith(MockitoExtension.class)
class TurSNDuplicateClusteringServiceTest {

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

    private TurSNDuplicateClusteringService service;

    @BeforeEach
    void setUp() {
        service = new TurSNDuplicateClusteringService(snapshotService, pluginFactory,
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

    private static Map<String, Object> doc(String id, String title, String source) {
        return Map.of(TurSNFieldName.ID, id, TurSNFieldName.TITLE, title,
                TurSNFieldName.TYPE, "Course", TurSNFieldName.URL, "/c/" + id,
                TurSNFieldName.SOURCE_APPS, source);
    }

    // ---- guards / fail-safe ----------------------------------------------

    @Test
    void blankIdReturnsEmptyCluster() {
        TurSNDuplicateCluster cluster = service.findCluster(SITE, "  ", 0.92, 10, Locale.ENGLISH);
        assertThat(cluster.members()).isEmpty();
        assertThat(cluster.duplicate()).isFalse();
        verify(snapshotService, never()).getSnapshot(anyString(), any());
    }

    @Test
    void missingSeedDocumentReturnsEmptyCluster() {
        snapshotExists(Locale.ENGLISH);
        when(plugin.getDocumentsByIds(any(), eq(List.of(SEED)))).thenReturn(List.of());

        TurSNDuplicateCluster cluster = service.findCluster(SITE, SEED, 0.92, 10, Locale.ENGLISH);

        assertThat(cluster.members()).isEmpty();
        assertThat(cluster.canonicalId()).isEqualTo(SEED);
        verify(hybridRankingService, never()).findScoredNeighbors(any(), any(), anyInt(), anyDouble());
    }

    @Test
    void nonHybridSiteReturnsSingleMemberCluster() {
        snapshotExists(Locale.ENGLISH);
        when(plugin.getDocumentsByIds(any(), eq(List.of(SEED))))
                .thenReturn(List.of(doc(SEED, "Data Science", "portal-a")));
        when(hybridRankingService.isEnabled(any())).thenReturn(false);

        TurSNDuplicateCluster cluster = service.findCluster(SITE, SEED, 0.92, 10, Locale.ENGLISH);

        assertThat(cluster.size()).isEqualTo(1);
        assertThat(cluster.duplicate()).isFalse();
        assertThat(cluster.members()).extracting(TurSNDuplicateClusterMember::id).containsExactly(SEED);
        verify(hybridRankingService, never()).findScoredNeighbors(any(), any(), anyInt(), anyDouble());
    }

    @Test
    void noNeighborsReturnsSingleMemberCluster() {
        snapshotExists(Locale.ENGLISH);
        when(plugin.getDocumentsByIds(any(), eq(List.of(SEED))))
                .thenReturn(List.of(doc(SEED, "Data Science", "portal-a")));
        when(hybridRankingService.isEnabled(any())).thenReturn(true);
        when(hybridRankingService.findScoredNeighbors(any(), any(), anyInt(), anyDouble()))
                .thenReturn(List.of());

        TurSNDuplicateCluster cluster = service.findCluster(SITE, SEED, 0.92, 10, Locale.ENGLISH);

        assertThat(cluster.size()).isEqualTo(1);
        assertThat(cluster.duplicate()).isFalse();
    }

    // ---- duplicate clustering --------------------------------------------

    @Test
    void clustersNearIdenticalDocumentsWithProvenance() {
        snapshotExists(Locale.ENGLISH);
        when(plugin.getDocumentsByIds(any(), eq(List.of(SEED))))
                .thenReturn(List.of(doc(SEED, "Data Science MSc", "portal-a")));
        when(hybridRankingService.isEnabled(any())).thenReturn(true);
        when(hybridRankingService.findScoredNeighbors(any(), any(), eq(10), eq(0.92)))
                .thenReturn(List.of(new TurSNScoredNeighbor("doc-2", 0.97),
                        new TurSNScoredNeighbor("doc-3", 0.93)));
        when(plugin.getDocumentsByIds(any(), eq(List.of("doc-2", "doc-3"))))
                .thenReturn(List.of(doc("doc-2", "Data Science MSc", "portal-b"),
                        doc("doc-3", "Data Science MSc", "portal-c")));

        TurSNDuplicateCluster cluster = service.findCluster(SITE, SEED, 0.92, 10, Locale.ENGLISH);

        assertThat(cluster.duplicate()).isTrue();
        assertThat(cluster.size()).isEqualTo(3);
        assertThat(cluster.members()).extracting(TurSNDuplicateClusterMember::id)
                .containsExactly(SEED, "doc-2", "doc-3");
        assertThat(cluster.members()).extracting(TurSNDuplicateClusterMember::source)
                .containsExactly("portal-a", "portal-b", "portal-c");
        // Seed carries no similarity; neighbors carry their score to the seed.
        assertThat(cluster.members().get(0).similarity()).isNull();
        assertThat(cluster.members().get(1).similarity()).isEqualTo(0.97);
    }

    @Test
    void canonicalIsLexicographicallySmallestMemberId() {
        snapshotExists(Locale.ENGLISH);
        when(plugin.getDocumentsByIds(any(), eq(List.of(SEED))))
                .thenReturn(List.of(doc(SEED, "Course", "portal-a")));
        when(hybridRankingService.isEnabled(any())).thenReturn(true);
        when(hybridRankingService.findScoredNeighbors(any(), any(), anyInt(), anyDouble()))
                .thenReturn(List.of(new TurSNScoredNeighbor("aaa", 0.99)));
        when(plugin.getDocumentsByIds(any(), eq(List.of("aaa"))))
                .thenReturn(List.of(doc("aaa", "Course", "portal-b")));

        TurSNDuplicateCluster cluster = service.findCluster(SITE, SEED, 0.92, 10, Locale.ENGLISH);

        // "aaa" sorts before the seed "doc-1", so it becomes the canonical.
        assertThat(cluster.canonicalId()).isEqualTo("aaa");
    }

    @Test
    void seedIsExcludedFromHydratedNeighborMembers() {
        snapshotExists(Locale.ENGLISH);
        when(plugin.getDocumentsByIds(any(), eq(List.of(SEED))))
                .thenReturn(List.of(doc(SEED, "Course", "portal-a")));
        when(hybridRankingService.isEnabled(any())).thenReturn(true);
        when(hybridRankingService.findScoredNeighbors(any(), any(), anyInt(), anyDouble()))
                .thenReturn(List.of(new TurSNScoredNeighbor("doc-2", 0.95)));
        // Defensive: even if the seed slips into the hydrated set it must be dropped.
        when(plugin.getDocumentsByIds(any(), eq(List.of("doc-2"))))
                .thenReturn(List.of(doc(SEED, "Course", "portal-a"), doc("doc-2", "Course", "portal-b")));

        TurSNDuplicateCluster cluster = service.findCluster(SITE, SEED, 0.92, 10, Locale.ENGLISH);

        assertThat(cluster.members()).extracting(TurSNDuplicateClusterMember::id)
                .containsExactly(SEED, "doc-2");
    }

    // ---- parameter clamping ----------------------------------------------

    @Test
    void nonPositiveThresholdFallsBackToDefault() {
        snapshotExists(Locale.ENGLISH);
        when(plugin.getDocumentsByIds(any(), eq(List.of(SEED))))
                .thenReturn(List.of(doc(SEED, "Course", "portal-a")));
        when(hybridRankingService.isEnabled(any())).thenReturn(true);
        when(hybridRankingService.findScoredNeighbors(any(), any(), anyInt(), anyDouble()))
                .thenReturn(List.of());

        service.findCluster(SITE, SEED, 0.0, 10, Locale.ENGLISH);

        verify(hybridRankingService).findScoredNeighbors(any(), any(), eq(10),
                eq(TurSNDuplicateClusteringService.DEFAULT_THRESHOLD));
    }

    @Test
    void rowsAreClampedToUpperBound() {
        snapshotExists(Locale.ENGLISH);
        when(plugin.getDocumentsByIds(any(), eq(List.of(SEED))))
                .thenReturn(List.of(doc(SEED, "Course", "portal-a")));
        when(hybridRankingService.isEnabled(any())).thenReturn(true);
        when(hybridRankingService.findScoredNeighbors(any(), any(), anyInt(), anyDouble()))
                .thenReturn(List.of());

        service.findCluster(SITE, SEED, 0.92, 999, Locale.ENGLISH);

        verify(hybridRankingService).findScoredNeighbors(any(), any(),
                eq(TurSNDuplicateClusteringService.MAX_MEMBERS), anyDouble());
    }

    // ---- attachClusters (inline per-result enrichment) -------------------

    private TurSNSiteSearchSnapshot snapshot(Locale language) {
        return new TurSNSiteSearchSnapshot(site(), language, "solr", false, List.of(),
                java.util.Set.of(), Map.of(), Map.of(), List.of(locale(language)), List.of());
    }

    @Test
    void attachClustersIsNoOpOnNonHybridSite() {
        when(hybridRankingService.isEnabled(any())).thenReturn(false);
        TurSEResult r = TurSEResult.builder().fields(doc(SEED, "Course", "portal-a")).build();

        service.attachClusters(snapshot(Locale.ENGLISH), Locale.ENGLISH, plugin, List.of(r), 0.92, 5);

        assertThat(r.getDuplicateCluster()).isNull();
        verify(hybridRankingService, never()).findScoredNeighbors(any(), any(), anyInt(), anyDouble());
    }

    @Test
    void attachClustersEnrichesOnlyResultsWithDuplicatesAndHydratesOnce() {
        when(hybridRankingService.isEnabled(any())).thenReturn(true);
        TurSEResult withDup = TurSEResult.builder().fields(doc(SEED, "Course", "portal-a")).build();
        TurSEResult noDup = TurSEResult.builder().fields(doc("solo", "Other", "portal-x")).build();
        when(hybridRankingService.findScoredNeighbors(any(), eq(withDup.getFields()), eq(5), anyDouble()))
                .thenReturn(List.of(new TurSNScoredNeighbor("doc-2", 0.96)));
        when(hybridRankingService.findScoredNeighbors(any(), eq(noDup.getFields()), eq(5), anyDouble()))
                .thenReturn(List.of());
        // A single batched hydration call carries every neighbor id across the page.
        when(plugin.getDocumentsByIds(any(), eq(List.of("doc-2"))))
                .thenReturn(List.of(doc("doc-2", "Course", "portal-b")));

        service.attachClusters(snapshot(Locale.ENGLISH), Locale.ENGLISH, plugin,
                List.of(withDup, noDup), 0.92, 5);

        assertThat(noDup.getDuplicateCluster()).isNull();
        assertThat(withDup.getDuplicateCluster()).isNotNull();
        assertThat(withDup.getDuplicateCluster().duplicate()).isTrue();
        assertThat(withDup.getDuplicateCluster().members())
                .extracting(TurSNDuplicateClusterMember::id).containsExactly(SEED, "doc-2");
        // Exactly one hydration call for the whole page (the batch optimization).
        verify(plugin).getDocumentsByIds(any(), eq(List.of("doc-2")));
    }
}
