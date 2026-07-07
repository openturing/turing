/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.persona;

import java.util.List;

import org.springframework.ai.document.Document;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.viglet.turing.persistence.model.embedding.TurEmbeddingModel;
import com.viglet.turing.persistence.model.persona.TurPersona;

/**
 * Fuses a {@link TurPersona} into the system prompt for a chat turn. The
 * output layout is:
 *
 * <pre>
 *   {persona.systemInstruction}
 *
 *   # Style Guidelines
 *   - Tone: ...
 *   - Verbosity (1-5): ...
 *   - Language Style: ...
 *   - Custom: ... (free-form)
 *
 *   # Required Vocabulary
 *   ...                  (only when persona declares mandatory terms)
 *
 *   # Forbidden Vocabulary
 *   ...                  (only when persona declares forbidden terms;
 *                         the post-LLM validator also enforces this)
 *
 *   # Brand Context
 *   ...                  (placeholder note when an MCP brand server is
 *                         attached; the actual content is retrieved by
 *                         the LLM via tool calls)
 *
 *   # Few-Shot Examples
 *   Q: ...
 *   A: ...
 *   ---
 *   ...                  (top-K from the persona's vector store)
 *
 *   ----
 *   {basePrompt}         (the agent's RAG system prompt, including any
 *                         retrieved knowledge chunks)
 * </pre>
 *
 * <p>The composer is purely a string builder — no I/O, no LLM calls. It
 * delegates few-shot lookup to {@link TurPersonaFewShotRetriever} and
 * lets the caller decide which embedding model to use.
 *
 * <p><b>T31 / §IV.5 caching:</b> the deterministic persona block (everything
 * from {@code systemInstruction} through {@code Brand Context}) is built
 * by {@link TurPersonaStaticPromptCache#composeStaticBlock(TurPersona)},
 * which is a Spring {@code @Cacheable} method keyed by persona id. Persona
 * edits invalidate that cache via
 * {@link TurPersonaStaticPromptEvictionListener}. The few-shot block stays uncached — it
 * depends on the user query (vector store retrieval) — and so does the
 * base-prompt suffix (per-turn RAG context).
 *
 * @author Alexandre Oliveira
 * @since 2026.2.6
 */
@Component
public class TurPersonaPromptComposer {

    /**
     * The delimiter inserted between the persona head (static block + few-shot)
     * and the wrapped base prompt. Exposed so the Block AL contributor pipeline
     * ({@code TurPromptAssemblyPipeline}) reproduces the exact same wrap
     * byte-for-byte.
     */
    public static final String PERSONA_BASE_SEPARATOR = "\n\n----\n";

    private final TurPersonaFewShotRetriever fewShotRetriever;
    private final TurPersonaStaticPromptCache staticPromptCache;

    public TurPersonaPromptComposer(TurPersonaFewShotRetriever fewShotRetriever,
            TurPersonaStaticPromptCache staticPromptCache) {
        this.fewShotRetriever = fewShotRetriever;
        this.staticPromptCache = staticPromptCache;
    }

    /**
     * @param persona         may be {@code null}; when {@code null} the base
     *                        prompt is returned unchanged.
     * @param embeddingModel  the model used to embed the user query for
     *                        few-shot retrieval; if {@code null}, no
     *                        few-shot block is added.
     * @param userQuery       the latest user message — drives few-shot
     *                        similarity search.
     * @param basePrompt      the agent-level system prompt (already
     *                        carrying RAG context when applicable).
     */
    public String compose(TurPersona persona, TurEmbeddingModel embeddingModel,
            String userQuery, String basePrompt) {
        if (persona == null) {
            return basePrompt == null ? "" : basePrompt;
        }

        // T31 — deterministic block goes through the cache bean so a
        // repeat turn for the same persona skips the StringBuilder work
        // (typically 1-3 KB of formatted prompt). The bean is a separate
        // @Component because Spring's @Cacheable proxy doesn't intercept
        // intra-class `this.method()` calls.
        StringBuilder sb = new StringBuilder(staticPromptCache.composeStaticBlock(persona));

        sb.append(fewShotBlock(persona, embeddingModel, userQuery));

        if (StringUtils.hasText(basePrompt)) {
            sb.append(PERSONA_BASE_SEPARATOR).append(basePrompt);
        }

        return sb.toString();
    }

    /**
     * The per-turn few-shot block (empty string when the persona has no
     * few-shot store, no embedding model / user query is supplied, or the
     * retrieval returns nothing). Exposed so the Block AL persona contributor
     * emits it as its own {@code PER_TURN} segment — one source of truth for the
     * exact few-shot text, shared with {@link #compose}.
     *
     * @since 2026.3.4
     */
    public String fewShotBlock(TurPersona persona, TurEmbeddingModel embeddingModel,
            String userQuery) {
        if (persona == null || persona.getFewShotStore() == null || embeddingModel == null
                || !StringUtils.hasText(userQuery)) {
            return "";
        }
        List<Document> docs = fewShotRetriever.retrieve(persona, embeddingModel, userQuery);
        if (docs.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("\n\n# Few-Shot Examples\n");
        sb.append("Mirror the tone and structure of the examples below when answering:\n\n");
        for (int i = 0; i < docs.size(); i++) {
            String text = docs.get(i).getText();
            if (!StringUtils.hasText(text)) {
                continue;
            }
            if (i > 0) {
                sb.append("\n---\n");
            }
            sb.append(text.trim()).append('\n');
        }
        return sb.toString();
    }
}
