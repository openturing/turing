/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.nativeapi.cohere;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.viglet.turing.genai.citation.TurChatCitation;
import com.viglet.turing.genai.citation.TurCitationDocument;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * T519 — the pure Cohere citation decoder: builds the v2 {@code documents} array
 * with stable {@code doc-N} ids and maps {@code message.citations[]} back onto
 * the {@link TurChatCitation} contract.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
class TurCohereCitationSupportTest {

    private static final ObjectMapper MAPPER = JsonMapper.builder().build();
    private final TurCohereCitationSupport support = new TurCohereCitationSupport();

    private static List<TurCitationDocument> docs() {
        return List.of(
                new TurCitationDocument("src-0", "Solar Policy", "https://x/0", "The sun rises in the east."),
                new TurCitationDocument("src-1", "Lunar Policy", "https://x/1", "The moon orbits the earth."));
    }

    @Test
    void documentsArrayUsesStableDocIdsAndCarriesTextAndTitle() {
        ObjectNode root = MAPPER.createObjectNode();
        ArrayNode array = support.documentsArray(root, docs());

        assertThat(array).hasSize(2);
        assertThat(array.get(0).path("id").asString()).isEqualTo("doc-0");
        assertThat(array.get(0).path("data").path("text").asString()).isEqualTo("The sun rises in the east.");
        assertThat(array.get(0).path("data").path("title").asString()).isEqualTo("Solar Policy");
        assertThat(array.get(1).path("id").asString()).isEqualTo("doc-1");
    }

    @Test
    void decodeMapsCitationsOntoContractResolvingDocIds() {
        // A Cohere v2/chat message with one citation grounding answer chars 0..20
        // on document doc-1.
        String json = """
                {
                  "role": "assistant",
                  "content": [{"type": "text", "text": "The moon orbits the earth, per the policy."}],
                  "citations": [
                    {"start": 0, "end": 25, "text": "The moon orbits the earth",
                     "sources": [{"type": "document", "id": "doc-1"}]}
                  ]
                }
                """;
        JsonNode message = MAPPER.readTree(json);

        List<TurChatCitation> citations = support.decode(message, docs());

        assertThat(citations).hasSize(1);
        TurChatCitation c = citations.get(0);
        assertThat(c.documentIndex()).isEqualTo(1);
        assertThat(c.sourceId()).isEqualTo("src-1");
        assertThat(c.documentTitle()).isEqualTo("Lunar Policy");
        assertThat(c.url()).isEqualTo("https://x/1");
        assertThat(c.citedText()).isEqualTo("The moon orbits the earth.");
        assertThat(c.answerStart()).isZero();
        assertThat(c.answerEnd()).isEqualTo(25);
        assertThat(c.locationType()).isEqualTo("search_result");
    }

    @Test
    void decodeEmitsOneCitationPerCitedSource() {
        String json = """
                {
                  "citations": [
                    {"start": 5, "end": 9, "text": "both",
                     "sources": [{"id": "doc-0"}, {"id": "doc-1"}]}
                  ]
                }
                """;
        JsonNode message = MAPPER.readTree(json);

        List<TurChatCitation> citations = support.decode(message, docs());

        assertThat(citations).extracting(TurChatCitation::documentIndex).containsExactly(0, 1);
    }

    @Test
    void decodeToleratesMissingCitationsAndOutOfRangeIds() {
        assertThat(support.decode(MAPPER.readTree("{}"), docs())).isEmpty();
        assertThat(support.decode(null, docs())).isEmpty();

        String outOfRange = """
                {"citations": [{"start": 0, "end": 1, "sources": [{"id": "doc-9"}]}]}
                """;
        assertThat(support.decode(MAPPER.readTree(outOfRange), docs())).isEmpty();
    }
}
