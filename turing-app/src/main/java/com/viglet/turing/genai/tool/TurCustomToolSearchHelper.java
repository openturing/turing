/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.tool;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.apache.commons.lang3.LocaleUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.stereotype.Service;

import com.viglet.turing.api.sn.search.TurSNSiteSearchService;
import com.viglet.turing.genai.TurGenAiContext;
import com.viglet.turing.genai.TurGenAiContextFactory;
import com.viglet.turing.genai.TurRagFilters;
import com.viglet.turing.persistence.model.sn.TurSNSite;
import com.viglet.turing.persistence.model.sn.locale.TurSNSiteLocale;
import com.viglet.turing.persistence.repository.sn.locale.TurSNSiteLocaleRepository;
import com.viglet.turing.sn.TurSNSearchProcess;

/**
 * High-level search helper exposed to Custom Tool Groovy scripts as the
 * binding variable {@code turingSearch}. Wraps the two search surfaces
 * a customer-authored tool typically needs — the semantic ANN index
 * (vector similarity) and the SN site search — so scripts can ignore
 * the {@code RestClient} / {@code VectorStore} plumbing.
 *
 * <p>Implementation mirrors {@code TurAnnSearchAPI} for the {@link #ann(Map)}
 * path: site resolution → genAI context build → similarity search on the
 * site's vector store. No HTTP self-call — the helper runs in-process,
 * shares the same Spring beans the public endpoint uses, and produces
 * results identical to what the admin sees in the ANN admin UI.
 *
 * <p>Both methods accept a Groovy-friendly {@link Map} of named parameters
 * (Groovy lets callers write {@code turingSearch.ann(site: "x", query: "y")}
 * which becomes a single {@code Map} argument), keeping the call site terse.
 *
 * <h3>Return shape</h3>
 * Each method returns {@code List<Map<String, Object>>} — one entry per hit.
 * Every hit map contains:
 * <ul>
 *   <li>{@code id} — document id</li>
 *   <li>{@code score} — similarity score (when applicable; {@code null} for
 *       browse-all)</li>
 *   <li>{@code content} — full chunk text</li>
 *   <li>everything from the document's metadata, lifted to top level so
 *       Groovy can do {@code hit.title}, {@code hit.preco},
 *       {@code hit.dateOfClassesText} — no nested unwrapping in scripts.</li>
 * </ul>
 *
 * @author Alexandre Oliveira
 * @since 2026.2.7
 */
@Service
public class TurCustomToolSearchHelper {

    private static final Logger log = LoggerFactory.getLogger(TurCustomToolSearchHelper.class);
    private static final int DEFAULT_TOP_K = 8;
    private static final int MAX_TOP_K = 200;
    /**
     * Multiplier used to over-fetch chunks when dedup is enabled. The vector
     * store returns CHUNKS, not documents — a single rich page may be split
     * into 5–10 chunks that dominate the top of the ranking. Fetching
     * {@code topK_docs × OVERFETCH} chunks gives the dedup pass enough
     * material to find {@code topK_docs} distinct documents in the common
     * case. Capped at {@link #MAX_TOP_K} by the search request itself.
     */
    private static final int DEDUP_OVERFETCH_FACTOR = 8;
    /** Sentinel value for {@code dedupBy} that disables dedup ("give me chunks"). */
    private static final String DEDUP_NONE = "none";
    /**
     * Default field-priority list used to key chunks back to their parent
     * document when no explicit {@code dedupBy} is supplied. {@code source_id}
     * is what the Turing indexer writes as "the document this chunk came from"
     * (the {@code id} field carries a chunk suffix like {@code "-0"} that
     * varies per chunk). {@code url} is the next-most-stable identifier
     * across indexers, and {@code title} / {@code id} are last-ditch fallbacks.
     */
    private static final List<String> DEFAULT_DEDUP_KEYS = List.of("source_id", "url", "title", "id");

    private final TurSNSearchProcess turSNSearchProcess;
    private final TurGenAiContextFactory turGenAiContextFactory;
    private final TurSNSiteLocaleRepository turSNSiteLocaleRepository;
    private final TurSNSiteSearchService turSNSiteSearchService;

    public TurCustomToolSearchHelper(TurSNSearchProcess turSNSearchProcess,
            TurGenAiContextFactory turGenAiContextFactory,
            TurSNSiteLocaleRepository turSNSiteLocaleRepository,
            TurSNSiteSearchService turSNSiteSearchService) {
        this.turSNSearchProcess = turSNSearchProcess;
        this.turGenAiContextFactory = turGenAiContextFactory;
        this.turSNSiteLocaleRepository = turSNSiteLocaleRepository;
        this.turSNSiteSearchService = turSNSiteSearchService;
    }

    /**
     * Similarity search over a site's vector store, with built-in collapse
     * of chunk results back to their parent documents — the headline
     * abstraction over the raw {@code POST /api/ann/{siteName}/search}
     * endpoint: callers ask for {@code topK} <em>documents</em> and we
     * over-fetch chunks under the hood, dedup, and trim.
     *
     * <p>The motivation: the underlying vector store returns CHUNKS, not
     * documents. A rich page split into 8 chunks can dominate the top of
     * the ranking — top-3 chunks then means "3 copies of the same course".
     * The helper hides this by fetching {@code topK × OVERFETCH} chunks,
     * keying each chunk to its source document, and emitting only the
     * first chunk per document up to {@code topK} unique results.
     *
     * <p>Supported parameters (Groovy {@code Map} keys):
     * <ul>
     *   <li>{@code site} — the SN site name (required).</li>
     *   <li>{@code query} — semantic query string (required).</li>
     *   <li>{@code locale} — BCP-47 locale tag; defaults to the site's
     *       default locale.</li>
     *   <li>{@code topK} — number of DOCUMENTS to return when dedup is on,
     *       or number of CHUNKS when dedup is off. Default
     *       {@value #DEFAULT_TOP_K}, capped at {@value #MAX_TOP_K}.</li>
     *   <li>{@code filters} — {@code Map<String, List<String>>} of metadata
     *       facets; e.g. {@code [templateName: ["educacao-executiva"]]}.</li>
     *   <li>{@code templateName} — shorthand: a single string promotes to
     *       {@code filters.templateName = [value]} so scripts can skip the
     *       nested map when they need only this very common filter.</li>
     *   <li>{@code dedupBy} — controls chunk collapse.
     *     <ul>
     *       <li>Omitted (default) → dedup by the first non-blank value of
     *           {@code source_id}, {@code url}, {@code title}, {@code id}.
     *           This matches every shape the Turing indexer + RagFilters
     *           pipeline produce in the wild.</li>
     *       <li>A field name (e.g. {@code "url"}) → dedup by that field
     *           only, falling back through the default keys if absent.</li>
     *       <li>{@code "none"} or {@code false} → no dedup; the raw
     *           {@code topK} chunks come through as-is. Use this for
     *           "give me chunks for highlighting" scenarios.</li>
     *     </ul>
     *   </li>
     * </ul>
     *
     * <p>Each hit map carries every metadata field flattened to top-level
     * plus the reserved {@code id}, {@code score}, and {@code content}.
     *
     * @return list of hit maps (empty list when no results); never
     *         {@code null}.
     * @throws IllegalArgumentException when {@code site} is missing or the
     *         site doesn't exist or its RAG isn't enabled.
     */
    public List<Map<String, Object>> ann(Map<String, Object> params) {
        String site = asString(params, "site");
        if (site == null || site.isBlank()) {
            throw new IllegalArgumentException("turingSearch.ann: 'site' is required");
        }
        String query = asString(params, "query");
        if (query == null || query.isBlank()) {
            throw new IllegalArgumentException("turingSearch.ann: 'query' is required (use '*' for browse-all)");
        }
        int topK = clampTopK(asInt(params, "topK"));
        Locale locale = resolveLocale(asString(params, "locale"), site);
        List<String> dedupKeys = resolveDedupKeys(params.get("dedupBy"));
        boolean dedupEnabled = !dedupKeys.isEmpty();
        // When dedup is on, over-fetch so the trim pass has enough chunks
        // to find {@code topK} distinct documents. When off, topK passes
        // through to the vector store unchanged.
        int searchTopK = dedupEnabled
                ? Math.min(topK * DEDUP_OVERFETCH_FACTOR, MAX_TOP_K)
                : topK;

        TurSNSite snSite = turSNSearchProcess.getSNSite(site)
                .orElseThrow(() -> new IllegalArgumentException(
                        "turingSearch.ann: site '" + site + "' not found"));
        var genAi = snSite.getTurSNSiteGenAi();
        var agent = genAi == null ? null : genAi.getTurAIAgent();
        if (agent == null || agent.getEnabled() != 1 || !agent.isRagEnabled()) {
            throw new IllegalArgumentException(
                    "turingSearch.ann: RAG not enabled for site '" + site + "'");
        }

        String collectionName = resolveCollectionName(snSite, locale);
        TurGenAiContext context = turGenAiContextFactory.build(genAi, collectionName);
        if (!context.isEnabled() || context.getVectorStore() == null) {
            log.warn("[turingSearch.ann] RAG context unavailable: site={} locale={}", site, locale);
            return Collections.emptyList();
        }

        Map<String, List<String>> filters = resolveFilters(params);
        Filter.Expression filterExpr = TurRagFilters.buildFilterExpression(filters);

        SearchRequest.Builder reqBuilder = SearchRequest.builder().query(query).topK(searchTopK);
        if (filterExpr != null) {
            reqBuilder.filterExpression(filterExpr);
        }

        log.info("[turingSearch.ann] site={} locale={} topK={} (search={}) dedupBy={} filters={} query='{}'",
                site, locale, topK, searchTopK,
                dedupEnabled ? dedupKeys : "none", filters, query);

        List<Document> rawHits;
        try {
            rawHits = context.getVectorStore().similaritySearch(reqBuilder.build());
        } catch (Exception e) {
            log.error("[turingSearch.ann] failed: site={} query='{}': {}", site, query, e.getMessage(), e);
            return Collections.emptyList();
        }
        List<Map<String, Object>> hits = toHitList(rawHits == null ? List.of() : rawHits);
        return dedupEnabled
                ? collapseToTopKDocuments(hits, topK, dedupKeys)
                : hits;
    }

    /**
     * Walks the chunk-ranked hit list and keeps only the first hit per
     * unique document key, stopping once {@code limit} distinct documents
     * have been collected. Preserves the input order so the highest-score
     * chunk represents its document in the return.
     */
    // Package-private so TurCustomToolSearchHelperTest can exercise the dedup
    // logic without standing up the full Spring context + a real vector store.
    static List<Map<String, Object>> collapseToTopKDocuments(List<Map<String, Object>> hits,
            int limit, List<String> dedupKeys) {
        if (hits.isEmpty() || limit <= 0) {
            return Collections.emptyList();
        }
        java.util.Set<String> seen = new java.util.LinkedHashSet<>();
        List<Map<String, Object>> out = new java.util.ArrayList<>(Math.min(hits.size(), limit));
        for (Map<String, Object> hit : hits) {
            String key = firstNonBlank(hit, dedupKeys);
            if (key == null || !seen.add(key)) {
                continue;
            }
            out.add(hit);
            if (out.size() >= limit) {
                break;
            }
        }
        return out;
    }

    /**
     * Resolves the {@code dedupBy} parameter into an ordered list of metadata
     * field names to try when keying a chunk to its parent document.
     * <ul>
     *   <li>{@code null} / missing → {@link #DEFAULT_DEDUP_KEYS}.</li>
     *   <li>{@code "none"} / {@link Boolean#FALSE} → empty list (dedup off).</li>
     *   <li>Any other string → that field first, plus the default list as
     *       fallback (so a missing custom key still finds a sensible key).</li>
     *   <li>A {@link List} → those fields in order, dedup'd.</li>
     * </ul>
     */
    // Package-private for unit testing.
    @SuppressWarnings("unchecked")
    static List<String> resolveDedupKeys(Object dedupBy) {
        if (dedupBy == null) {
            return DEFAULT_DEDUP_KEYS;
        }
        if (Boolean.FALSE.equals(dedupBy)) {
            return Collections.emptyList();
        }
        if (dedupBy instanceof String s) {
            String trimmed = s.trim();
            if (trimmed.isEmpty() || DEDUP_NONE.equalsIgnoreCase(trimmed)) {
                return Collections.emptyList();
            }
            // Custom key first, then fall back to defaults — keeps the helper
            // resilient against indexes that omit the requested field.
            List<String> ordered = new java.util.ArrayList<>();
            ordered.add(trimmed);
            for (String def : DEFAULT_DEDUP_KEYS) {
                if (!def.equalsIgnoreCase(trimmed)) {
                    ordered.add(def);
                }
            }
            return ordered;
        }
        if (dedupBy instanceof List<?> list) {
            List<String> ordered = new java.util.ArrayList<>();
            java.util.Set<String> seen = new java.util.HashSet<>();
            for (Object o : list) {
                if (o == null) continue;
                String key = o.toString().trim();
                if (!key.isEmpty() && seen.add(key.toLowerCase(Locale.ROOT))) {
                    ordered.add(key);
                }
            }
            return ordered.isEmpty() ? DEFAULT_DEDUP_KEYS : ordered;
        }
        return DEFAULT_DEDUP_KEYS;
    }

    /**
     * Reads {@code hit[keys[0]]}, then {@code hit[keys[1]]}, … returning the
     * first non-blank string value. Returns {@code null} when every key is
     * absent or blank — the caller treats that as "this hit can't be deduped"
     * and skips it.
     */
    // Package-private for unit testing.
    static String firstNonBlank(Map<String, Object> hit, List<String> keys) {
        for (String key : keys) {
            Object value = hit.get(key);
            if (value == null) continue;
            String s = value.toString().trim();
            if (!s.isEmpty()) {
                return s;
            }
        }
        return null;
    }

    /**
     * Convenience: the SN site search endpoint flavored for Groovy scripts.
     * Currently a placeholder — wire to {@link TurSNSiteSearchService} once
     * a customer tool demands it. Throws {@link UnsupportedOperationException}
     * to make the lazy-implementation choice explicit rather than silently
     * returning empty.
     */
    public List<Map<String, Object>> sn(Map<String, Object> params) {
        throw new UnsupportedOperationException(
                "turingSearch.sn is not implemented yet — use turingSearch.ann for vector search "
                + "or http.getJson('/sn/{site}?...') for the raw site-search endpoint.");
    }

    /* ---------- Internals ---------- */

    @SuppressWarnings("unchecked")
    private static Map<String, List<String>> resolveFilters(Map<String, Object> params) {
        Object filters = params.get("filters");
        Map<String, List<String>> resolved = new LinkedHashMap<>();
        if (filters instanceof Map<?, ?> rawMap) {
            for (Map.Entry<?, ?> entry : rawMap.entrySet()) {
                String key = String.valueOf(entry.getKey());
                Object value = entry.getValue();
                if (value instanceof List<?> list) {
                    List<String> stringList = new ArrayList<>(list.size());
                    for (Object o : list) {
                        if (o != null) stringList.add(String.valueOf(o));
                    }
                    resolved.put(key, stringList);
                } else if (value != null) {
                    resolved.put(key, List.of(String.valueOf(value)));
                }
            }
        }
        // Shorthand: templateName=string → filters.templateName=[string]
        Object templateName = params.get("templateName");
        if (templateName != null && !resolved.containsKey("templateName")) {
            resolved.put("templateName", List.of(String.valueOf(templateName)));
        }
        return resolved;
    }

    private static List<Map<String, Object>> toHitList(List<Document> hits) {
        List<Map<String, Object>> out = new ArrayList<>(hits.size());
        for (Document doc : hits) {
            Map<String, Object> hit = new LinkedHashMap<>();
            if (doc.getMetadata() != null) {
                hit.putAll(doc.getMetadata());
            }
            // Reserved fields come AFTER the metadata so they always win when a
            // metadata key happens to use one of these names.
            hit.put("id", doc.getId());
            hit.put("score", doc.getScore());
            hit.put("content", doc.getText());
            out.add(hit);
        }
        return out;
    }

    private Locale resolveLocale(String requested, String siteName) {
        if (requested == null || requested.isBlank()) {
            return turSNSiteSearchService.resolveDefaultLocale(siteName);
        }
        try {
            return LocaleUtils.toLocale(requested);
        } catch (IllegalArgumentException e) {
            return turSNSiteSearchService.resolveDefaultLocale(siteName);
        }
    }

    private String resolveCollectionName(TurSNSite site, Locale locale) {
        if (locale == null) return null;
        TurSNSiteLocale siteLocale = turSNSiteLocaleRepository.findByTurSNSiteAndLanguage(site, locale);
        return siteLocale != null ? siteLocale.getCore() : null;
    }

    private static int clampTopK(Integer requested) {
        if (requested == null || requested <= 0) return DEFAULT_TOP_K;
        return Math.min(requested, MAX_TOP_K);
    }

    private static String asString(Map<String, Object> params, String key) {
        Object v = params.get(key);
        return v == null ? null : String.valueOf(v);
    }

    private static Integer asInt(Map<String, Object> params, String key) {
        Object v = params.get(key);
        if (v instanceof Number n) return n.intValue();
        if (v instanceof String s) {
            try {
                return Integer.parseInt(s.trim());
            } catch (NumberFormatException ignored) { /* fall through */ }
        }
        return null;
    }
}
