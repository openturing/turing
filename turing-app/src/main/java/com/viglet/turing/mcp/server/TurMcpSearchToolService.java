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

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Service;

import com.viglet.turing.genai.tool.TurDslToolService;

import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * T247 / §XIII.3 — the read search surface published over MCP, with the
 * spec-friendly tool names an external client expects ({@code list_sites},
 * {@code get_site_fields}, {@code search_site}, {@code get_document},
 * {@code similar_documents}, {@code facet_search}, {@code autocomplete}).
 *
 * <p><b>Reuse, don't fork (§XIII.5)</b>: every tool delegates to the
 * production-tested {@link TurDslToolService} — the same service the internal
 * Semantic-Navigation agent uses. The full-text / facet / similarity tools just
 * build the Elasticsearch-DSL query body programmatically (via a {@link Map} so
 * user input is JSON-escaped safely) and hand it to {@code dsl_search}, so all
 * the result formatting, multi-engine routing (Solr / Lucene / Elasticsearch),
 * and locale handling come along unchanged. This service therefore adds a clean
 * public vocabulary, not a second search implementation.
 *
 * <p>Descriptions are supplied through the {@code prompts/tools/mcp/*.md} files
 * and applied by {@link com.viglet.turing.genai.tool.TurToolCallbackPipeline}.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
@Slf4j
@Service
public class TurMcpSearchToolService {

    private static final int DEFAULT_ROWS = 10;
    private static final int MAX_ROWS = 50;
    private static final int DEFAULT_FACET_SIZE = 20;
    /** Virtual full-text field understood by every Turing search-engine plugin. */
    private static final String FULL_TEXT_FIELD = "_text_";

    private final TurDslToolService dslToolService;
    private final ObjectMapper objectMapper = JsonMapper.builder().build();

    public TurMcpSearchToolService(TurDslToolService dslToolService) {
        this.dslToolService = dslToolService;
    }

    // ==================== list_sites ====================

    @Tool(name = "list_sites", description = ".")
    public String listSites(String namePattern) {
        return dslToolService.listIndices(namePattern);
    }

    // ==================== get_site_fields ====================

    @Tool(name = "get_site_fields", description = ".")
    public String getSiteFields(String site) {
        return dslToolService.getMappings(site);
    }

    // ==================== search_site ====================

    @Tool(name = "search_site", description = ".")
    public String searchSite(String site, String locale, String query, Integer maxResults) {
        if (site == null || site.isBlank()) {
            return "Error: 'site' parameter is required.";
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("query", matchOrAll(query));
        body.put("size", clampRows(maxResults));
        return runDsl(site, locale, body, "search_site");
    }

    // ==================== get_document ====================

    @Tool(name = "get_document", description = ".")
    public String getDocument(String site, String locale, String documentId) {
        return dslToolService.getDocument(site, locale, documentId);
    }

    // ==================== similar_documents ====================

    @Tool(name = "similar_documents", description = ".")
    public String similarDocuments(String site, String locale, String referenceText, Integer maxResults) {
        if (site == null || site.isBlank()) {
            return "Error: 'site' parameter is required.";
        }
        if (referenceText == null || referenceText.isBlank()) {
            return "Error: 'referenceText' parameter is required (the text to find neighbours of).";
        }
        // Similarity over the seed text. On a site whose engine has semantic /
        // vector search configured, this rides that capability; otherwise it is
        // a full-text more-like-this. Honest either way: it returns the most
        // similar indexed documents to the supplied text.
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("query", Map.of("match", Map.of(FULL_TEXT_FIELD, referenceText)));
        body.put("size", clampRows(maxResults));
        return runDsl(site, locale, body, "similar_documents");
    }

    // ==================== facet_search ====================

    @Tool(name = "facet_search", description = ".")
    public String facetSearch(String site, String locale, String query, String facetField) {
        if (site == null || site.isBlank()) {
            return "Error: 'site' parameter is required.";
        }
        if (facetField == null || facetField.isBlank()) {
            return "Error: 'facetField' parameter is required (the field to drill down by).";
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("query", matchOrAll(query));
        body.put("size", 0);
        body.put("aggs", Map.of("facet",
                Map.of("terms", Map.of("field", facetField, "size", DEFAULT_FACET_SIZE))));
        return runDsl(site, locale, body, "facet_search");
    }

    // ==================== autocomplete ====================

    @Tool(name = "autocomplete", description = ".")
    public String autocomplete(String site, String locale, String prefix) {
        return dslToolService.suggest(site, locale, prefix);
    }

    // ==================== helpers ====================

    private Map<String, Object> matchOrAll(String query) {
        if (query == null || query.isBlank()) {
            return Map.of("match_all", Map.of());
        }
        return Map.of("match", Map.of(FULL_TEXT_FIELD, query));
    }

    private int clampRows(Integer maxResults) {
        if (maxResults == null || maxResults < 1) {
            return DEFAULT_ROWS;
        }
        return Math.min(maxResults, MAX_ROWS);
    }

    private String runDsl(String site, String locale, Map<String, Object> body, String tool) {
        try {
            String json = objectMapper.writeValueAsString(body);
            return dslToolService.search(site, locale, json);
        } catch (Exception e) {
            log.error("[MCP Tool] {} failed for site={}", tool, site, e);
            return "Error executing " + tool + ": " + e.getMessage();
        }
    }
}
