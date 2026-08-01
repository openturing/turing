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

import java.util.Map;

import com.viglet.turing.genai.catalog.TurCatalogCitation;
import com.viglet.turing.genai.rag.TurRagSource;

/**
 * T832 / §LXII.1 — the single answer-citation projection every answer surface
 * normalizes onto. Before this, the three answer paths each shipped their own
 * shape — {@link TurRagSource} (no footnote rank), {@link TurCatalogCitation}
 * (footnote rank) and {@link TurChatCitation} (per-sentence answer-span offsets) —
 * so a client had to know which path produced a turn to render its chips. This
 * record is the union the streaming {@code /answer} endpoint (T833) and the
 * {@code useTuringAnswer} hook (T837) emit, with the {@link TurChatCitation}
 * answer-span data folded in as an <em>enrichment</em> rather than a fourth shape.
 *
 * <p>The footnote {@code rank} is 1-based and, on the marker-driven path, agrees
 * with the {@code [n]} markers in the answer prose (see
 * {@link TurAnswerCitationNumberer}); on the positional SN/RAG path it is the
 * relevance-order position.
 *
 * @param rank               1-based footnote / relevance position
 * @param sourceId           stable identifier of the cited source document
 * @param title              human-friendly label for the chip
 * @param url                deep link to the source, when available
 * @param score              retrieval score (cosine / BM25 / RRF), when known
 * @param chunkIndex         zero-based chunk index within the source ({@code null}
 *                           on the catalog path, which cites whole rows)
 * @param keywordOnly        {@code true} when the chunk reached the answer through
 *                           the keyword (BM25) fallback only
 * @param rankingExplanation the T389 lexical/semantic/fused rank breakdown when the
 *                           hybrid pipeline attached one ({@code null} otherwise) —
 *                           the raw material for the T839 "why this answer" panel
 * @param answerStart        T154 enrichment: char offset where the cited claim
 *                           starts in the answer text ({@code null} unless a native
 *                           per-sentence span was attached)
 * @param answerEnd          T154 enrichment: char offset where the cited claim ends
 * @param citedText          T154 enrichment: the exact grounded passage, when known
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
public record TurAnswerCitation(
        int rank,
        String sourceId,
        String title,
        String url,
        Double score,
        Integer chunkIndex,
        boolean keywordOnly,
        Map<String, Object> rankingExplanation,
        Integer answerStart,
        Integer answerEnd,
        String citedText) {

    /**
     * Normalizes an SN/RAG {@link TurRagSource} onto the projection. A source
     * numbered by {@link TurAnswerCitationNumberer#numberInOrder} carries its
     * footnote in {@link TurRagSource#rank()}; an un-numbered source (rank
     * {@code null}) projects to rank {@code 0}.
     */
    public static TurAnswerCitation fromRagSource(TurRagSource source) {
        return new TurAnswerCitation(
                source.rank() == null ? 0 : source.rank(),
                source.sourceId(), source.title(), source.url(), source.score(),
                source.chunkIndex(), source.keywordOnly(),
                null, null, null, null);
    }

    /** Normalizes a catalog {@link TurCatalogCitation} onto the projection. */
    public static TurAnswerCitation fromCatalogCitation(TurCatalogCitation citation) {
        return new TurAnswerCitation(
                citation.rank(), citation.id(), citation.title(), citation.url(),
                citation.score(), null, false, citation.rankingExplanation(),
                null, null, null);
    }

    /**
     * Returns a copy enriched with a native per-sentence answer span
     * ({@link TurChatCitation}, T154). The span data augments an existing footnote
     * citation — it never introduces a competing shape.
     */
    public TurAnswerCitation withAnswerSpan(TurChatCitation span) {
        if (span == null) {
            return this;
        }
        return new TurAnswerCitation(rank, sourceId, title, url, score, chunkIndex,
                keywordOnly, rankingExplanation,
                span.answerStart(), span.answerEnd(), span.citedText());
    }
}
