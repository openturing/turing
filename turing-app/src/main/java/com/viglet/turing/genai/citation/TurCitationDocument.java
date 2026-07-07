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
package com.viglet.turing.genai.citation;

import java.util.List;

import org.springframework.ai.document.Document;

import com.viglet.turing.genai.rag.TurRagSource;

/**
 * T152 / §X.7.a — one retrieved passage prepared as an Anthropic
 * {@code document} content block for verifiable RAG.
 *
 * <p>When an agent enables the {@code citations} request option, each
 * {@link TurCitationDocument} in the turn becomes a {@code document} block (with
 * {@code citations: {enabled: true}}) attached to the user message. Claude then
 * answers grounding every sentence in these blocks and returns per-sentence
 * citation arrays whose {@code documentIndex} points back into this ordered
 * list — see {@link TurChatCitation}. The {@code sourceId} / {@code title} /
 * {@code url} are carried verbatim so the streamed citation can deep-link to the
 * originating {@code TurSNDocument} without a second lookup.
 *
 * <p>T176 / §X.12.b — when the agent enables <em>Native PDF Grounding</em> and
 * the passage comes from an indexed PDF, the optional {@link #nativePdf} carries
 * the base64 of the original binary (and the cached vendor {@code file_id}); the
 * citation support then sends a native PDF {@code document} block instead of the
 * Tika-extracted text, so Claude reads the real layout/tables/figures and cites
 * by page. {@code null} (the default) is the unchanged text-only path.
 *
 * @param sourceId stable identifier of the source document (objectName / SN doc id)
 * @param title    human-friendly label rendered on the citation chip / popover
 * @param url      deep link to the source (may be {@code null})
 * @param text     the passage text sent verbatim as the document block payload
 * @param nativePdf optional native-PDF source for this passage ({@code null} = text only)
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
public record TurCitationDocument(String sourceId, String title, String url, String text,
        NativePdf nativePdf) {

    /**
     * T176 / §X.12.b — the native-document representation of a retrieved PDF:
     * the base64-encoded original binary (sent as a citations-enabled
     * {@code document} block) plus the {@code file_id} the {@code TurFilesApiBridge}
     * (T175) cached for it, kept for cross-agent dedup (T177) and the future
     * file-source migration.
     */
    public record NativePdf(String base64, String mediaType, String vendorFileId) {
    }

    /** Text-only passage — the pre-T176 shape; no native PDF attached. */
    public TurCitationDocument(String sourceId, String title, String url, String text) {
        this(sourceId, title, url, text, null);
    }

    /** A copy of this passage carrying the given native-PDF source. */
    public TurCitationDocument withNativePdf(NativePdf pdf) {
        return new TurCitationDocument(sourceId, title, url, text, pdf);
    }

    /** True when a non-blank base64 PDF is attached for native grounding. */
    public boolean hasNativePdf() {
        return nativePdf != null && nativePdf.base64() != null && !nativePdf.base64().isBlank();
    }

    /**
     * Adapt a Spring AI {@link Document} (a RAG retrieval hit) into a citation
     * document, reusing {@link TurRagSource#fromDocument(Document)} for the
     * sourceId / title / url so provenance is resolved identically to the
     * {@code sources[]} SSE event. Returns {@code null} when the hit carries no
     * text (nothing citable).
     */
    public static TurCitationDocument fromDocument(Document doc) {
        if (doc == null) {
            return null;
        }
        String text = doc.getText();
        if (text == null || text.isBlank()) {
            return null;
        }
        TurRagSource source = TurRagSource.fromDocument(doc);
        return new TurCitationDocument(source.sourceId(), source.title(), source.url(), text);
    }

    /** Convenience: map a list of RAG hits, dropping any that carry no text. */
    public static List<TurCitationDocument> fromDocuments(List<Document> docs) {
        if (docs == null || docs.isEmpty()) {
            return List.of();
        }
        return docs.stream()
                .map(TurCitationDocument::fromDocument)
                .filter(d -> d != null)
                .toList();
    }
}
