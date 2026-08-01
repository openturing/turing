/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.catalog.planning;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;

/**
 * T842 / §LXII.11 — multi-query fan-out decomposition. A multi-hop or comparative
 * question ("Anthropic vs OpenAI models under $10", "price and context window of
 * Claude") is one question but several retrievals: Perplexity's recall advantage on
 * such questions is mostly fan-out, not model quality. This splits the question into
 * bounded sub-queries whose recall the caller unions through
 * {@link com.viglet.turing.genai.rag.TurRagRrf#fuseKeyed} before rerank.
 *
 * <p>Deterministic and fail-open by design — it splits on explicit comparison /
 * conjunction markers ({@code vs}, {@code versus}, {@code compared to}, {@code and},
 * {@code or}, commas) and always keeps the whole question for holistic recall. A
 * question with no such marker yields a single query, so the caller stays on the
 * single-pass path. No LLM call; CI-testable. (An LLM decomposition could later back
 * the same seam for subtler multi-hop questions.)
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Service
public class TurQueryFanOut {

    /** Explicit markers that signal a question worth splitting into sub-queries. */
    private static final Pattern SPLIT = Pattern.compile(
            "(?i)\\s*(?:\\bvs\\.?\\b|\\bversus\\b|\\bcompared to\\b|\\band\\b|\\bor\\b|,)\\s*");

    /** Sub-query fragments shorter than this (after trim) are dropped as noise. */
    private static final int MIN_PART_CHARS = 2;

    /**
     * Decomposes {@code question} into at most {@code maxQueries} sub-queries. The
     * whole question is always first (holistic recall); marker-delimited parts
     * follow, de-duplicated. Returns a single-element list (just the question) when
     * there is no marker to split on or {@code maxQueries <= 1}.
     *
     * @param question   the user's natural-language question
     * @param maxQueries the fan-out bound (e.g. {@code planning.max-passes})
     * @return 1..{@code maxQueries} sub-queries, never {@code null}
     */
    public List<String> decompose(String question, int maxQueries) {
        String whole = question == null ? "" : question.strip();
        if (whole.isEmpty()) {
            return List.of();
        }
        if (maxQueries <= 1) {
            return List.of(whole);
        }
        LinkedHashSet<String> queries = new LinkedHashSet<>();
        queries.add(whole);
        for (String part : SPLIT.split(whole)) {
            String fragment = part.strip();
            if (fragment.length() >= MIN_PART_CHARS && !fragment.equalsIgnoreCase(whole)) {
                queries.add(fragment);
            }
        }
        return queries.stream().limit(maxQueries).toList();
    }
}
