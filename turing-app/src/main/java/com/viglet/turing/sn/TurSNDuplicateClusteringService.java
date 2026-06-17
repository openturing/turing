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
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

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

import lombok.extern.slf4j.Slf4j;

/**
 * T390 / §XX.10 — multi-source duplicate clustering for Semantic Navigation.
 *
 * <p>The same real-world entity arrives from many of an aggregator's sources —
 * the identical course listed by five portals. Without dedup the catalog shows
 * it five times and facet counts inflate. This service reuses the T384
 * vector-neighbor retrieval ({@link TurSNHybridRankingService#findScoredNeighbors}),
 * but tuned for <em>identity</em> rather than similarity: it applies a high
 * similarity threshold so only near-identical documents join the cluster, and it
 * keeps every member's provenance so the duplicates collapse into one canonical
 * result while still surfacing all the sources they came from.
 *
 * <p>Clustering is purely content-based — the canonical representative is chosen
 * deterministically (lexicographically smallest member id) and never reflects
 * any commercial signal (the Block&nbsp;R objective-ranking invariant).
 *
 * <p><strong>Vector-only and fail-safe.</strong> Clustering needs the per-site
 * {@code sn_<siteId>} vector collection, so it only runs on
 * {@link com.viglet.turing.persistence.model.sn.genai.TurSNRankingMode#isHybrid()
 * hybrid} sites with a default embedding model configured. On any other site, a
 * missing seed, or any failure it degrades to a single-member cluster (the seed
 * alone, {@code duplicate=false}) — it never wrongly merges distinct documents.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Slf4j
@Service
public class TurSNDuplicateClusteringService {

    /** Near-identical default: copies of one entity embed almost identically. */
    static final double DEFAULT_THRESHOLD = 0.92;
    /** Hard upper bound so a caller can't request an unbounded neighbor scan. */
    static final int MAX_MEMBERS = 50;
    static final int DEFAULT_MAX_MEMBERS = 10;

    private final TurSNSiteSearchSnapshotService snapshotService;
    private final TurSearchEnginePluginFactory pluginFactory;
    private final TurSNHybridRankingService hybridRankingService;

    public TurSNDuplicateClusteringService(TurSNSiteSearchSnapshotService snapshotService,
            TurSearchEnginePluginFactory pluginFactory,
            TurSNHybridRankingService hybridRankingService) {
        this.snapshotService = snapshotService;
        this.pluginFactory = pluginFactory;
        this.hybridRankingService = hybridRankingService;
    }

    /**
     * Returns the cluster of documents near-identical to {@code id} on the site.
     *
     * @param siteName   the SN site name
     * @param id         the seed document id
     * @param threshold  minimum similarity for a document to be a duplicate
     *                  (clamped to (0, 1]; a non-positive value uses
     *                  {@value #DEFAULT_THRESHOLD})
     * @param maxMembers maximum extra members beyond the seed (clamped to
     *                  [1, {@value #MAX_MEMBERS}])
     * @param locale     request locale (a default site locale is used when null)
     * @return the cluster around the seed; a single-member, non-duplicate cluster
     *         when there is nothing to collapse; never null
     */
    public TurSNDuplicateCluster findCluster(String siteName, String id, double threshold,
            int maxMembers, Locale locale) {
        if (!StringUtils.hasText(siteName) || !StringUtils.hasText(id)) {
            return empty(id);
        }
        double minSimilarity = clampThreshold(threshold);
        int limit = clampMembers(maxMembers);
        Optional<TurSNSiteSearchSnapshot> snapshot = snapshotService.getSnapshot(siteName, locale);
        if (snapshot.isEmpty() || snapshot.get().site() == null) {
            return empty(id);
        }
        TurSNSite site = snapshot.get().site();
        TurSNSiteLocale siteLocale = resolveLocale(snapshot.get(), locale);
        if (siteLocale == null) {
            return empty(id);
        }
        TurSearchEnginePlugin plugin = pluginFactory.getPluginForSite(site);
        List<Map<String, Object>> seed = plugin.getDocumentsByIds(siteLocale, List.of(id));
        if (seed.isEmpty()) {
            return empty(id);
        }
        Map<String, Object> seedDoc = seed.get(0);
        TurSNDuplicateClusterMember seedMember = toMember(seedDoc, null);

        // Vector-only: a non-hybrid site has no embedded collection to cluster against.
        if (!hybridRankingService.isEnabled(site)) {
            return single(seedMember);
        }
        List<TurSNScoredNeighbor> neighbors = hybridRankingService
                .findScoredNeighbors(site, seedDoc, limit, minSimilarity);
        if (neighbors.isEmpty()) {
            return single(seedMember);
        }
        return hydrate(siteLocale, plugin, seedMember, neighbors);
    }

    /**
     * T390 / §XX.10 — attaches a duplicate cluster to every result on a search
     * page that has near-identical siblings, in place. This is the inline
     * counterpart of {@link #findCluster}: instead of re-fetching a seed by id it
     * uses each result's own fields as the seed, runs one vector pass per result,
     * and <em>batches</em> the hydration of all neighbor ids into a single engine
     * call. Results without a duplicate keep {@code null} (no cluster attached).
     *
     * <p>No-op (and never throws) when the site is not on a hybrid ranking mode,
     * the embedding defaults are missing, or the page is empty — so the legacy
     * lexical response is unchanged.
     *
     * @param snapshot   the search snapshot (site + locales)
     * @param locale     request locale (used to resolve the hydration locale)
     * @param plugin     the site's search-engine plugin
     * @param results    the result page to enrich, mutated in place
     * @param threshold  minimum similarity for a duplicate (clamped; default
     *                  {@value #DEFAULT_THRESHOLD})
     * @param maxMembers maximum extra members per result (clamped to
     *                  [1, {@value #MAX_MEMBERS}])
     */
    public void attachClusters(TurSNSiteSearchSnapshot snapshot, Locale locale,
            TurSearchEnginePlugin plugin, List<TurSEResult> results, double threshold, int maxMembers) {
        if (snapshot == null || snapshot.site() == null || plugin == null
                || CollectionUtils.isEmpty(results)
                || !hybridRankingService.isEnabled(snapshot.site())) {
            return;
        }
        TurSNSiteLocale siteLocale = resolveLocale(snapshot, locale);
        if (siteLocale == null) {
            return;
        }
        TurSNSite site = snapshot.site();
        double minSimilarity = clampThreshold(threshold);
        int limit = clampMembers(maxMembers);
        try {
            // Pass 1: one vector pass per result; collect every neighbor id once.
            Map<TurSEResult, List<TurSNScoredNeighbor>> neighborsByResult = new LinkedHashMap<>();
            Set<String> allNeighborIds = new LinkedHashSet<>();
            for (TurSEResult result : results) {
                Map<String, Object> seedDoc = result.getFields();
                if (CollectionUtils.isEmpty(seedDoc)) {
                    continue;
                }
                List<TurSNScoredNeighbor> neighbors = hybridRankingService
                        .findScoredNeighbors(site, seedDoc, limit, minSimilarity);
                if (!neighbors.isEmpty()) {
                    neighborsByResult.put(result, neighbors);
                    neighbors.forEach(neighbor -> allNeighborIds.add(neighbor.id()));
                }
            }
            if (allNeighborIds.isEmpty()) {
                return;
            }
            // Pass 2: hydrate all neighbors in a single engine call, index by id.
            Map<String, Map<String, Object>> docById = new LinkedHashMap<>();
            for (Map<String, Object> doc : plugin.getDocumentsByIds(siteLocale,
                    new ArrayList<>(allNeighborIds))) {
                String docId = asString(doc.get(TurSNFieldName.ID));
                if (docId != null) {
                    docById.putIfAbsent(docId, doc);
                }
            }
            // Pass 3: assemble each result's cluster from the shared hydration map.
            neighborsByResult.forEach((result, neighbors) -> {
                TurSNDuplicateClusterMember seedMember = toMember(result.getFields(), null);
                TurSNDuplicateCluster cluster = assemble(seedMember, neighbors, docById);
                if (cluster != null) {
                    result.setDuplicateCluster(cluster);
                }
            });
        } catch (RuntimeException e) {
            log.warn("[SN-DEDUP] attaching clusters on site '{}' failed: {} — results unchanged",
                    site.getName(), e.getMessage());
        }
    }

    /** Builds a cluster for one seed from already-hydrated neighbor docs, or null when no sibling survives. */
    private static TurSNDuplicateCluster assemble(TurSNDuplicateClusterMember seedMember,
            List<TurSNScoredNeighbor> neighbors, Map<String, Map<String, Object>> docById) {
        List<TurSNDuplicateClusterMember> members = new ArrayList<>(neighbors.size() + 1);
        members.add(seedMember);
        for (TurSNScoredNeighbor neighbor : neighbors) {
            Map<String, Object> doc = docById.get(neighbor.id());
            if (doc == null || neighbor.id().equals(seedMember.id())) {
                continue;
            }
            members.add(toMember(doc, neighbor.score()));
        }
        if (members.size() < 2) {
            return null;
        }
        return new TurSNDuplicateCluster(canonicalOf(members), members.size(), true, members);
    }

    /** Hydrates the neighbor ids into members and assembles the cluster. */
    private TurSNDuplicateCluster hydrate(TurSNSiteLocale siteLocale, TurSearchEnginePlugin plugin,
            TurSNDuplicateClusterMember seedMember, List<TurSNScoredNeighbor> neighbors) {
        Map<String, Double> scoreById = new LinkedHashMap<>();
        for (TurSNScoredNeighbor neighbor : neighbors) {
            scoreById.putIfAbsent(neighbor.id(), neighbor.score());
        }
        List<Map<String, Object>> docs = plugin.getDocumentsByIds(siteLocale,
                new ArrayList<>(scoreById.keySet()));

        List<TurSNDuplicateClusterMember> members = new ArrayList<>(docs.size() + 1);
        members.add(seedMember);
        for (Map<String, Object> doc : docs) {
            String docId = asString(doc.get(TurSNFieldName.ID));
            if (docId == null || docId.equals(seedMember.id())) {
                continue;
            }
            members.add(toMember(doc, scoreById.get(docId)));
        }
        if (members.size() < 2) {
            return single(seedMember);
        }
        String canonicalId = canonicalOf(members);
        return new TurSNDuplicateCluster(canonicalId, members.size(), true, members);
    }

    /** The deterministic representative: the lexicographically smallest member id. */
    private static String canonicalOf(List<TurSNDuplicateClusterMember> members) {
        String canonical = members.get(0).id();
        for (TurSNDuplicateClusterMember member : members) {
            if (member.id() != null && (canonical == null || member.id().compareTo(canonical) < 0)) {
                canonical = member.id();
            }
        }
        return canonical;
    }

    private static TurSNDuplicateClusterMember toMember(Map<String, Object> doc, Double similarity) {
        return new TurSNDuplicateClusterMember(
                asString(doc.get(TurSNFieldName.ID)),
                asString(doc.get(TurSNFieldName.TITLE)),
                asString(doc.get(TurSNFieldName.TYPE)),
                asString(doc.get(TurSNFieldName.URL)),
                asString(doc.get(TurSNFieldName.SOURCE_APPS)),
                similarity);
    }

    private static TurSNDuplicateCluster single(TurSNDuplicateClusterMember seedMember) {
        return new TurSNDuplicateCluster(seedMember.id(), 1, false, List.of(seedMember));
    }

    private static TurSNDuplicateCluster empty(String id) {
        return new TurSNDuplicateCluster(id, 0, false, List.of());
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

    private static double clampThreshold(double threshold) {
        if (threshold <= 0.0 || threshold > 1.0) {
            return DEFAULT_THRESHOLD;
        }
        return threshold;
    }

    private static int clampMembers(int maxMembers) {
        if (maxMembers < 1) {
            return DEFAULT_MAX_MEMBERS;
        }
        return Math.min(maxMembers, MAX_MEMBERS);
    }

    private static String asString(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof List<?> list) {
            return list.isEmpty() || list.get(0) == null ? null : list.get(0).toString();
        }
        return value.toString();
    }
}
