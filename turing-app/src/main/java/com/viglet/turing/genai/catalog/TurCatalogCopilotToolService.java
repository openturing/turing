/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.catalog;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Service;

import com.viglet.turing.genai.catalog.TurCatalogCopilotService.Retrieval;

import lombok.extern.slf4j.Slf4j;

/**
 * T392 / §XX.12 — exposes the grounded catalog retrieval (NL→facet + hybrid
 * search) as a Spring AI {@code @Tool}, so the conversational agent (semantic
 * navigation chat) can "talk to the catalog" with the same conservative grounding
 * and objective ranking the dedicated copilot endpoint uses.
 *
 * <p>Unlike the raw {@code dsl_search} tool — which expects the model to
 * hand-write an Elasticsearch DSL body — {@code catalog_search} takes plain prose
 * and grounds it against the site's declared schema for the model, returning a
 * numbered, cited result list the model can quote as {@code [n]}. The tool reply
 * is the projection {@link TurCatalogCopilotService#retrieve} produces; the actual
 * answer synthesis stays with the calling LLM.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Slf4j
@Service
public class TurCatalogCopilotToolService {

    private final TurCatalogCopilotService copilotService;

    public TurCatalogCopilotToolService(TurCatalogCopilotService copilotService) {
        this.copilotService = copilotService;
    }

    @Tool(name = "catalog_search", description = ".")
    public String catalogSearch(String index, String locale, String query) {
        log.info("[CatalogCopilot Tool] catalog_search called: index={}, locale={}, query={}",
                index, locale, query);
        if (index == null || index.isBlank()) {
            return "Error: 'index' (catalog site name) is required.";
        }
        if (query == null || query.isBlank()) {
            return "Error: 'query' (the natural-language request) is required.";
        }

        Retrieval retrieval = copilotService.retrieve(index, locale, query);
        if (!retrieval.siteFound()) {
            return "Catalog site not found: " + index;
        }
        if (retrieval.contextBlocks().isEmpty()) {
            return "No catalog results matched. Applied filters: "
                    + (retrieval.groundedSummary() == null || retrieval.groundedSummary().isBlank()
                            ? "(none)" : retrieval.groundedSummary());
        }

        StringBuilder sb = new StringBuilder();
        sb.append("Found %d catalog results (showing %d), ranked by objective relevance.\n"
                .formatted(retrieval.totalHits(), retrieval.contextBlocks().size()));
        String summary = retrieval.groundedSummary();
        if (summary != null && !summary.isBlank()) {
            sb.append("Applied filters: ").append(summary).append("\n");
        }
        sb.append("\n").append(String.join("\n\n", retrieval.contextBlocks()));
        sb.append("\n\nAnswer the user using ONLY these results and cite each with its [n].");
        return sb.toString();
    }
}
