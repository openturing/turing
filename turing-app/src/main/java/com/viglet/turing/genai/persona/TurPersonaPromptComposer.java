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

import java.util.ArrayList;
import java.util.List;

import org.springframework.ai.document.Document;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.viglet.turing.persistence.model.embedding.TurEmbeddingModel;
import com.viglet.turing.persistence.model.persona.TurPersona;
import com.viglet.turing.persistence.model.persona.TurPersonaLanguageStyle;
import com.viglet.turing.persistence.model.persona.TurPersonaTone;

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
 * @author Alexandre Oliveira
 * @since 2026.2.6
 */
@Component
public class TurPersonaPromptComposer {

    private final TurPersonaFewShotRetriever fewShotRetriever;

    public TurPersonaPromptComposer(TurPersonaFewShotRetriever fewShotRetriever) {
        this.fewShotRetriever = fewShotRetriever;
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

        StringBuilder sb = new StringBuilder();
        appendIfPresent(sb, persona.getSystemInstruction());

        appendStyleGuidelines(sb, persona);
        appendTermsBlock(sb, "# Required Vocabulary",
                "Naturally weave the following terms into your responses when they fit the topic:",
                persona.getMandatoryTerms());
        appendTermsBlock(sb, "# Forbidden Vocabulary",
                "Never use the following terms — rephrase if needed:",
                persona.getForbiddenTerms());

        if (persona.getBrandContextMcpServer() != null) {
            sb.append("\n\n# Brand Context\n");
            sb.append("You have access to brand-guideline tools provided by the '")
                    .append(persona.getBrandContextMcpServer().getTitle())
                    .append("' MCP server. Consult them whenever the user asks about brand voice, ");
            sb.append("messaging, or visual identity.");
        }

        appendFewShotBlock(sb, persona, embeddingModel, userQuery);

        if (StringUtils.hasText(basePrompt)) {
            sb.append("\n\n----\n").append(basePrompt);
        }

        return sb.toString();
    }

    private static void appendStyleGuidelines(StringBuilder sb, TurPersona persona) {
        TurPersonaTone tone = persona.getTone();
        TurPersonaLanguageStyle style = persona.getLanguageStyle();
        int verbosity = persona.getVerbosity();
        if (tone == null && style == null && verbosity == 0) {
            return;
        }

        if (sb.length() > 0) {
            sb.append("\n\n");
        }
        sb.append("# Style Guidelines\n");
        if (tone != null) {
            sb.append("- Tone: ").append(tone.name().toLowerCase()).append('\n');
        }
        sb.append("- Verbosity (1=terse, 5=expansive): ")
                .append(clampVerbosity(verbosity)).append('\n');
        if (style != null) {
            sb.append("- Language Style: ").append(style.name().toLowerCase()).append('\n');
        }
    }

    private static void appendTermsBlock(StringBuilder sb, String header, String intro, String raw) {
        List<String> terms = parseTerms(raw);
        if (terms.isEmpty()) {
            return;
        }
        sb.append("\n\n").append(header).append('\n').append(intro).append('\n');
        for (String t : terms) {
            sb.append("- ").append(t).append('\n');
        }
    }

    private void appendFewShotBlock(StringBuilder sb, TurPersona persona,
            TurEmbeddingModel embeddingModel, String userQuery) {
        if (persona.getFewShotStore() == null || embeddingModel == null
                || !StringUtils.hasText(userQuery)) {
            return;
        }
        List<Document> docs = fewShotRetriever.retrieve(persona, embeddingModel, userQuery);
        if (docs.isEmpty()) {
            return;
        }
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
    }

    private static void appendIfPresent(StringBuilder sb, String s) {
        if (StringUtils.hasText(s)) {
            sb.append(s.trim());
        }
    }

    private static int clampVerbosity(int v) {
        if (v < 1) return 1;
        if (v > 5) return 5;
        return v;
    }

    private static List<String> parseTerms(String raw) {
        List<String> out = new ArrayList<>();
        if (raw == null) {
            return out;
        }
        for (String t : raw.split("\\|")) {
            String trimmed = t.trim();
            if (!trimmed.isEmpty()) {
                out.add(trimmed);
            }
        }
        return out;
    }
}
