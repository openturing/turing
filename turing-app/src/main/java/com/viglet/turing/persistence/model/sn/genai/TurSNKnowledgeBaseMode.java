/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.persistence.model.sn.genai;

import java.util.Locale;

/**
 * T790 / §LIV.1 (Block BF) — the knowledge-base <strong>mode</strong> of an SN
 * site's GenAI features: the explicit, first-class name for the retrieval
 * strategy that answers questions over the site's content.
 *
 * <p>Turing has always shipped two grounded answering paths; this enum simply
 * <em>names</em> them so an integrator can find and pick the one that fits — the
 * key differentiator being whether an embedding model + vector store are required
 * at all.
 *
 * <ul>
 *   <li>{@link #VECTOR} — the classic embedding RAG chat: the site's content is
 *       chunked, embedded, and stored in a vector store; the chat
 *       ({@code POST /api/sn/{site}/chat/conversation}) retrieves top-K chunks by
 *       cosine similarity and grounds the agent on them. <b>Requires</b> a
 *       complete embedding-model + vector-store setup on the bound agent.
 *       Default — preserves every pre-T790 site exactly, so nothing needs
 *       revalidation.</li>
 *   <li>{@link #VECTORLESS_STRUCTURED} — <b>Vectorless (Structured-Data) RAG</b>:
 *       the T392 catalog copilot ({@code POST /api/sn/{site}/copilot}). The user's
 *       prose is parsed into a grounded DSL query over the site's <em>declared</em>
 *       field schema (T385 {@code TurNLFacetParser}), executed by the search engine
 *       (field/facet/range — <em>zero embeddings</em>), and the default LLM answers
 *       strictly from the cited rows. <b>Needs only a default LLM</b> — no embedding
 *       model, no vector store, no chunking pipeline. The right choice for talking
 *       to a structured catalog / price list / spec sheet / model registry.</li>
 *   <li>{@link #HYBRID} — both surfaces enabled on the same site: the vector RAG
 *       chat for prose/unstructured questions AND the vectorless copilot for
 *       structured "which item matches these attributes" questions. Requires the
 *       full vector setup (it is a superset of {@link #VECTOR}).</li>
 * </ul>
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
public enum TurSNKnowledgeBaseMode {
    VECTOR,
    VECTORLESS_STRUCTURED,
    HYBRID;

    /**
     * Whether this mode exposes the vectorless (structured-data) copilot path —
     * i.e. {@code POST /api/sn/{site}/copilot} should be advertised as available.
     */
    public boolean isVectorless() {
        return this == VECTORLESS_STRUCTURED || this == HYBRID;
    }

    /**
     * Whether this mode requires the vector RAG setup (embedding model + vector
     * store on the bound agent). {@link #VECTORLESS_STRUCTURED} does not — it
     * needs only a default LLM.
     */
    public boolean needsVectorSetup() {
        return this == VECTOR || this == HYBRID;
    }

    /**
     * Lenient parse used wherever a stored/config string is turned into a mode.
     * Unknown, blank, or {@code null} values fall back to {@link #VECTOR} (the
     * legacy default), so a corrupt config row can never silently disable the
     * classic RAG path or throw.
     */
    public static TurSNKnowledgeBaseMode fromValue(String value) {
        if (value == null || value.isBlank()) {
            return VECTOR;
        }
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return VECTOR;
        }
    }
}
