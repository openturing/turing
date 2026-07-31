/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.transcription;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;

/**
 * T847 / §LXIII.2 — optional transcription context: free-form {@code prompt}
 * text and a bounded domain {@code keywords} list, handed to backends that
 * accept them and <b>silently ignored</b> by those that don't (a whisper server
 * gets the prompt, only {@code gpt-transcribe} gets the keywords — see
 * {@link TurTranscriptionModelFamily}).
 *
 * <p>Hints resolve globally (Global Settings / {@code turing.transcription.*})
 * and can be overridden per request via {@link #merge(TurTranscriptionHints)}.
 * The differentiating source is Turing's own curated vocabulary — the Thesaurus
 * (Block AQ) via {@link TurTranscriptionKeywordSource} — which is what stops
 * product names, model ids and jargon from being transcribed phonetically.
 *
 * <p>The canonical constructor normalizes: blanks dropped, values trimmed,
 * keywords de-duplicated case-insensitively (first spelling wins) and capped at
 * {@link #MAX_KEYWORDS}. A keyword list is a hint, not a dictionary dump.
 *
 * @param prompt   free-form context sentence(s), or {@code null}
 * @param keywords domain terms; never {@code null} (empty when unset)
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
public record TurTranscriptionHints(String prompt, List<String> keywords) {

    /** Hard cap on the keyword list — a hint, not a dictionary dump. */
    public static final int MAX_KEYWORDS = 100;

    /** Hard cap on the free-form prompt, so a runaway setting can't bloat every request. */
    public static final int MAX_PROMPT_CHARS = 2000;

    private static final TurTranscriptionHints NONE = new TurTranscriptionHints(null, List.of());

    public TurTranscriptionHints {
        prompt = StringUtils.abbreviate(StringUtils.trimToNull(prompt), MAX_PROMPT_CHARS);
        keywords = normalizeKeywords(keywords);
    }

    /** The empty hints — what every caller that has nothing to say passes. */
    public static TurTranscriptionHints none() {
        return NONE;
    }

    /** Hints carrying only a keyword list (the Thesaurus-sourced case). */
    public static TurTranscriptionHints ofKeywords(List<String> keywords) {
        return new TurTranscriptionHints(null, keywords);
    }

    /** True when there is nothing to send. */
    public boolean isEmpty() {
        return prompt == null && keywords.isEmpty();
    }

    /**
     * Per-request hints layered over these global ones: a non-blank
     * {@code override} prompt replaces this prompt, and a non-empty override
     * keyword list replaces (not appends to) this one — an explicit per-request
     * vocabulary is a decision, not an addition. A {@code null} override is a
     * no-op.
     */
    public TurTranscriptionHints merge(TurTranscriptionHints override) {
        if (override == null || override.isEmpty()) {
            return this;
        }
        return new TurTranscriptionHints(
                override.prompt() != null ? override.prompt() : prompt,
                override.keywords().isEmpty() ? keywords : override.keywords());
    }

    /** Trim, drop blanks, de-duplicate case-insensitively, cap at {@link #MAX_KEYWORDS}. */
    private static List<String> normalizeKeywords(List<String> raw) {
        if (raw == null || raw.isEmpty()) {
            return List.of();
        }
        Set<String> seen = new LinkedHashSet<>();
        List<String> out = new ArrayList<>();
        for (String keyword : raw) {
            String trimmed = StringUtils.trimToNull(keyword);
            if (trimmed == null || !seen.add(trimmed.toLowerCase(Locale.ROOT))) {
                continue;
            }
            out.add(trimmed);
            if (out.size() == MAX_KEYWORDS) {
                break;
            }
        }
        return List.copyOf(out);
    }
}
