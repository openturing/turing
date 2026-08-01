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

import java.util.Map;

import org.junit.jupiter.api.Test;

import com.viglet.turing.genai.catalog.TurCatalogCitation;
import com.viglet.turing.genai.rag.TurRagSource;

/**
 * T832 / §LXII.1 — coverage for the unified {@link TurAnswerCitation} projection
 * the catalog, SN/RAG and native-citation paths normalize onto.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
class TurAnswerCitationTest {

    @Test
    void fromRagSourceCarriesTheAssignedFootnote() {
        TurRagSource source =
                new TurRagSource("doc-9", "Guide", "https://ex/guide", 2, 0.88, false).withRank(3);

        TurAnswerCitation citation = TurAnswerCitation.fromRagSource(source);

        assertThat(citation.rank()).isEqualTo(3);
        assertThat(citation.sourceId()).isEqualTo("doc-9");
        assertThat(citation.title()).isEqualTo("Guide");
        assertThat(citation.url()).isEqualTo("https://ex/guide");
        assertThat(citation.chunkIndex()).isEqualTo(2);
        assertThat(citation.score()).isEqualTo(0.88);
        assertThat(citation.keywordOnly()).isFalse();
        assertThat(citation.rankingExplanation()).isNull();
    }

    @Test
    void fromRagSourceDefaultsUnnumberedSourceToRankZero() {
        TurRagSource source = new TurRagSource("doc-1", "A", "u", null, 0.5, true);

        TurAnswerCitation citation = TurAnswerCitation.fromRagSource(source);

        assertThat(citation.rank()).isZero();
        assertThat(citation.keywordOnly()).isTrue();
    }

    @Test
    void fromCatalogCitationMapsRankAndExplanation() {
        Map<String, Object> explanation = Map.of("lexicalRank", 4, "rrfScore", 0.31);
        TurCatalogCitation source =
                new TurCatalogCitation(2, "id-2", "Model 2", "https://ex/2", 9.0, explanation);

        TurAnswerCitation citation = TurAnswerCitation.fromCatalogCitation(source);

        assertThat(citation.rank()).isEqualTo(2);
        assertThat(citation.sourceId()).isEqualTo("id-2");
        assertThat(citation.title()).isEqualTo("Model 2");
        assertThat(citation.rankingExplanation()).isEqualTo(explanation);
        assertThat(citation.chunkIndex()).isNull();
        assertThat(citation.answerStart()).isNull();
    }

    @Test
    void withAnswerSpanFoldsInNativePerSentenceOffsets() {
        TurAnswerCitation base = TurAnswerCitation.fromRagSource(
                new TurRagSource("doc-1", "A", "u", null, 0.5, false).withRank(1));
        TurChatCitation span = new TurChatCitation(0, "doc-1", "A", "u", "the cited passage",
                10, 42, "TEXT", 5, 20);

        TurAnswerCitation enriched = base.withAnswerSpan(span);

        assertThat(enriched.answerStart()).isEqualTo(5);
        assertThat(enriched.answerEnd()).isEqualTo(20);
        assertThat(enriched.citedText()).isEqualTo("the cited passage");
        // The footnote and source identity survive the enrichment.
        assertThat(enriched.rank()).isEqualTo(1);
        assertThat(enriched.sourceId()).isEqualTo("doc-1");
    }

    @Test
    void withAnswerSpanOfNullIsIdentity() {
        TurAnswerCitation base = TurAnswerCitation.fromRagSource(
                new TurRagSource("doc-1", "A", "u", null, 0.5, false).withRank(1));

        assertThat(base.withAnswerSpan(null)).isSameAs(base);
    }
}
