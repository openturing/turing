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

import com.viglet.turing.persistence.model.llm.TurLLMInstance;
import com.viglet.turing.persistence.model.sn.genai.TurSNSiteGenAi;
import com.viglet.turing.persistence.model.store.TurStoreInstance;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.ollama.OllamaChatModel;
import dev.langchain4j.model.ollama.OllamaEmbeddingModel;
import dev.langchain4j.store.embedding.chroma.ChromaEmbeddingStore;
import jakarta.persistence.Column;
import lombok.Getter;

@Getter
public class TurGenAiContext {
    private final ChromaEmbeddingStore chromaEmbeddingStore;
    private final EmbeddingModel embeddingModel;
    private final ChatLanguageModel chatLanguageModel;
    private final boolean enabled;
    private final String systemPrompt;
    public static final String COLLECTION_NAME = "turing";
    
    public TurGenAiContext(TurSNSiteGenAi turSNSiteGenAi) {
        this.chromaEmbeddingStore = setEmbeddingStore(turSNSiteGenAi.getTurStoreInstance());
        this.embeddingModel = setEmbeddingModel(turSNSiteGenAi.getTurLLMInstance());
        this.chatLanguageModel = setChatModel(turSNSiteGenAi.getTurLLMInstance());
        this.systemPrompt = turSNSiteGenAi.getSystemPrompt();
        this.enabled = turSNSiteGenAi.isEnabled();
    }

    private ChromaEmbeddingStore setEmbeddingStore(TurStoreInstance turStoreInstance) {
        return ChromaEmbeddingStore.builder()
                .baseUrl(turStoreInstance.getUrl())
                .collectionName(COLLECTION_NAME)
                .logRequests(true)
                .logResponses(true)
                .build();
    }
    private OllamaEmbeddingModel setEmbeddingModel(TurLLMInstance turLLMInstance) {
        return OllamaEmbeddingModel.builder()
                .baseUrl(turLLMInstance.getUrl())
                .logRequests(true)
                .logResponses(true)
                .modelName(turLLMInstance.getModelName())
                .build();

    }

    private OllamaChatModel setChatModel(TurLLMInstance turLLMInstance) {
        return  OllamaChatModel.builder()
                .baseUrl(turLLMInstance.getUrl())
                .logRequests(true)
                .logResponses(true)
                .modelName(turLLMInstance.getModelName())
                .temperature(turLLMInstance.getTemperature())
                .topK(turLLMInstance.getTopK())
                .build();
    }

}
