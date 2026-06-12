/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.rag;

import java.util.Map;

import org.springframework.ai.document.Document;

import com.viglet.turing.genai.TurSNGenAi;
import com.viglet.turing.genai.provider.store.lucene.TurLuceneVectorStore;

/**
 * T292 / §XVII.1 — provider-agnostic provenance metadata for a single
 * retrieved RAG chunk. Turing already <em>retrieves</em> this data on every
 * answer (T19 / T24); {@code TurRagSource} is the wire shape that carries it
 * out to the chat client as a structured {@code sources[]} SSE event,
 * independent of whether the underlying LLM emits its own citations.
 *
 * <p>The same record powers both the {@code search_knowledge_base} tool path
 * (T292) and the public semantic-navigation chat path (T327), so the chip UI
 * (T293 / T332) consumes one contract regardless of which retrieval surface
 * produced the answer.
 *
 * @param sourceId   stable identifier of the source document (the SN
 *                   {@code source_id}, the asset {@code objectName}, or the
 *                   chunk's own document id as a last resort)
 * @param title      human-friendly label for the chip (document title, file
 *                   name, or the source id when nothing better exists)
 * @param url        deep link to the source when available (the indexed
 *                   {@code url} for SN content, or an asset download URL)
 * @param chunkIndex zero-based index of the retrieved chunk within its source
 *                   document, when the store records it; {@code null} otherwise
 * @param score      retrieval score (cosine similarity for the vector pass,
 *                   BM25/RRF score otherwise); drives the confidence cue
 * @param keywordOnly {@code true} when this chunk reached the answer through
 *                   the keyword (BM25) fallback only — the vector pass did not
 *                   confirm it, so the UI should soften the confidence cue
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
public record TurRagSource(String sourceId, String title, String url,
        Integer chunkIndex, Double score, boolean keywordOnly) {

    private static final String OBJECT_NAME = "objectName";
    private static final String FILE_NAME = "fileName";
    private static final String CONTENT_TYPE = "contentType";
    private static final String CHUNK_INDEX = "chunkIndex";
    private static final String CHUNK_INDEX_SNAKE = "chunk_index";

    /**
     * Extracts provenance metadata from a retrieved Spring AI {@link Document},
     * tolerating the differing metadata schemas of the SN path
     * ({@code source_id} / {@code url} / {@code title}) and the asset RAG path
     * ({@code objectName} / {@code fileName} / {@code contentType}).
     *
     * @param doc a retrieved document (never {@code null})
     * @return the provenance projection, never {@code null}
     */
    public static TurRagSource fromDocument(Document doc) {
        Map<String, Object> meta = doc.getMetadata();

        String objectName = str(meta.get(OBJECT_NAME));
        String sourceId = firstNonBlank(
                str(meta.get(TurSNGenAi.SOURCE_ID)),
                objectName,
                doc.getId());

        String title = firstNonBlank(
                str(meta.get("title")),
                str(meta.get(FILE_NAME)),
                objectName,
                sourceId);

        String url = firstNonBlank(str(meta.get("url")), assetDownloadUrl(objectName));

        Integer chunkIndex = intOrNull(meta.get(CHUNK_INDEX) != null
                ? meta.get(CHUNK_INDEX)
                : meta.get(CHUNK_INDEX_SNAKE));

        boolean keywordOnly = Boolean.TRUE.equals(
                meta.get(TurLuceneVectorStore.METADATA_KEYWORD_ONLY_FALLBACK));

        // contentType is carried in `title` indirectly for assets; the chip UI
        // shows it only when present, so we don't fail when absent.
        String contentType = str(meta.get(CONTENT_TYPE));
        if (title.isBlank() && !contentType.isBlank()) {
            title = contentType;
        }

        return new TurRagSource(sourceId, title, url, chunkIndex, doc.getScore(), keywordOnly);
    }

    private static String assetDownloadUrl(String objectName) {
        if (objectName == null || objectName.isBlank()) {
            return null;
        }
        return "/api/asset/download?objectName="
                + java.net.URLEncoder.encode(objectName, java.nio.charset.StandardCharsets.UTF_8);
    }

    private static String str(Object value) {
        return value == null ? "" : value.toString();
    }

    private static String firstNonBlank(String... candidates) {
        for (String c : candidates) {
            if (c != null && !c.isBlank()) {
                return c;
            }
        }
        return "";
    }

    private static Integer intOrNull(Object value) {
        if (value instanceof Number n) {
            return n.intValue();
        }
        if (value instanceof String s && !s.isBlank()) {
            try {
                return Integer.valueOf(s.trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }
}
