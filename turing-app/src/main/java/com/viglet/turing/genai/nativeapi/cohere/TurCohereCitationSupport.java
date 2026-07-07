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

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

import com.viglet.turing.genai.citation.TurChatCitation;
import com.viglet.turing.genai.citation.TurCitationDocument;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * T519 / §XXVIII.15 — decodes Cohere {@code Command} citation-mode responses onto
 * the existing T152–T155 {@link TurChatCitation} contract, so a Cohere-grounded
 * answer renders through the same {@code citations} SSE event + chat UI as the
 * Anthropic / Gemini native citations already do.
 *
 * <p>Two pure halves, both unit-testable without a live API:
 * <ul>
 *   <li>{@link #documentsArray} — encode the retrieved {@link TurCitationDocument}s
 *       as the Cohere v2 {@code documents} array (stable {@code doc-N} ids that
 *       index back into the ordered list);</li>
 *   <li>{@link #decode} — map the response {@code message.citations[]} (each
 *       {@code start}/{@code end}/{@code text} + {@code sources[].id}) back onto
 *       {@link TurChatCitation}, resolving {@code doc-N} to the document's
 *       sourceId / title / url. {@code start}/{@code end} are answer-text offsets
 *       (T154 {@code answerStart}/{@code answerEnd}); {@code locationType} is
 *       {@code "search_result"} (T153).</li>
 * </ul>
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Component
public class TurCohereCitationSupport {

    private static final String DOC_ID_PREFIX = "doc-";
    private static final String SEARCH_RESULT = "search_result";

    /**
     * Builds the Cohere v2 {@code documents} request array from the ordered
     * citation documents — each gets a stable {@code doc-N} id (N = list index)
     * carrying {@code data.text} (+ {@code data.title} when present).
     */
    public ArrayNode documentsArray(ObjectNode root, List<TurCitationDocument> documents) {
        ArrayNode array = root.arrayNode();
        for (int i = 0; i < documents.size(); i++) {
            TurCitationDocument doc = documents.get(i);
            ObjectNode entry = array.objectNode();
            entry.put("id", DOC_ID_PREFIX + i);
            ObjectNode data = entry.putObject("data");
            data.put("text", doc.text() == null ? "" : doc.text());
            if (doc.title() != null && !doc.title().isBlank()) {
                data.put("title", doc.title());
            }
            array.add(entry);
        }
        return array;
    }

    /**
     * Decodes {@code message.citations[]} into {@link TurChatCitation}s resolved
     * against {@code documents}. One {@link TurChatCitation} is emitted per
     * (citation × cited source); the cited source's passage text becomes
     * {@code citedText}. Tolerates a missing/empty citations array (returns an
     * empty list) so a benign response never breaks the turn.
     *
     * @param messageNode the response {@code message} object node
     * @param documents   the ordered documents sent in the request
     */
    public List<TurChatCitation> decode(JsonNode messageNode, List<TurCitationDocument> documents) {
        List<TurChatCitation> citations = new ArrayList<>();
        if (messageNode == null) {
            return citations;
        }
        JsonNode citationArray = messageNode.path("citations");
        if (!citationArray.isArray()) {
            return citations;
        }
        for (JsonNode citation : citationArray) {
            Integer answerStart = intOrNull(citation.get("start"));
            Integer answerEnd = intOrNull(citation.get("end"));
            JsonNode sources = citation.path("sources");
            if (!sources.isArray() || sources.isEmpty()) {
                continue;
            }
            for (JsonNode source : sources) {
                int index = parseDocIndex(source.path("id").asString(""));
                if (index < 0 || index >= documents.size()) {
                    continue;
                }
                TurCitationDocument doc = documents.get(index);
                citations.add(new TurChatCitation(index, doc.sourceId(), doc.title(), doc.url(),
                        doc.text(), null, null, SEARCH_RESULT, answerStart, answerEnd));
            }
        }
        return citations;
    }

    /**
     * Parses the document index out of a Cohere source id. Cohere echoes the
     * request {@code doc-N} id (sometimes suffixed, e.g. {@code doc-0:1}); we read
     * the leading integer after the prefix. Returns {@code -1} when it can't.
     */
    private static int parseDocIndex(String id) {
        if (id == null || !id.startsWith(DOC_ID_PREFIX)) {
            return -1;
        }
        StringBuilder digits = new StringBuilder();
        for (int i = DOC_ID_PREFIX.length(); i < id.length(); i++) {
            char c = id.charAt(i);
            if (Character.isDigit(c)) {
                digits.append(c);
            } else {
                break;
            }
        }
        if (digits.isEmpty()) {
            return -1;
        }
        try {
            return Integer.parseInt(digits.toString());
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private static Integer intOrNull(JsonNode node) {
        return node != null && node.isInt() ? node.asInt() : (node != null && node.canConvertToInt()
                ? node.asInt() : null);
    }
}
