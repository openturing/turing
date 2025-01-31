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

package com.viglet.turing.llm;

import com.viglet.turing.api.llm.TurAssistant;
import com.viglet.turing.api.llm.TurChatMessage;
import com.viglet.turing.client.sn.job.TurSNJobItem;
import com.viglet.turing.client.sn.job.TurSNJobItems;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.document.splitter.DocumentByCharacterSplitter;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.ollama.OllamaChatModel;
import dev.langchain4j.model.ollama.OllamaEmbeddingModel;
import dev.langchain4j.rag.DefaultRetrievalAugmentor;
import dev.langchain4j.rag.RetrievalAugmentor;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.content.retriever.EmbeddingStoreContentRetriever;
import dev.langchain4j.rag.query.transformer.CompressingQueryTransformer;
import dev.langchain4j.rag.query.transformer.QueryTransformer;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.store.embedding.EmbeddingStoreIngestor;
import dev.langchain4j.store.embedding.chroma.ChromaEmbeddingStore;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.function.Function;

@Component
public class TurLlm {
    public static final String MODIFICATION_DATE = "modification_date";
    public static final String PUBLICATION_DATE = "publication_date";
    public static final String URL = "url";
    public static final String ID = "id";
    public static final String LOCALE = "locale";
    public static final String SOURCE_APPS = "sourceApps";
    public static final String SITES = "sites";
    public static final String TITLE = "title";
    public static final String ABSTRACT = "abstract";
    public static final String TEXT = "text";
    public static final String MISTRAL = "mistral";
    public static final String OLLAMA_URL = "http://localhost:11434";
    public static final String COLLECTION_NAME = "turing";
    public static final String CHROMA_URL = "http://localhost:8000/";
    private final ChromaEmbeddingStore chromaEmbeddingStore;
    private final EmbeddingModel embeddingModel;
    private final ChatLanguageModel chatLanguageModel;
    private final boolean enabledAi;

    @Autowired
    public TurLlm(@Value(value = "${turing.ai.enabled:false}") boolean enabledAi) {
        this.enabledAi = enabledAi;
        if (enabledAi) {
            chromaEmbeddingStore = ChromaEmbeddingStore.builder()
                    .baseUrl(CHROMA_URL)
                    .collectionName(COLLECTION_NAME)
                    .logRequests(true)
                    .logResponses(true)
                    .build();
            embeddingModel = OllamaEmbeddingModel.builder()
                    .baseUrl(OLLAMA_URL)
                    .logRequests(true)
                    .logResponses(true)
                    .modelName(MISTRAL)
                    .build();
            chatLanguageModel = OllamaChatModel.builder()
                    .baseUrl(OLLAMA_URL)
                    .logRequests(true)
                    .logResponses(true)
                    .modelName(MISTRAL)
                    .temperature(0.8)
                    .topK(6)
                    .build();
        }
        else {
            chromaEmbeddingStore = null;
            embeddingModel =  null;
            chatLanguageModel = null;
        }
    }
    public TurChatMessage assistant(String prompt, String q) {
        if (enabledAi) {
            QueryTransformer queryTransformer = new CompressingQueryTransformer(chatLanguageModel);
            Function<Object, String> systemMessageProvider = (memoryId) -> {
                if (memoryId.equals("1")) {
                    return prompt;
                } else {
                    return "You are a helpful assistant.";
                }
            };
            ContentRetriever contentRetriever = EmbeddingStoreContentRetriever.builder()
                    .embeddingStore(chromaEmbeddingStore)
                    .embeddingModel(embeddingModel)
                    .maxResults(2)
                    .minScore(0.6)
                    .build();

            RetrievalAugmentor retrievalAugmentor = DefaultRetrievalAugmentor.builder()
                    .queryTransformer(queryTransformer)
                    .contentRetriever(contentRetriever)
                    .build();

            TurAssistant assistant = AiServices.builder(TurAssistant.class)
                    .chatLanguageModel(chatLanguageModel)
                    .systemMessageProvider(systemMessageProvider)
                    .retrievalAugmentor(retrievalAugmentor)
                    .chatMemoryProvider(
                            sessionId -> MessageWindowChatMemory.withMaxMessages(10))
                    .build();
            return new  TurChatMessage(assistant.chat("1", q));
        }
        return new TurChatMessage("");
    }
    public void addDocuments(TurSNJobItems turSNJobItems) {
        if (enabledAi) {
            System.out.println("addDocuments");
            turSNJobItems.getTuringDocuments().forEach(jobItem -> {
                StringBuilder sb = new StringBuilder();
                addAttributes(jobItem, sb);
                addDocument(sb.toString(), new Metadata(setMetadata(jobItem)));
            });
            System.out.println("added Documents");
        }
    }

    public void removeAllDocuments() {
        chromaEmbeddingStore.removeAll();
    }
    public void addDocument(String text, Metadata metadata) {
        Document document = new Document(text, metadata);
        DocumentByCharacterSplitter documentByCharacterSplitter =
                new DocumentByCharacterSplitter(1024, 0);
        EmbeddingStoreIngestor embeddingStoreIngestor = EmbeddingStoreIngestor.builder()
                .documentSplitter(documentByCharacterSplitter)
                .embeddingModel(embeddingModel)
                .embeddingStore(chromaEmbeddingStore)
                .build();
        embeddingStoreIngestor.ingest(document);
    }
    private void addAttributes(TurSNJobItem jobItem, StringBuilder sb) {
        if (enabledAi) {
            String[] allowedAttributes = {TITLE, ABSTRACT, TEXT};
            jobItem.getAttributes().forEach((key, value) -> {
                if (Arrays.asList(allowedAttributes).contains(key))
                    sb.append(value).append(System.lineSeparator());
            });
        }
    }

    @NotNull
    private Map<String, Object> setMetadata(TurSNJobItem jobItem) {
        Map<String, Object> metadata = new HashMap<>();
        if (enabledAi) {
            metadata.put(ID, jobItem.getId());
            metadata.put(LOCALE, jobItem.getLocale().toString());
            metadata.put(SOURCE_APPS, jobItem.getProviderName());
            metadata.put(SITES, jobItem.getSiteNames().getFirst());
            if (jobItem.getAttributes().containsKey(MODIFICATION_DATE)) {
                metadata.put(MODIFICATION_DATE, jobItem.getAttributes().get(MODIFICATION_DATE));
            }
            if (jobItem.getAttributes().containsKey(PUBLICATION_DATE)) {
                metadata.put(PUBLICATION_DATE, jobItem.getAttributes().get(PUBLICATION_DATE));
            }
            if (jobItem.getAttributes().containsKey(URL)) {
                metadata.put(URL, jobItem.getAttributes().get(URL));
            }
        }
        return metadata;
    }
}
