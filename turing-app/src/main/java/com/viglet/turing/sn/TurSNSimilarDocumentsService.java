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

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.viglet.turing.commons.se.similar.TurSESimilarResult;
import com.viglet.turing.commons.sn.field.TurSNFieldName;
import com.viglet.turing.persistence.model.sn.TurSNSite;
import com.viglet.turing.persistence.model.sn.locale.TurSNSiteLocale;
import com.viglet.turing.plugins.se.TurSearchEnginePlugin;
import com.viglet.turing.plugins.se.TurSearchEnginePluginFactory;
import com.viglet.turing.sn.ranking.TurSNHybridRankingService;
import com.viglet.turing.sn.snapshot.TurSNSiteSearchSnapshot;
import com.viglet.turing.sn.snapshot.TurSNSiteSearchSnapshotService;

import lombok.extern.slf4j.Slf4j;

/**
 * T384 / §XX.4 — public "documents similar to this id" retrieval for Semantic
 * Navigation, so a catalog renders "similar courses" live from the index
 * instead of shipping a pre-baked neighbors file.
 *
 * <p>Two retrieval strategies, selected per request (see {@link TurSNSimilarMode}):
 * <ul>
 *   <li><b>VECTOR</b> — semantic neighbors over the T383 per-site
 *       {@code sn_<siteId>} vector collection ({@link TurSNHybridRankingService#findNeighbors}),
 *       hydrated back into renderable results via the engine. This is the basis
 *       for T390 cross-source duplicate clustering.</li>
 *   <li><b>MLT</b> — lexical MoreLikeThis over the live search-engine index
 *       ({@link TurSearchEnginePlugin#getSimilarDocuments}).</li>
 * </ul>
 *
 * <p>When no mode is requested it defaults to VECTOR for {@code HYBRID_RRF} sites
 * (where the vector collection exists) and MLT otherwise. The VECTOR path is
 * fail-open: an empty vector result degrades to lexical MLT so the endpoint
 * always returns whatever the index can offer.
 *
 * <p>Ranking is purely content-based — similarity never reflects any commercial
 * signal (the Block R objective-ranking invariant).
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Slf4j
@Service
public class TurSNSimilarDocumentsService {

    /** Hard upper bound so a caller can't request an unbounded neighbor scan. */
    private static final int MAX_ROWS = 50;
    private static final int DEFAULT_ROWS = 10;

    private final TurSNSiteSearchSnapshotService snapshotService;
    private final TurSearchEnginePluginFactory pluginFactory;
    private final TurSNHybridRankingService hybridRankingService;

    public TurSNSimilarDocumentsService(TurSNSiteSearchSnapshotService snapshotService,
            TurSearchEnginePluginFactory pluginFactory,
            TurSNHybridRankingService hybridRankingService) {
        this.snapshotService = snapshotService;
        this.pluginFactory = pluginFactory;
        this.hybridRankingService = hybridRankingService;
    }

    /**
     * Returns the documents most similar to {@code id} on the given site.
     *
     * @param siteName the SN site name
     * @param id       the seed document id
     * @param rows     desired number of results (clamped to [1, {@value #MAX_ROWS}])
     * @param locale   request locale (a default site locale is used when null)
     * @param mode     retrieval strategy, or null to auto-select
     * @return up to {@code rows} similar documents, seed excluded; never null
     */
    public List<TurSESimilarResult> findSimilar(String siteName, String id, int rows,
            Locale locale, TurSNSimilarMode mode) {
        if (!StringUtils.hasText(siteName) || !StringUtils.hasText(id)) {
            return List.of();
        }
        int limit = clampRows(rows);
        Optional<TurSNSiteSearchSnapshot> snapshot = snapshotService.getSnapshot(siteName, locale);
        if (snapshot.isEmpty() || snapshot.get().site() == null) {
            return List.of();
        }
        TurSNSite site = snapshot.get().site();
        TurSNSiteLocale siteLocale = resolveLocale(snapshot.get(), locale);
        if (siteLocale == null) {
            return List.of();
        }
        TurSearchEnginePlugin plugin = pluginFactory.getPluginForSite(site);

        boolean hybrid = hybridRankingService.isEnabled(site);
        TurSNSimilarMode defaultMode = hybrid ? TurSNSimilarMode.VECTOR : TurSNSimilarMode.MLT;
        TurSNSimilarMode effective = mode != null ? mode : defaultMode;

        if (effective == TurSNSimilarMode.VECTOR && hybrid) {
            List<TurSESimilarResult> vector = vectorNeighbors(site, siteLocale, plugin, id, limit);
            if (!vector.isEmpty()) {
                return vector;
            }
            // Fail-open: nothing semantic to offer — fall through to lexical MLT.
            log.debug("[SN-SIMILAR] vector neighbors empty for id '{}' on '{}' — using lexical MLT",
                    id, siteName);
        }
        return plugin.getSimilarDocuments(siteLocale, id, limit);
    }

    private List<TurSESimilarResult> vectorNeighbors(TurSNSite site, TurSNSiteLocale siteLocale,
            TurSearchEnginePlugin plugin, String id, int rows) {
        List<Map<String, Object>> seed = plugin.getDocumentsByIds(siteLocale, List.of(id));
        if (seed.isEmpty()) {
            return List.of();
        }
        List<String> neighborIds = hybridRankingService.findNeighbors(site, seed.get(0), rows);
        if (neighborIds.isEmpty()) {
            return List.of();
        }
        List<Map<String, Object>> docs = plugin.getDocumentsByIds(siteLocale, neighborIds);
        List<TurSESimilarResult> results = new ArrayList<>(docs.size());
        for (Map<String, Object> doc : docs) {
            String docId = asString(doc.get(TurSNFieldName.ID));
            if (docId == null || docId.equals(id)) {
                continue;
            }
            results.add(TurSESimilarResult.builder()
                    .id(docId)
                    .title(asString(doc.get(TurSNFieldName.TITLE)))
                    .type(asString(doc.get(TurSNFieldName.TYPE)))
                    .url(asString(doc.get(TurSNFieldName.URL)))
                    .build());
        }
        return results;
    }

    /** Picks the {@link TurSNSiteLocale} matching the request locale, else the first available. */
    private static TurSNSiteLocale resolveLocale(TurSNSiteSearchSnapshot snapshot, Locale locale) {
        List<TurSNSiteLocale> locales = snapshot.allLocales();
        if (locales == null || locales.isEmpty()) {
            return null;
        }
        if (locale != null) {
            for (TurSNSiteLocale candidate : locales) {
                if (locale.equals(candidate.getLanguage())) {
                    return candidate;
                }
            }
        }
        return locales.get(0);
    }

    private static int clampRows(int rows) {
        if (rows < 1) {
            return DEFAULT_ROWS;
        }
        return Math.min(rows, MAX_ROWS);
    }

    private static String asString(Object value) {
        return value == null ? null : value.toString();
    }
}
