/*
 *
 * Copyright (C) 2016-2025 the original author or authors.
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *   (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.viglet.turing.genai;

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;

import com.viglet.turing.genai.TurRagContextBuilder.RagInfrastructure;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class TurGenAiContext {
    private final VectorStore vectorStore;
    private final EmbeddingModel embeddingModel;
    private final ChatModel chatModel;
    private final boolean enabled;
    private final String systemPrompt;
    /**
     * Per-agent grounding policy (from {@code TurAIAgent#groundingMode}). When
     * {@code STRICT_RAG}, the SN RAG path prefixes a non-removable grounding
     * guard to the system prompt. {@code null} is treated as {@code OPEN} (e.g.
     * the {@link #disabled()} context), so it never forces grounding by accident.
     */
    private final com.viglet.turing.persistence.model.agent.TurAgentGroundingMode groundingMode;
    private final RagInfrastructure ragInfrastructure;
    /**
     * T500 / §X.19 — per-site full-context (retrieval-free) answering opt-in.
     * When {@code true} and the whole corpus fits {@link #fullContextTokenBudget},
     * the SN RAG path grounds over every chunk instead of top-K retrieval.
     */
    private final boolean fullContextEnabled;
    /** T500 — max estimated corpus tokens that still qualify for full-context. */
    private final int fullContextTokenBudget;
    public static final String COLLECTION_NAME = "turing";

    public static TurGenAiContext disabled() {
        return TurGenAiContext.builder()
                .enabled(false)
                .build();
    }

}
