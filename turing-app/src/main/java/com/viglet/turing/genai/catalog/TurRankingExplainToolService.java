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

import java.util.Map;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Service;

import com.viglet.turing.genai.catalog.TurCatalogCopilotService.Retrieval;

import lombok.extern.slf4j.Slf4j;

/**
 * T444 / §XXIII.3 — conversational glass-box ranking.
 *
 * <p>Exposes the T389 objective-ranking breakdown as a Spring AI {@code @Tool} so
 * the agent can answer "why is this result #1?" with the REAL signals — the BM25
 * (lexical) rank, the vector (semantic) rank, the HYBRID_RRF fused rank + score
 * (T383), and the Block N reranker's effect — rather than a hand-wave. It re-runs
 * the same grounded retrieval the catalog copilot uses ({@link
 * TurCatalogCopilotService#retrieve}), which attaches the per-result ranking
 * explanation, then finds the asked-about document and narrates its breakdown.
 *
 * <p>Signals only ever describe objective ranking (ranks + RRF score + rerank
 * delta), never any commercial weight — a trust feature for regulated/enterprise
 * procurement where "the AI said so" is not an acceptable answer.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Slf4j
@Service
public class TurRankingExplainToolService {

    private final TurCatalogCopilotService copilotService;

    public TurRankingExplainToolService(TurCatalogCopilotService copilotService) {
        this.copilotService = copilotService;
    }

    @Tool(name = "explain_ranking", description = ".")
    public String explainRanking(String index, String locale, String query, String documentId) {
        log.info("[GlassBox Tool] explain_ranking called: index={}, locale={}, query={}, doc={}",
                index, locale, query, documentId);
        if (index == null || index.isBlank()) {
            return "Error: 'index' (site name) is required.";
        }
        if (query == null || query.isBlank()) {
            return "Error: 'query' (the search the user ran) is required.";
        }

        Retrieval retrieval = copilotService.retrieve(index, locale, query);
        if (!retrieval.siteFound()) {
            return "Site not found: " + index;
        }
        if (retrieval.citations().isEmpty()) {
            return "No results matched that query, so there is nothing to explain. "
                    + "Try the exact query the user ran.";
        }

        TurCatalogCitation target = findTarget(retrieval, documentId);
        if (target == null) {
            return "Document '%s' is not in the top results for that query (top %d shown). %s"
                    .formatted(documentId, retrieval.citations().size(), availableList(retrieval));
        }
        return narrate(target);
    }

    /** Match by document id; with a blank id, explain the #1 result. */
    private static TurCatalogCitation findTarget(Retrieval retrieval, String documentId) {
        if (documentId == null || documentId.isBlank()) {
            return retrieval.citations().get(0);
        }
        return retrieval.citations().stream()
                .filter(c -> documentId.equals(c.id()))
                .findFirst()
                .orElse(null);
    }

    private static String availableList(Retrieval retrieval) {
        StringBuilder sb = new StringBuilder("Available results: ");
        for (TurCatalogCitation c : retrieval.citations()) {
            sb.append("[#%d %s] ".formatted(c.rank(), c.id()));
        }
        return sb.toString().trim();
    }

    private static String narrate(TurCatalogCitation c) {
        Map<String, Object> explain = c.rankingExplanation();
        StringBuilder sb = new StringBuilder();
        sb.append("Ranking breakdown for \"%s\" (id %s), currently at position #%d:\n"
                .formatted(c.title(), c.id(), c.rank()));
        if (explain == null || explain.isEmpty()) {
            sb.append("- This site uses lexical (keyword-only / BM25) ranking, so the order "
                    + "reflects keyword match strength alone — there is no hybrid or reranking "
                    + "stage to break down. The result placed at #").append(c.rank())
                    .append(" by keyword relevance.");
            return sb.toString();
        }
        appendSignal(sb, "Pipeline", explain.get("pipeline"));
        appendRank(sb, "Keyword (BM25) rank", explain.get("lexicalRank"));
        appendRank(sb, "Semantic (vector) rank", explain.get("semanticRank"));
        appendRank(sb, "Fused (RRF) rank", explain.get("fusedRank"));
        if (explain.get("rrfScore") != null) {
            sb.append("- RRF fused score: ").append(explain.get("rrfScore"))
                    .append(" (reciprocal-rank fusion of the two ranks above, k=60)\n");
        }
        appendRank(sb, "Final position", explain.get("finalRank"));
        if (explain.get("rerankStrategy") != null) {
            sb.append("- Reranker: ").append(explain.get("rerankStrategy"));
            Object delta = explain.get("rerankerDelta");
            if (delta instanceof Number n) {
                sb.append(describeDelta(n.intValue()));
            }
            sb.append("\n");
        }
        sb.append("\nNarrate these objective signals to the user in plain language; "
                + "do not invent scores that are not listed here.");
        return sb.toString();
    }

    private static void appendSignal(StringBuilder sb, String label, Object value) {
        if (value != null) {
            sb.append("- ").append(label).append(": ").append(value).append("\n");
        }
    }

    private static void appendRank(StringBuilder sb, String label, Object value) {
        if (value != null) {
            sb.append("- ").append(label).append(": #").append(value).append("\n");
        }
    }

    private static String describeDelta(int delta) {
        if (delta > 0) {
            return " (moved it UP %d position(s) from the fused order)".formatted(delta);
        }
        if (delta < 0) {
            return " (moved it DOWN %d position(s) from the fused order)".formatted(-delta);
        }
        return " (kept its fused position)";
    }
}
