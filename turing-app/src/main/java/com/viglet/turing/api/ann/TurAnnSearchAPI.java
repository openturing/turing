/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package com.viglet.turing.api.ann;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.apache.commons.lang3.LocaleUtils;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.viglet.turing.api.sn.search.TurSNSiteSearchService;
import com.viglet.turing.genai.TurGenAiContext;
import com.viglet.turing.genai.TurGenAiContextFactory;
import com.viglet.turing.genai.TurRagFilters;
import com.viglet.turing.genai.provider.store.TurStoreChunkPage;
import com.viglet.turing.persistence.model.sn.TurSNSite;
import com.viglet.turing.persistence.model.sn.locale.TurSNSiteLocale;
import com.viglet.turing.persistence.repository.sn.locale.TurSNSiteLocaleRepository;
import com.viglet.turing.sn.TurSNSearchProcess;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;

/**
 * Public ANN (Approximate Nearest Neighbor) search endpoint that queries the
 * site's configured vector store directly. All metadata fields on the
 * retrieved documents are exposed as facets, aggregated within the {@code topK}
 * window. Requires {@code TurSNSiteGenAi.enabled = true} on the site —
 * otherwise responds {@code 404} so the page is effectively gated by RAG.
 *
 * @author Alexandre Oliveira
 * @since 2026.2.4
 */
@Slf4j
@RestController
@RequestMapping("/api/ann/{siteName}")
@Tag(name = "ANN Search", description = "Vector store similarity search per SN site")
public class TurAnnSearchAPI {

    private static final int DEFAULT_TOP_K = 20;
    private static final int MAX_TOP_K = 200;

    private final TurSNSearchProcess turSNSearchProcess;
    private final TurGenAiContextFactory turGenAiContextFactory;
    private final TurSNSiteLocaleRepository turSNSiteLocaleRepository;
    private final TurSNSiteSearchService turSNSiteSearchService;

    public TurAnnSearchAPI(TurSNSearchProcess turSNSearchProcess,
            TurGenAiContextFactory turGenAiContextFactory,
            TurSNSiteLocaleRepository turSNSiteLocaleRepository,
            TurSNSiteSearchService turSNSiteSearchService) {
        this.turSNSearchProcess = turSNSearchProcess;
        this.turGenAiContextFactory = turGenAiContextFactory;
        this.turSNSiteLocaleRepository = turSNSiteLocaleRepository;
        this.turSNSiteSearchService = turSNSiteSearchService;
    }

    public record TurAnnSearchRequest(
            String query,
            String locale,
            Integer topK,
            Integer page,
            Integer pageSize,
            Map<String, List<String>> filters) {
    }

    public record TurAnnResultItem(
            String id,
            String content,
            Double score,
            Map<String, Object> metadata) {
    }

    public record TurAnnFacetItem(String value, int count) {
    }

    public record TurAnnSearchResponse(
            String query,
            String locale,
            int topK,
            int page,
            int pageSize,
            int totalHits,
            boolean hasMore,
            List<TurAnnResultItem> results,
            Map<String, List<TurAnnFacetItem>> facets) {
    }

    @PostMapping("/search")
    public ResponseEntity<TurAnnSearchResponse> search(@PathVariable String siteName,
            @RequestBody TurAnnSearchRequest body) {
        if (body == null) {
            return ResponseEntity.badRequest().build();
        }

        TurSNSite site = turSNSearchProcess.getSNSite(siteName).orElse(null);
        if (site == null) {
            return ResponseEntity.notFound().build();
        }
        var genAi = site.getTurSNSiteGenAi();
        var agent = genAi == null ? null : genAi.getTurAIAgent();
        if (agent == null || agent.getEnabled() != 1 || !agent.isRagEnabled()) {
            // 403 (not 404) so the frontend can distinguish "RAG disabled" from
            // "endpoint missing / app not restarted yet".
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        Locale locale = resolveLocale(body.locale(), siteName);
        String collectionName = resolveCollectionName(site, locale);

        TurGenAiContext context = turGenAiContextFactory.build(genAi, collectionName);
        if (!context.isEnabled() || context.getVectorStore() == null) {
            log.warn("ANN search: RAG context not available for site '{}' (locale='{}')", siteName, locale);
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
        }

        boolean isBrowseAll = !StringUtils.hasText(body.query()) || "*".equals(body.query().trim());
        if (isBrowseAll) {
            return ResponseEntity.ok(browseAll(siteName, locale, body, context, collectionName));
        }
        return ResponseEntity.ok(similaritySearch(siteName, locale, body, context));
    }

    private TurAnnSearchResponse similaritySearch(String siteName, Locale locale,
            TurAnnSearchRequest body, TurGenAiContext context) {
        int topK = clampTopK(body.topK());
        Filter.Expression filterExpr = TurRagFilters.buildFilterExpression(body.filters());

        SearchRequest.Builder reqBuilder = SearchRequest.builder()
                .query(body.query())
                .topK(topK);
        if (filterExpr != null) {
            reqBuilder.filterExpression(filterExpr);
        }

        log.info("ANN search site='{}' locale='{}' topK={} filters={} query='{}'",
                siteName, locale, topK, body.filters(), body.query());

        List<Document> hits;
        try {
            hits = context.getVectorStore().similaritySearch(reqBuilder.build());
        } catch (Exception e) {
            log.error("ANN search failed for site '{}': {}", siteName, e.getMessage(), e);
            return new TurAnnSearchResponse(body.query(), locale.toString(), topK,
                    0, topK, 0, false, List.of(), Map.of());
        }
        if (hits == null) {
            hits = List.of();
        }

        List<TurAnnResultItem> items = new ArrayList<>(hits.size());
        for (Document doc : hits) {
            items.add(new TurAnnResultItem(
                    doc.getId(),
                    doc.getText(),
                    doc.getScore(),
                    doc.getMetadata() != null ? doc.getMetadata() : Map.of()));
        }

        Map<String, List<TurAnnFacetItem>> facets = aggregateFacets(items);
        // Similarity search returns a single page of size topK (no pagination).
        return new TurAnnSearchResponse(
                body.query(), locale.toString(), topK,
                0, topK, items.size(), false,
                items, facets);
    }

    /**
     * Browse-all path triggered when the query is empty or {@code "*"}: the
     * underlying store provider's {@code listChunks} paginates without
     * similarity scoring so the admin can scroll all indexed content.
     */
    private TurAnnSearchResponse browseAll(String siteName, Locale locale,
            TurAnnSearchRequest body, TurGenAiContext context, String collectionName) {
        int pageSize = clampPageSize(body.pageSize());
        int page = body.page() == null ? 0 : Math.max(0, body.page());

        var infra = context.getRagInfrastructure();
        log.info("ANN browse-all site='{}' locale='{}' page={} pageSize={} filters={}",
                siteName, locale, page, pageSize, body.filters());

        TurStoreChunkPage chunkPage;
        try {
            chunkPage = infra.storeProvider().listChunks(
                    infra.storeInstance(),
                    infra.storeCredential(),
                    collectionName,
                    page,
                    pageSize,
                    body.filters());
        } catch (UnsupportedOperationException e) {
            log.warn("ANN browse-all not supported by store '{}': {}",
                    infra.storeProvider().getPluginType(), e.getMessage());
            return new TurAnnSearchResponse(body.query(), locale.toString(), 0,
                    page, pageSize, 0, false, List.of(), Map.of());
        } catch (Exception e) {
            log.error("ANN browse-all failed for site '{}': {}", siteName, e.getMessage(), e);
            return new TurAnnSearchResponse(body.query(), locale.toString(), 0,
                    page, pageSize, 0, false, List.of(), Map.of());
        }

        List<TurAnnResultItem> items = new ArrayList<>(chunkPage.chunks().size());
        for (TurStoreChunkPage.Chunk c : chunkPage.chunks()) {
            items.add(new TurAnnResultItem(c.id(), c.content(), null, c.metadata()));
        }
        Map<String, List<TurAnnFacetItem>> facets = aggregateFacets(items);
        long total = chunkPage.total();
        boolean hasMore = total >= 0
                ? (long) (page + 1) * pageSize < total
                : items.size() == pageSize;
        int totalHits = total >= 0 ? (int) Math.min(Integer.MAX_VALUE, total) : items.size();
        return new TurAnnSearchResponse(
                body.query(), locale.toString(), 0,
                page, pageSize, totalHits, hasMore,
                items, facets);
    }

    private int clampPageSize(Integer requested) {
        if (requested == null || requested <= 0) {
            return DEFAULT_TOP_K;
        }
        return Math.min(requested, MAX_TOP_K);
    }

    private int clampTopK(Integer requested) {
        if (requested == null || requested <= 0) {
            return DEFAULT_TOP_K;
        }
        return Math.min(requested, MAX_TOP_K);
    }

    private Locale resolveLocale(String requested, String siteName) {
        if (!StringUtils.hasText(requested)) {
            return turSNSiteSearchService.resolveDefaultLocale(siteName);
        }
        try {
            return LocaleUtils.toLocale(requested);
        } catch (IllegalArgumentException e) {
            return turSNSiteSearchService.resolveDefaultLocale(siteName);
        }
    }

    private String resolveCollectionName(TurSNSite site, Locale locale) {
        if (locale == null) {
            return null;
        }
        TurSNSiteLocale siteLocale = turSNSiteLocaleRepository.findByTurSNSiteAndLanguage(site, locale);
        return siteLocale != null ? siteLocale.getCore() : null;
    }

    /**
     * Aggregates facet counts over the returned hits. Scalar values become a
     * single bucket; collections/arrays expand into multiple buckets. Buckets
     * are sorted by count desc.
     */
    private Map<String, List<TurAnnFacetItem>> aggregateFacets(List<TurAnnResultItem> items) {
        Map<String, Map<String, Integer>> raw = new LinkedHashMap<>();
        for (TurAnnResultItem item : items) {
            for (Map.Entry<String, Object> entry : item.metadata().entrySet()) {
                String key = entry.getKey();
                Object value = entry.getValue();
                if (value == null) {
                    continue;
                }
                Map<String, Integer> bucket = raw.computeIfAbsent(key, k -> new LinkedHashMap<>());
                if (value instanceof Collection<?> collection) {
                    for (Object v : collection) {
                        if (v != null) {
                            bucket.merge(String.valueOf(v), 1, (a, c) -> a + c);
                        }
                    }
                } else if (value.getClass().isArray()) {
                    for (Object v : (Object[]) value) {
                        if (v != null) {
                            bucket.merge(String.valueOf(v), 1, (a, c) -> a + c);
                        }
                    }
                } else {
                    bucket.merge(String.valueOf(value), 1, (a, c) -> a + c);
                }
            }
        }
        Map<String, List<TurAnnFacetItem>> facets = new LinkedHashMap<>();
        for (Map.Entry<String, Map<String, Integer>> entry : raw.entrySet()) {
            List<TurAnnFacetItem> sorted = entry.getValue().entrySet().stream()
                    .sorted((a, c) -> Integer.compare(c.getValue(), a.getValue()))
                    .map(e -> new TurAnnFacetItem(e.getKey(), e.getValue()))
                    .toList();
            facets.put(entry.getKey(), sorted);
        }
        return facets;
    }
}
