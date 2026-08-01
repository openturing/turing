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

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.viglet.turing.genai.catalog.TurCatalogCitation;
import com.viglet.turing.genai.rag.TurRagSource;

/**
 * T832 / §LXII.1 — direct unit coverage for the shared footnote numbering lifted
 * out of {@code TurCatalogCopilotService.groundCitations} (the T800 renumbering).
 * The marker-driven cases mirror the assertions previously exercised only through
 * the catalog service, proving the extraction is behaviour-preserving; the
 * positional cases cover the new SN/RAG {@code numberInOrder} path.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
class TurAnswerCitationNumbererTest {

    /** Matches the catalog service's backstop / top-k so the assertions carry over. */
    private static final int MAX_RETURNED = 24;
    private static final int TOP_K = 8;

    private static TurCatalogCitation cat(int rank) {
        return new TurCatalogCitation(rank, "id-" + rank, "Model " + rank,
                "https://ex/" + rank, (double) rank, null);
    }

    private static List<TurCatalogCitation> catalog(int size) {
        List<TurCatalogCitation> all = new ArrayList<>(size);
        for (int i = 1; i <= size; i++) {
            all.add(cat(i));
        }
        return all;
    }

    private static TurAnswerCitationNumberer.Numbered<TurCatalogCitation> ground(
            String answer, List<TurCatalogCitation> all) {
        return TurAnswerCitationNumberer.renumberByMarkers(answer, all,
                TurCatalogCitation::rank,
                (c, footnote) -> c.rank() == footnote ? c
                        : new TurCatalogCitation(footnote, c.id(), c.title(), c.url(),
                                c.score(), c.rankingExplanation()),
                MAX_RETURNED, TOP_K);
    }

    // ─────────────────────────── marker-driven (T800) ───────────────────────────

    @Test
    void keepsOnlyCitedRanksRenumberedToCompactFootnotes() {
        // The canonical T800 assertion: grounds on 12, answer cites [3]/[7].
        var out = ground("Consider the third [3] or the seventh [7] option.", catalog(12));

        assertThat(out.citations()).extracting(TurCatalogCitation::rank).containsExactly(1, 2);
        assertThat(out.citations()).extracting(TurCatalogCitation::title)
                .containsExactly("Model 3", "Model 7");
        assertThat(out.answer()).isEqualTo("Consider the third [1] or the seventh [2] option.");
    }

    @Test
    void footnoteOrderFollowsFirstAppearanceNotNumericOrder() {
        var out = ground("First [5], then [2].", catalog(5));

        assertThat(out.citations()).extracting(TurCatalogCitation::title)
                .containsExactly("Model 5", "Model 2");
        assertThat(out.answer()).isEqualTo("First [1], then [2].");
    }

    @Test
    void dropsDanglingMarkersWithNoBackingCitation() {
        var out = ground("Valid [2] but dangling [99].", catalog(3));

        assertThat(out.citations()).extracting(TurCatalogCitation::rank).containsExactly(1);
        // The unmapped [99] is removed entirely so no orphan number reaches the reader.
        assertThat(out.answer()).isEqualTo("Valid [1] but dangling .");
    }

    @Test
    void backstopCapsRunawayCitations() {
        List<TurCatalogCitation> all = catalog(30);
        StringBuilder answer = new StringBuilder();
        for (int i = 1; i <= 30; i++) {
            answer.append("claim [").append(i).append("] ");
        }

        var out = ground(answer.toString(), all);

        assertThat(out.citations()).hasSize(MAX_RETURNED);
        assertThat(out.citations().get(0).rank()).isEqualTo(1);
        assertThat(out.citations().get(MAX_RETURNED - 1).rank()).isEqualTo(24);
    }

    // ─────────────────────────── fail-open (marker-less) ───────────────────────────

    @Test
    void failsOpenToBoundedTopKWhenAnswerHasNoMarkers() {
        var out = ground("A plain answer with no citations at all.", catalog(12));

        assertThat(out.citations()).hasSize(TOP_K);
        assertThat(out.citations()).extracting(TurCatalogCitation::rank)
                .containsExactly(1, 2, 3, 4, 5, 6, 7, 8);
        assertThat(out.answer()).isEqualTo("A plain answer with no citations at all.");
    }

    @Test
    void failOpenNeverExceedsAvailableCitations() {
        var out = ground("no markers here", catalog(3));

        assertThat(out.citations()).extracting(TurCatalogCitation::rank).containsExactly(1, 2, 3);
    }

    @Test
    void emptyOrNullGroundingSetReturnsNoCitations() {
        assertThat(ground("answer [1]", List.of()).citations()).isEmpty();
        assertThat(ground("answer [1]", null).citations()).isEmpty();
        assertThat(ground(null, null).answer()).isNull();
    }

    // ─────────────────────────── positional (SN/RAG) ───────────────────────────

    @Test
    void numberInOrderAssignsSequentialFootnotesPreservingOrder() {
        List<TurRagSource> sources = List.of(
                new TurRagSource("a", "A", "ua", null, 0.9, false),
                new TurRagSource("b", "B", "ub", null, 0.7, false),
                new TurRagSource("c", "C", "uc", null, 0.5, true));

        List<TurRagSource> numbered =
                TurAnswerCitationNumberer.numberInOrder(sources, TurRagSource::withRank);

        assertThat(numbered).extracting(TurRagSource::sourceId).containsExactly("a", "b", "c");
        assertThat(numbered).extracting(TurRagSource::rank).containsExactly(1, 2, 3);
        // Every other field is left untouched.
        assertThat(numbered.get(2).keywordOnly()).isTrue();
        assertThat(numbered.get(0).score()).isEqualTo(0.9);
    }

    @Test
    void numberInOrderHandlesNullAndEmpty() {
        assertThat(TurAnswerCitationNumberer.numberInOrder(null, TurRagSource::withRank)).isEmpty();
        assertThat(TurAnswerCitationNumberer.numberInOrder(List.<TurRagSource>of(),
                TurRagSource::withRank)).isEmpty();
    }
}
