/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.nativeapi.gemini;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.springframework.util.StringUtils;

import com.google.genai.types.GroundingChunk;
import com.google.genai.types.GroundingChunkWeb;
import com.google.genai.types.GroundingMetadata;
import com.google.genai.types.GroundingSupport;
import com.google.genai.types.Segment;
import com.viglet.turing.genai.citation.TurChatCitation;

/**
 * T490 / §X.19 — decodes Gemini {@code google_search} grounding metadata onto
 * the shipped citation contract, the Gemini analog of {@code TurAnthropicCitationSupport}.
 *
 * <p>Gemini returns {@link GroundingMetadata} on the response candidate: a list
 * of {@code groundingChunks} (the web sources) and a list of
 * {@code groundingSupports} (each a text {@link Segment} of the answer plus the
 * indices of the chunks that support it). This maps each
 * (support → supporting chunk) pair to one {@link TurChatCitation} carrying the
 * cited answer span ({@code answerStart}/{@code answerEnd}) and the source link,
 * so the citation-aware chat UI (T154) underlines the grounded claim and
 * deep-links to the web source — identical to the Anthropic citation path.
 *
 * <p>Gemini's segment offsets are <b>UTF-8 byte</b> offsets into the answer
 * text; {@link #byteOffsetToCharIndex} converts them to char indices so they
 * line up with the JavaScript string offsets the UI uses.
 *
 * <p>{@code locationType} is {@code "web"} (a free string in the contract); the
 * source-level {@code startIndex}/{@code endIndex} are {@code null} (web
 * grounding has no source char offsets, only the answer span).
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
public final class TurGeminiGroundingDecoder {

    /** Gemini web-grounding citation location type. */
    static final String LOCATION_TYPE_WEB = "web";

    private TurGeminiGroundingDecoder() {
    }

    /**
     * Google's mandatory Search Suggestion chips ({@code searchEntryPoint
     * .renderedContent}, an HTML/CSS fragment) plus the queries the model ran.
     * Streamed to the client as the {@code type:"searchSuggestions"} SSE event.
     */
    public record SearchSuggestions(String renderedContent, List<String> queries) {
        public boolean isEmpty() {
            return !StringUtils.hasText(renderedContent) && (queries == null || queries.isEmpty());
        }
    }

    /** Decoded grounding: the per-claim citations + the Search Suggestion chips. */
    public record GroundingResult(List<TurChatCitation> citations, SearchSuggestions suggestions) {
        public static final GroundingResult EMPTY =
                new GroundingResult(List.of(), new SearchSuggestions(null, List.of()));
    }

    /**
     * Decode the grounding metadata against the full assistant answer text.
     * Returns {@link GroundingResult#EMPTY} for {@code null}/empty metadata.
     */
    public static GroundingResult decode(GroundingMetadata metadata, String answerText) {
        if (metadata == null) {
            return GroundingResult.EMPTY;
        }
        List<GroundingChunk> chunks = metadata.groundingChunks().orElse(List.of());
        List<GroundingSupport> supports = metadata.groundingSupports().orElse(List.of());
        List<TurChatCitation> citations = new ArrayList<>();
        for (GroundingSupport support : supports) {
            Segment segment = support.segment().orElse(null);
            if (segment == null) {
                continue;
            }
            String citedText = segment.text().orElse("");
            int answerStart = byteOffsetToCharIndex(answerText, segment.startIndex().orElse(0));
            int answerEnd = byteOffsetToCharIndex(answerText, segment.endIndex().orElse(0));
            for (Integer chunkIndex : support.groundingChunkIndices().orElse(List.of())) {
                if (chunkIndex == null || chunkIndex < 0 || chunkIndex >= chunks.size()) {
                    continue;
                }
                GroundingChunkWeb web = chunks.get(chunkIndex).web().orElse(null);
                if (web == null) {
                    continue;
                }
                String uri = web.uri().orElse(null);
                String title = web.title().orElse(web.domain().orElse(uri));
                citations.add(new TurChatCitation(chunkIndex, uri, title, uri, citedText,
                        null, null, LOCATION_TYPE_WEB, answerStart, answerEnd));
            }
        }
        SearchSuggestions suggestions = new SearchSuggestions(
                metadata.searchEntryPoint().flatMap(e -> e.renderedContent()).orElse(null),
                metadata.webSearchQueries().orElse(List.of()));
        return new GroundingResult(citations, suggestions);
    }

    /**
     * Convert a UTF-8 byte offset (as Gemini reports segment indices) to a Java
     * {@code String} char index, clamped to the text bounds. ASCII text is a
     * 1:1 mapping; multi-byte runs shift the char index left of the byte index.
     */
    static int byteOffsetToCharIndex(String text, int byteOffset) {
        if (!StringUtils.hasText(text) || byteOffset <= 0) {
            return 0;
        }
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        int clamped = Math.min(byteOffset, bytes.length);
        return new String(bytes, 0, clamped, StandardCharsets.UTF_8).length();
    }
}
