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

import java.util.List;

/**
 * T392 / §XX.12 — the response of the grounded conversational catalog copilot:
 * a natural-language {@code answer} grounded strictly in the catalog index,
 * together with the {@code citations} that answer is built from (the
 * objectively-ranked hits) and a human-readable summary of the structured
 * {@code groundedQuery} the prose was parsed into.
 *
 * <p>{@code available} is {@code false} (with {@code error} set) when the copilot
 * cannot run — typically because no default LLM is configured. Even when the LLM
 * answer step fails, {@code citations} may still be populated so the client can
 * show the matched catalog results without a synthesized answer.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
public record TurCatalogCopilotResult(
        boolean available,
        String answer,
        List<TurCatalogCitation> citations,
        String groundedQuerySummary,
        long totalHits,
        String error) {

    /** A successful grounded answer. */
    public static TurCatalogCopilotResult ok(String answer, List<TurCatalogCitation> citations,
            String groundedQuerySummary, long totalHits) {
        return new TurCatalogCopilotResult(true, answer, citations, groundedQuerySummary, totalHits, null);
    }

    /** The copilot could not run at all (e.g. no default LLM, site missing). */
    public static TurCatalogCopilotResult error(String error) {
        return new TurCatalogCopilotResult(false, null, List.of(), null, 0L, error);
    }

    /**
     * Retrieval succeeded and citations are available, but the answer step could
     * not produce text (e.g. the LLM call failed). The client can still render the
     * matched results.
     */
    public static TurCatalogCopilotResult degraded(List<TurCatalogCitation> citations,
            String groundedQuerySummary, long totalHits, String error) {
        return new TurCatalogCopilotResult(true, null, citations, groundedQuerySummary, totalHits, error);
    }
}
