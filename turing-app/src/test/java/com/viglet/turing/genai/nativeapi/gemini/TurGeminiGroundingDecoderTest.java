/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.nativeapi.gemini;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.google.genai.types.GroundingChunk;
import com.google.genai.types.GroundingChunkWeb;
import com.google.genai.types.GroundingMetadata;
import com.google.genai.types.GroundingSupport;
import com.google.genai.types.SearchEntryPoint;
import com.google.genai.types.Segment;
import com.viglet.turing.genai.citation.TurChatCitation;
import com.viglet.turing.genai.nativeapi.gemini.TurGeminiGroundingDecoder.GroundingResult;

/**
 * T490 / §X.19 — unit coverage for decoding Gemini google_search grounding
 * metadata onto the {@link TurChatCitation} + Search Suggestion contracts.
 */
class TurGeminiGroundingDecoderTest {

    private static GroundingChunk webChunk(String uri, String title) {
        return GroundingChunk.builder()
                .web(GroundingChunkWeb.builder().uri(uri).title(title).build())
                .build();
    }

    private static GroundingSupport support(String text, int start, int end, Integer... chunkIndices) {
        return GroundingSupport.builder()
                .segment(Segment.builder().text(text).startIndex(start).endIndex(end).build())
                .groundingChunkIndices(List.of(chunkIndices))
                .build();
    }

    @Test
    void decodesCitationsFromGroundingSupports() {
        String answer = "The capital is Paris.";
        GroundingMetadata metadata = GroundingMetadata.builder()
                .groundingChunks(List.of(
                        webChunk("https://a.example/x", "Source A"),
                        webChunk("https://b.example/y", "Source B")))
                .groundingSupports(List.of(support("The capital is Paris.", 0, 21, 0, 1)))
                .webSearchQueries(List.of("capital of France"))
                .searchEntryPoint(SearchEntryPoint.builder()
                        .renderedContent("<div>chips</div>").build())
                .build();

        GroundingResult result = TurGeminiGroundingDecoder.decode(metadata, answer);

        assertThat(result.citations()).hasSize(2);
        TurChatCitation first = result.citations().get(0);
        assertThat(first.documentIndex()).isZero();
        assertThat(first.sourceId()).isEqualTo("https://a.example/x");
        assertThat(first.url()).isEqualTo("https://a.example/x");
        assertThat(first.documentTitle()).isEqualTo("Source A");
        assertThat(first.citedText()).isEqualTo("The capital is Paris.");
        assertThat(first.locationType()).isEqualTo("web");
        assertThat(first.answerStart()).isZero();
        assertThat(first.answerEnd()).isEqualTo(21);
        assertThat(result.citations().get(1).documentIndex()).isEqualTo(1);
    }

    @Test
    void surfacesSearchSuggestionChipsAndQueries() {
        GroundingMetadata metadata = GroundingMetadata.builder()
                .webSearchQueries(List.of("q1", "q2"))
                .searchEntryPoint(SearchEntryPoint.builder()
                        .renderedContent("<style>.x{}</style><div>chips</div>").build())
                .build();

        GroundingResult result = TurGeminiGroundingDecoder.decode(metadata, "answer");

        assertThat(result.suggestions().isEmpty()).isFalse();
        assertThat(result.suggestions().renderedContent()).contains("chips");
        assertThat(result.suggestions().queries()).containsExactly("q1", "q2");
        assertThat(result.citations()).isEmpty();
    }

    @Test
    void skipsOutOfRangeChunkIndices() {
        GroundingMetadata metadata = GroundingMetadata.builder()
                .groundingChunks(List.of(webChunk("https://a.example", "A")))
                .groundingSupports(List.of(support("claim", 0, 5, 0, 9)))
                .build();

        GroundingResult result = TurGeminiGroundingDecoder.decode(metadata, "claim text");

        // Only the in-range chunk index (0) yields a citation; 9 is dropped.
        assertThat(result.citations()).hasSize(1);
        assertThat(result.citations().get(0).documentIndex()).isZero();
    }

    @Test
    void nullMetadataYieldsEmpty() {
        GroundingResult result = TurGeminiGroundingDecoder.decode(null, "answer");
        assertThat(result.citations()).isEmpty();
        assertThat(result.suggestions().isEmpty()).isTrue();
    }

    @Test
    void byteOffsetToCharIndexConvertsMultibyteRuns() {
        // "café" is 5 UTF-8 bytes (é is 2 bytes); byte offset 5 = char index 4.
        assertThat(TurGeminiGroundingDecoder.byteOffsetToCharIndex("café", 5)).isEqualTo(4);
        // Clamps past the end.
        assertThat(TurGeminiGroundingDecoder.byteOffsetToCharIndex("hi", 99)).isEqualTo(2);
        assertThat(TurGeminiGroundingDecoder.byteOffsetToCharIndex("hi", 0)).isZero();
    }
}
