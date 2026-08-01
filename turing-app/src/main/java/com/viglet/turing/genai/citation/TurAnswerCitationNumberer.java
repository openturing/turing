/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.citation;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.ToIntFunction;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * T832 / §LXII.1 — the shared footnote numbering that makes an answer's
 * {@code [n]} citation markers agree with the citation chips the client renders,
 * regardless of which answer path produced them (catalog copilot, SN/RAG chat, or
 * a native-provider citation stream).
 *
 * <p>The logic is the T800 renumbering originally embedded in
 * {@code TurCatalogCopilotService.groundCitations}, lifted here so the SN/RAG path
 * (and, later, the streaming {@code /answer} endpoint) can reuse it verbatim
 * instead of re-implementing it. It is stateless and generic over the citation
 * type: a caller supplies how to read an item's raw rank and how to produce a copy
 * of the item with a new footnote, and the numberer owns the marker parsing,
 * remapping and prose rewriting.
 *
 * <p>Two modes:
 * <ul>
 *   <li>{@link #renumberByMarkers} — the marker-driven path. Parses the distinct
 *       {@code [n]} markers the answer actually emits (in order of first
 *       appearance), keeps only those that back a citation, remaps them to compact
 *       answer-order footnotes {@code 1..k}, rewrites the prose to those footnotes,
 *       and returns the citations in matching footnote order. <b>Fail-open:</b>
 *       prose with no usable markers degrades to a bounded top-k slice renumbered
 *       {@code 1..k} with the text left untouched.</li>
 *   <li>{@link #numberInOrder} — the positional path for surfaces whose answer
 *       text carries no {@code [n]} markers (today's SN/RAG {@code sources[]} SSE):
 *       assign footnotes {@code 1..k} by the incoming (relevance/dedupe) order,
 *       touching nothing else.</li>
 * </ul>
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
public final class TurAnswerCitationNumberer {

    /** Matches a {@code [n]} citation marker in an answer (1–4 digit rank). */
    private static final Pattern CITATION_MARKER = Pattern.compile("\\[(\\d{1,4})\\]");

    private TurAnswerCitationNumberer() {
        // utility class — the numbering is stateless
    }

    /**
     * An answer text with its {@code [n]} markers renumbered to compact footnotes,
     * paired with the cited citations in matching footnote order.
     *
     * @param <T> the caller's citation type
     */
    public record Numbered<T>(String answer, List<T> citations) {
    }

    /**
     * Produces a copy of a citation with a new footnote rank. Supplied by the
     * caller so the numberer stays generic over the concrete citation type.
     *
     * @param <T> the caller's citation type
     */
    @FunctionalInterface
    public interface Renumber<T> {
        /** @return a copy of {@code item} carrying {@code footnote} as its rank. */
        T apply(T item, int footnote);
    }

    /**
     * Marker-driven renumbering (the T800 contract). Selects the citations the
     * answer actually names via its {@code [n]} markers, remaps them to compact
     * answer-order footnotes {@code 1..k}, rewrites the prose, and returns the
     * citations in footnote order. Fails open to a bounded top-k slice (prose
     * untouched) when no parsed marker backs a citation.
     *
     * @param answer      the LLM answer text (may be {@code null})
     * @param all         the full grounding citation set (may be {@code null}/empty)
     * @param rankOf      reads an item's raw rank (the marker it was grounded under)
     * @param renumber    produces a copy of an item with a new footnote rank
     * @param maxReturned hard backstop on how many citations are returned
     * @param failOpenTopK top-k cap used only on the fail-open (marker-less) path
     * @param <T>         the caller's citation type
     * @return the renumbered answer + citations, never {@code null}
     */
    public static <T> Numbered<T> renumberByMarkers(String answer, List<T> all,
            ToIntFunction<T> rankOf, Renumber<T> renumber, int maxReturned, int failOpenTopK) {
        if (all == null || all.isEmpty()) {
            return new Numbered<>(answer, List.of());
        }
        Map<Integer, T> byRank = new LinkedHashMap<>();
        for (T item : all) {
            byRank.putIfAbsent(rankOf.applyAsInt(item), item);
        }
        // Cited ranks in order of first appearance (parseCitedRanks preserves it),
        // kept only when they actually back a citation, capped by the backstop.
        Map<Integer, Integer> remap = new LinkedHashMap<>(); // raw rank -> compact footnote
        List<T> grounded = new ArrayList<>();
        for (int rawRank : parseCitedRanks(answer)) {
            if (!byRank.containsKey(rawRank) || grounded.size() >= maxReturned) {
                continue;
            }
            int footnote = grounded.size() + 1;
            remap.put(rawRank, footnote);
            grounded.add(renumber.apply(byRank.get(rawRank), footnote));
        }
        if (!grounded.isEmpty()) {
            return new Numbered<>(rewriteMarkers(answer, remap), List.copyOf(grounded));
        }
        // Fail-open: no usable markers — bounded top-k, renumbered 1..k, prose as-is.
        int cap = Math.min(failOpenTopK, all.size());
        List<T> slice = new ArrayList<>(cap);
        for (int i = 0; i < cap; i++) {
            slice.add(renumber.apply(all.get(i), i + 1));
        }
        return new Numbered<>(answer, List.copyOf(slice));
    }

    /**
     * Positional numbering for marker-less surfaces: assigns footnotes
     * {@code 1..k} by the incoming order (already relevance/dedupe ordered),
     * leaving every other field untouched. Used by the SN/RAG {@code sources[]}
     * path, whose streamed answer carries no {@code [n]} markers to parse.
     *
     * @param items    the ordered citation list (may be {@code null}/empty)
     * @param renumber produces a copy of an item with its footnote rank set
     * @param <T>      the caller's citation type
     * @return the same items in order, each renumbered {@code 1..k}; never {@code null}
     */
    public static <T> List<T> numberInOrder(List<T> items, Renumber<T> renumber) {
        if (items == null || items.isEmpty()) {
            return List.of();
        }
        List<T> out = new ArrayList<>(items.size());
        for (int i = 0; i < items.size(); i++) {
            out.add(renumber.apply(items.get(i), i + 1));
        }
        return List.copyOf(out);
    }

    /**
     * Rewrites every {@code [n]} marker in the answer to its compact footnote from
     * {@code remap}; a marker with no mapping (an out-of-range or dangling rank the
     * LLM emitted for a row it wasn't given) is dropped so no orphan number reaches
     * the reader. Returns the text unchanged when there is nothing to rewrite.
     */
    private static String rewriteMarkers(String answer, Map<Integer, Integer> remap) {
        if (answer == null || answer.isBlank()) {
            return answer;
        }
        Matcher matcher = CITATION_MARKER.matcher(answer);
        StringBuilder sb = new StringBuilder(answer.length());
        while (matcher.find()) {
            Integer footnote = null;
            try {
                footnote = remap.get(Integer.parseInt(matcher.group(1)));
            } catch (NumberFormatException ignored) {
                // marker out of int range — treat as unmapped (dropped)
            }
            matcher.appendReplacement(sb, footnote != null ? "[" + footnote + "]" : "");
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    /** Extracts the distinct 1-based ranks referenced as {@code [n]} in the answer. */
    private static Set<Integer> parseCitedRanks(String answer) {
        if (answer == null || answer.isBlank()) {
            return Set.of();
        }
        Set<Integer> ranks = new LinkedHashSet<>();
        Matcher matcher = CITATION_MARKER.matcher(answer);
        while (matcher.find()) {
            try {
                int rank = Integer.parseInt(matcher.group(1));
                if (rank >= 1) {
                    ranks.add(rank);
                }
            } catch (NumberFormatException ignored) {
                // marker out of int range — skip it
            }
        }
        return ranks;
    }
}
