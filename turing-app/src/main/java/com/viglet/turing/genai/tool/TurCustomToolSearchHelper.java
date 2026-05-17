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

import java.net.URI;
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
import com.viglet.turing.commons.se.TurSEParameters;
import com.viglet.turing.commons.sn.TurSNConfig;
import com.viglet.turing.commons.sn.bean.TurSNSearchParams;
import com.viglet.turing.commons.sn.bean.TurSNSiteSearchBean;
import com.viglet.turing.commons.sn.bean.TurSNSiteSearchDocumentBean;
import com.viglet.turing.commons.sn.search.TurSNFilterQueryOperator;
import com.viglet.turing.commons.sn.search.TurSNSiteSearchContext;
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
     * Lexical (Solr/Elastic) site search exposed to Custom Tool Groovy
     * scripts as {@code turingSearch.sn(...)}. Twin of {@link #ann(Map)}
     * for cases where vector similarity isn't the right tool —
     * exact-term lookups, faceted filtering on structured fields, sort
     * by a numeric/date field, pagination of a known corpus.
     *
     * <p>Runs in-process against {@link TurSNSearchProcess#search}, the
     * same code path the public {@code /api/sn/{site}/search} endpoint
     * uses — no HTTP self-call.
     *
     * <p>Supported parameters (Groovy {@code Map} keys):
     * <ul>
     *   <li>{@code site} — SN site name (required).</li>
     *   <li>{@code query} / {@code q} — search query (default {@code "*"}
     *       for browse-all).</li>
     *   <li>{@code locale} — BCP-47 tag; defaults to the site's default
     *       locale.</li>
     *   <li>{@code rows} — page size; default {@value #DEFAULT_TOP_K},
     *       capped at {@value #MAX_TOP_K}.</li>
     *   <li>{@code page} / {@code p} — 1-indexed page number; default 1.</li>
     *   <li>{@code sort} — Solr sort expression (e.g. {@code "preco asc"});
     *       default {@code "relevance"}.</li>
     *   <li>{@code fq} — {@code List<String>} of filter queries
     *       ({@code "field:value"}) joined with the {@code fqOp} operator.</li>
     *   <li>{@code fqAnd} / {@code fqOr} — explicit AND / OR filter pools.</li>
     *   <li>{@code fqOp} / {@code fqiOp} — top-level / per-item operator,
     *       one of {@code "AND"} / {@code "OR"} / {@code "NONE"}.</li>
     *   <li>{@code fl} — {@code List<String>} of field names to return
     *       (Solr {@code fl} param); default returns all stored fields.</li>
     *   <li>{@code templateName} — shorthand: a single string promotes to
     *       {@code fq=["templateName:<value>"]} so callers can skip the
     *       array boilerplate for the most common filter.</li>
     * </ul>
     *
     * <h3>Return shape</h3>
     * One {@link Map} per hit, structured like {@link #ann(Map)} returns:
     * <ul>
     *   <li>Every {@code fields[*]} entry flattened to the top level —
     *       {@code hit.title}, {@code hit.preco}, etc. work directly in
     *       Groovy without a nested unwrap.</li>
     *   <li>{@code id} — convenience alias of the document's id field.</li>
     *   <li>{@code source} — search-engine plugin source identifier
     *       (Solr, ES, etc.).</li>
     *   <li>{@code elevate} — true when the result is pinned by
     *       elevation rules; useful for the script to surface "promoted"
     *       hits differently.</li>
     * </ul>
     *
     * @return list of hit maps (empty when no results); never {@code null}.
     * @throws IllegalArgumentException when {@code site} is missing or the
     *         site doesn't exist.
     * @since 2026.2.7
     */
    public List<Map<String, Object>> sn(Map<String, Object> params) {
        String site = asString(params, "site");
        if (site == null || site.isBlank()) {
            throw new IllegalArgumentException("turingSearch.sn: 'site' is required");
        }
        String query = firstNonBlankString(asString(params, "query"), asString(params, "q"));
        if (query == null || query.isBlank()) {
            // Convenience: SN search default is browse-all when q is empty.
            // Match TurSNSearchParams' own default rather than forcing the
            // caller to remember the convention.
            query = "*";
        }
        if (!turSNSearchProcess.getSNSite(site).isPresent()) {
            throw new IllegalArgumentException("turingSearch.sn: site '" + site + "' not found");
        }

        TurSNSearchParams snParams = buildSnSearchParams(params, site, query);

        // Synthetic URI: only used for facet/pagination link construction
        // inside the response, which Custom Tool scripts never consume.
        // The /api/v2/sn/{site} shape mirrors the real endpoint so any
        // future code path that inspects it sees a coherent value.
        URI uri = URI.create("/api/v2/sn/" + site + "?q=" + urlEncode(query));
        TurSNSiteSearchContext context = new TurSNSiteSearchContext(
                site, new TurSNConfig(), new TurSEParameters(snParams),
                snParams.getLocale(), uri);

        log.info("[turingSearch.sn] site={} locale={} rows={} page={} sort={} fq={} query='{}'",
                site, snParams.getLocale(), snParams.getRows(), snParams.getP(),
                snParams.getSort(), snParams.getFq(), query);

        TurSNSiteSearchBean searchBean;
        try {
            searchBean = turSNSearchProcess.search(context);
        } catch (Exception e) {
            log.error("[turingSearch.sn] failed: site={} query='{}': {}",
                    site, query, e.getMessage(), e);
            return Collections.emptyList();
        }
        if (searchBean == null || searchBean.getResults() == null
                || searchBean.getResults().getDocument() == null) {
            return Collections.emptyList();
        }
        return toSnHitList(searchBean.getResults().getDocument());
    }

    /**
     * Builds {@link TurSNSearchParams} from the Groovy parameter map.
     * Maps Groovy-friendly keys ({@code rows}, {@code page}, {@code sort})
     * to the underlying bean fields and routes the {@code templateName}
     * shorthand into the default filter-query pool.
     */
    private TurSNSearchParams buildSnSearchParams(Map<String, Object> params,
            String site, String query) {
        TurSNSearchParams snParams = new TurSNSearchParams();
        snParams.setQ(query);
        Integer rows = asInt(params, "rows");
        snParams.setRows(rows == null ? DEFAULT_TOP_K : Math.min(rows, MAX_TOP_K));
        Integer page = firstNonNull(asInt(params, "page"), asInt(params, "p"));
        if (page != null && page > 0) snParams.setP(page);
        String sort = asString(params, "sort");
        if (sort != null && !sort.isBlank()) snParams.setSort(sort);
        snParams.setLocale(resolveLocale(asString(params, "locale"), site));

        List<String> fq = asStringList(params.get("fq"));
        // templateName=<v> shorthand → fq+="templateName:<v>". Mirrors the
        // ann() helper's same-named shortcut for muscle-memory parity.
        Object templateName = params.get("templateName");
        if (templateName != null) {
            String tn = templateName.toString().trim();
            if (!tn.isEmpty()) {
                if (fq == null) fq = new ArrayList<>();
                else fq = new ArrayList<>(fq);
                fq.add("templateName:" + tn);
            }
        }
        if (fq != null && !fq.isEmpty()) snParams.setFq(fq);

        List<String> fqAnd = asStringList(params.get("fqAnd"));
        if (fqAnd != null && !fqAnd.isEmpty()) snParams.setFqAnd(fqAnd);
        List<String> fqOr = asStringList(params.get("fqOr"));
        if (fqOr != null && !fqOr.isEmpty()) snParams.setFqOr(fqOr);

        TurSNFilterQueryOperator fqOp = asFqOperator(params.get("fqOp"));
        if (fqOp != null) snParams.setFqOp(fqOp);
        TurSNFilterQueryOperator fqiOp = asFqOperator(params.get("fqiOp"));
        if (fqiOp != null) snParams.setFqiOp(fqiOp);

        List<String> fl = asStringList(params.get("fl"));
        if (fl != null && !fl.isEmpty()) snParams.setFl(fl);

        return snParams;
    }

    /**
     * Flattens a {@link TurSNSiteSearchDocumentBean} list into the same
     * hit-map shape {@link #ann(Map)} emits, so a Groovy script can read
     * {@code hit.title} regardless of which search surface it called.
     */
    static List<Map<String, Object>> toSnHitList(List<TurSNSiteSearchDocumentBean> docs) {
        if (docs == null || docs.isEmpty()) return Collections.emptyList();
        List<Map<String, Object>> out = new ArrayList<>(docs.size());
        for (TurSNSiteSearchDocumentBean doc : docs) {
            Map<String, Object> hit = new LinkedHashMap<>();
            if (doc.getFields() != null) {
                hit.putAll(doc.getFields());
            }
            // Reserved fields come AFTER metadata so they always win when a
            // stored field happens to collide.
            if (doc.getFields() != null && doc.getFields().containsKey("id")) {
                hit.put("id", doc.getFields().get("id"));
            }
            hit.put("source", doc.getSource());
            hit.put("elevate", doc.isElevate());
            out.add(hit);
        }
        return out;
    }

    private static String firstNonBlankString(String... values) {
        for (String v : values) {
            if (v != null && !v.isBlank()) return v;
        }
        return null;
    }

    private static <T> T firstNonNull(T a, T b) {
        return a != null ? a : b;
    }

    @SuppressWarnings("unchecked")
    private static List<String> asStringList(Object raw) {
        if (raw == null) return null;
        if (raw instanceof List<?> list) {
            List<String> out = new ArrayList<>(list.size());
            for (Object o : list) {
                if (o != null) out.add(String.valueOf(o));
            }
            return out;
        }
        if (raw instanceof String s) {
            // Single string → singleton list. Groovy callers often pass
            // a bare string for the common case ({@code fq: "area:tech"}).
            String trimmed = s.trim();
            return trimmed.isEmpty() ? null : List.of(trimmed);
        }
        return null;
    }

    private static TurSNFilterQueryOperator asFqOperator(Object raw) {
        if (raw == null) return null;
        if (raw instanceof TurSNFilterQueryOperator op) return op;
        String s = raw.toString().trim().toUpperCase(Locale.ROOT);
        if (s.isEmpty()) return null;
        try {
            return TurSNFilterQueryOperator.valueOf(s);
        } catch (IllegalArgumentException e) {
            log.warn("[turingSearch.sn] unknown fqOp/fqiOp value '{}' — falling back to NONE", raw);
            return TurSNFilterQueryOperator.NONE;
        }
    }

    private static String urlEncode(String value) {
        try {
            return java.net.URLEncoder.encode(value, java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception e) {
            return value;
        }
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
