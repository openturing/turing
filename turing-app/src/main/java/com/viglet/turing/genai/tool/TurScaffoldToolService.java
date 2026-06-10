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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;

/**
 * §VII.4 / T316 — the three deterministic "scaffold" helpers that the platform
 * skills (`scaffold-markdown`, `scaffold-quote-extraction`, `scaffold-mindmap`)
 * declare. Extracted from the repeated Education-customer Custom Tools into
 * native, dependency-free tools so those skills reference real callables.
 *
 * <p>All three are pure text transforms (no I/O, no external deps): a beat
 * scaffold builder, a quote-picking heuristic, and a Mermaid mind-map builder.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
@Slf4j
@Service
public class TurScaffoldToolService {

    private static final int DEFAULT_QUOTE_MIN_LENGTH = 40;
    private static final int MIND_MAP_TOP_N = 8;
    private static final int MIN_TOKEN_LENGTH = 3;

    /** Compact PT+EN stop-word set for the mind-map term filter. */
    private static final Set<String> STOP_WORDS = Set.of(
            // pt
            "a", "o", "e", "de", "da", "do", "das", "dos", "um", "uma", "que", "para", "com",
            "por", "no", "na", "nos", "nas", "se", "as", "os", "ao", "aos", "em", "ou", "como",
            "mais", "mas", "foi", "ser", "sua", "seu", "sao", "são", "este", "esta", "isso",
            // en (duplicates with the pt set above — "a", "as" — omitted: Set.of rejects them)
            "the", "of", "and", "to", "in", "is", "that", "for", "with", "on", "are",
            "be", "by", "an", "it", "or", "from", "this", "was", "were", "which", "their", "its");

    /**
     * Build a beat-structured markdown scaffold for the persona to fill in.
     *
     * @param title the document title (blank → "Untitled")
     * @param beats comma-separated beat labels; blank → Hook / Development /
     *              Call to Action default sequence
     */
    @Tool(name = "scaffold_markdown", description = ".")
    public String scaffoldMarkdown(String title, String beats) {
        log.info("[Scaffold Tool] scaffold_markdown: title='{}', beats='{}'", title, beats);
        String safeTitle = (title == null || title.isBlank()) ? "Untitled" : title.strip();
        List<String> sections = splitCsv(beats);
        if (sections.isEmpty()) {
            sections = List.of("Hook", "Development", "Call to Action");
        }
        StringBuilder out = new StringBuilder("# ").append(safeTitle).append("\n");
        for (String section : sections) {
            out.append("\n## ").append(section).append("\n\n_…_\n");
        }
        return out.toString();
    }

    /**
     * Pick the most representative quote for a term from a body of text.
     * Heuristic: the longest sentence (≥ {@code minLength} chars) that contains
     * the term, case-insensitive; falls back to the longest sentence overall.
     *
     * @param text      the source text to extract from
     * @param term      the target term the quote should mention
     * @param minLength minimum quote length in characters ({@code <= 0} → 40)
     */
    @Tool(name = "scaffold_pick_quote", description = ".")
    public String scaffoldPickQuote(String text, String term, int minLength) {
        log.info("[Scaffold Tool] scaffold_pick_quote: term='{}', minLength={}", term, minLength);
        if (text == null || text.isBlank()) {
            return "";
        }
        int min = minLength <= 0 ? DEFAULT_QUOTE_MIN_LENGTH : minLength;
        String needle = term == null ? "" : term.strip().toLowerCase();

        String bestWithTerm = null;
        String longestOverall = null;
        for (String raw : text.split("(?<=[.!?])\\s+")) {
            String sentence = raw.strip();
            if (sentence.isEmpty()) {
                continue;
            }
            if (longestOverall == null || sentence.length() > longestOverall.length()) {
                longestOverall = sentence;
            }
            boolean hasTerm = !needle.isEmpty() && sentence.toLowerCase().contains(needle);
            if (hasTerm && sentence.length() >= min
                    && (bestWithTerm == null || sentence.length() > bestWithTerm.length())) {
                bestWithTerm = sentence;
            }
        }
        return bestWithTerm != null ? bestWithTerm : (longestOverall == null ? "" : longestOverall);
    }

    /**
     * Build Mermaid {@code mindmap} source from a body of text around a central
     * term: the term is the root; branches are the top-N most frequent words
     * (stop-words and the term itself filtered out), title-cased.
     *
     * @param text the source text (e.g. concatenated abstracts)
     * @param term the central term placed at the root
     */
    @Tool(name = "scaffold_build_mind_map", description = ".")
    public String scaffoldBuildMindMap(String text, String term) {
        log.info("[Scaffold Tool] scaffold_build_mind_map: term='{}'", term);
        String root = (term == null || term.isBlank()) ? "Topic" : titleCase(term.strip());
        String termLower = term == null ? "" : term.strip().toLowerCase();

        Map<String, Integer> freq = new LinkedHashMap<>();
        if (text != null && !text.isBlank()) {
            for (String token : text.toLowerCase().split("[^\\p{L}]+")) {
                if (token.length() < MIN_TOKEN_LENGTH || STOP_WORDS.contains(token)
                        || token.equals(termLower)) {
                    continue;
                }
                freq.merge(token, 1, Integer::sum);
            }
        }
        List<String> branches = freq.entrySet().stream()
                .sorted((a, b) -> b.getValue() - a.getValue())
                .limit(MIND_MAP_TOP_N)
                .map(e -> titleCase(e.getKey()))
                .toList();

        StringBuilder out = new StringBuilder("mindmap\n  root((").append(root).append("))\n");
        for (String branch : branches) {
            out.append("    ").append(branch).append("\n");
        }
        return out.toString();
    }

    private static List<String> splitCsv(String csv) {
        List<String> out = new ArrayList<>();
        if (csv == null) {
            return out;
        }
        for (String part : csv.split(",")) {
            String trimmed = part.strip();
            if (!trimmed.isEmpty()) {
                out.add(trimmed);
            }
        }
        return out;
    }

    private static String titleCase(String word) {
        if (word.isEmpty()) {
            return word;
        }
        return Character.toUpperCase(word.charAt(0)) + word.substring(1);
    }
}
