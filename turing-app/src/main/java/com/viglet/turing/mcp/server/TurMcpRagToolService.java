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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Service;

import com.viglet.turing.sn.dsl.TurDslQueryRequest;
import com.viglet.turing.sn.dsl.TurDslSearchResponse;
import com.viglet.turing.sn.dsl.TurDslSearchService;
import com.viglet.turing.system.TurLlmSummaryService;
import com.viglet.turing.system.TurLlmSummaryService.SummaryResult;

import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * T248 / §XIII.3 — the {@code rag_answer} MCP tool: hybrid search over one site
 * → an LLM answer grounded in the retrieved documents → the answer plus a
 * citation list, in a single call. This is the "point Claude at your intranet,
 * get cited answers" surface: a client gets enterprise-truth-grounded prose
 * without orchestrating search + prompt + cite itself.
 *
 * <p><b>Reuse, don't fork (§XIII.5)</b>: retrieval routes through the same
 * {@link TurDslSearchService} (multi-engine Solr/Lucene/Elasticsearch, including
 * each site's configured semantic/vector ranking) the search tools use, and the
 * grounded LLM call routes through {@link TurLlmSummaryService} (default LLM
 * resolution, token accounting, and caching). Citations are built from the
 * retrieved hits' own {@code title}/{@code url} source fields, so the answer is
 * always accompanied by an auditable source list even if the model forgets to
 * cite inline.
 *
 * <p>Degrades gracefully: with no documents it says so; with no default LLM
 * configured it returns the ranked sources (still useful) instead of failing.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
@Slf4j
@Service
public class TurMcpRagToolService {

    private static final int DEFAULT_SOURCES = 5;
    private static final int MAX_SOURCES = 20;
    private static final int SNIPPET_LEN = 280;
    private static final String FULL_TEXT_FIELD = "_text_";
    private static final String DEFAULT_LOCALE = "en";

    private static final String GROUNDING_SYSTEM_PROMPT = """
            You are Viglet Turing ES, an enterprise search assistant. Answer the user's \
            QUESTION using ONLY the numbered SOURCES provided in the user message. Cite the \
            sources you use inline with their bracketed number, e.g. [1], [2]. If the SOURCES \
            do not contain the answer, say clearly that you could not find it in the indexed \
            content — do not invent facts. Be concise and factual.""";

    private final TurDslSearchService turDslSearchService;
    private final TurLlmSummaryService llmSummaryService;
    private final ObjectMapper objectMapper = JsonMapper.builder().build();

    public TurMcpRagToolService(TurDslSearchService turDslSearchService,
            TurLlmSummaryService llmSummaryService) {
        this.turDslSearchService = turDslSearchService;
        this.llmSummaryService = llmSummaryService;
    }

    @Tool(name = "rag_answer", description = ".")
    public String ragAnswer(String site, String locale, String question, Integer maxSources) {
        if (site == null || site.isBlank()) {
            return "Error: 'site' parameter is required (use list_sites to discover sites).";
        }
        if (question == null || question.isBlank()) {
            return "Error: 'question' parameter is required.";
        }
        String searchLocale = (locale != null && !locale.isBlank()) ? locale : DEFAULT_LOCALE;
        int rows = clamp(maxSources);

        List<TurDslSearchResponse.Hit> hits = retrieve(site, searchLocale, question, rows);
        if (hits.isEmpty()) {
            return "No documents found for this question on site '%s'. The site may be empty, "
                    .formatted(site) + "the locale may be wrong, or the query may not match any content.";
        }

        List<String> citations = buildCitations(hits);
        String sourcesBlock = buildSourcesBlock(hits);

        if (!llmSummaryService.isAvailable()) {
            return "No default LLM is configured, so I can't compose a grounded answer. "
                    + "Here are the most relevant sources for your question:\n\n" + sourcesBlock;
        }

        String data = "QUESTION:\n" + question + "\n\nSOURCES:\n" + sourcesBlock;
        String cacheKey = "mcp-rag:" + site + ":" + searchLocale + ":" + question.trim().hashCode();
        SummaryResult result = llmSummaryService.generate(cacheKey, data, GROUNDING_SYSTEM_PROMPT, false);

        if (!result.success() || result.content() == null) {
            log.warn("[MCP] rag_answer LLM call failed for site={}: {}", site, result.error());
            return "Could not compose a grounded answer (" + result.error() + "). "
                    + "Most relevant sources:\n\n" + sourcesBlock;
        }

        return result.content() + "\n\nSources:\n" + String.join("\n", citations);
    }

    private List<TurDslSearchResponse.Hit> retrieve(String site, String locale, String question, int rows) {
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("query", Map.of("match", Map.of(FULL_TEXT_FIELD, question)));
            body.put("size", rows);
            TurDslQueryRequest request = objectMapper.readValue(
                    objectMapper.writeValueAsString(body), TurDslQueryRequest.class);
            Optional<TurDslSearchResponse> response = turDslSearchService.search(site, locale, request);
            if (response.isEmpty() || response.get().hits() == null
                    || response.get().hits().hits() == null) {
                return List.of();
            }
            return response.get().hits().hits();
        } catch (Exception e) {
            log.error("[MCP] rag_answer retrieval failed for site={}", site, e);
            return List.of();
        }
    }

    /** Human-facing citation lines: "[n] title — url". */
    private List<String> buildCitations(List<TurDslSearchResponse.Hit> hits) {
        List<String> citations = new ArrayList<>();
        int n = 1;
        for (TurDslSearchResponse.Hit hit : hits) {
            String title = title(hit);
            String url = url(hit);
            citations.add("[" + n + "] " + title + (url.isBlank() ? "" : " — " + url));
            n++;
        }
        return citations;
    }

    /** The numbered SOURCES block fed to the LLM: title, url, and a snippet per hit. */
    private String buildSourcesBlock(List<TurDslSearchResponse.Hit> hits) {
        StringBuilder sb = new StringBuilder();
        int n = 1;
        for (TurDslSearchResponse.Hit hit : hits) {
            sb.append('[').append(n).append("] ").append(title(hit));
            String url = url(hit);
            if (!url.isBlank()) {
                sb.append(" (").append(url).append(')');
            }
            sb.append('\n').append(snippet(hit)).append("\n\n");
            n++;
        }
        return sb.toString().strip();
    }

    private String title(TurDslSearchResponse.Hit hit) {
        Object t = source(hit).get("title");
        if (t != null && !t.toString().isBlank()) {
            return t.toString();
        }
        return "Document " + hit.id();
    }

    private String url(TurDslSearchResponse.Hit hit) {
        Object u = source(hit).get("url");
        return u != null ? u.toString() : "";
    }

    private String snippet(TurDslSearchResponse.Hit hit) {
        Map<String, Object> source = source(hit);
        for (String field : List.of("text", "abstract", "description", "body")) {
            Object v = source.get(field);
            if (v != null && !v.toString().isBlank()) {
                return truncate(v.toString());
            }
        }
        // Fall back to the first non-blank string field.
        for (Object v : source.values()) {
            if (v instanceof String s && !s.isBlank()) {
                return truncate(s);
            }
        }
        return "(no text preview)";
    }

    private Map<String, Object> source(TurDslSearchResponse.Hit hit) {
        return hit.source() != null ? hit.source() : Map.of();
    }

    private String truncate(String s) {
        String clean = s.strip().replaceAll("\\s+", " ");
        return clean.length() <= SNIPPET_LEN ? clean : clean.substring(0, SNIPPET_LEN) + "…";
    }

    private int clamp(Integer maxSources) {
        if (maxSources == null || maxSources < 1) {
            return DEFAULT_SOURCES;
        }
        return Math.min(maxSources, MAX_SOURCES);
    }
}
