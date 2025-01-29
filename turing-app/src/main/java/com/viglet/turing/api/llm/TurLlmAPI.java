/*
 * Copyright (C) 2016-2024 the original author or authors.
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package com.viglet.turing.api.llm;

import com.google.inject.Inject;
import com.viglet.turing.commons.sn.search.TurSNParamType;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
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
import dev.langchain4j.store.embedding.chroma.ChromaEmbeddingStore;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import dev.langchain4j.service.AiServices;

import java.util.*;
import java.util.function.Function;

@Slf4j
@RestController
@RequestMapping("/api/llm")
@Tag(name = "LLM", description = "LLM")
public class TurLlmAPI {
    public static final String PROMPT = "Using only this rag data. Answer in Portuguese.  You are a helpful assistant that can answer questions about the PDF document that uploaded by the user";
    private final ChromaEmbeddingStore chromaEmbeddingStore;
    private final EmbeddingModel embeddingModel;
    private final ChatLanguageModel chatLanguageModel;
    private final static boolean enabled = false;

    @Inject
    public TurLlmAPI() {
        super();
        if (enabled) {
            chromaEmbeddingStore = ChromaEmbeddingStore.builder()
                    .baseUrl("http://localhost:8000/")
                    .collectionName("turing")
                    .logRequests(true)
                    .logResponses(true)
                    .build();

            embeddingModel = OllamaEmbeddingModel.builder()
                    .baseUrl("http://localhost:11434")
                    .logRequests(true)
                    .logResponses(true)
                    .modelName("mistral")
                    .build();
            chatLanguageModel = OllamaChatModel.builder()
                    .baseUrl("http://localhost:11434")
                    .logRequests(true)
                    .logResponses(true)
                    .modelName("mistral")
                    .build();
        } else {
            chromaEmbeddingStore = null;
            embeddingModel = null;
            chatLanguageModel = null;
        }
    }

    @GetMapping("chat")
    public String chat(@RequestParam(required = false, name = TurSNParamType.QUERY) String q) {
        return assistant(q);
    }

    @GetMapping("chat-test")
    public String chatTest(@RequestParam(required = false, name = TurSNParamType.QUERY) String q) {
        if (enabled) {
            chromaEmbeddingStore.removeAll();
            addDocuments();
        }
        return assistant(q);
    }

    private String assistant(String q) {
        if (enabled) {
            QueryTransformer queryTransformer = new CompressingQueryTransformer(chatLanguageModel);
            Function<Object, String> systemMessageProvider = (memoryId) -> {
                if (memoryId.equals("1")) {
                    return PROMPT;
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

            return assistant.chat("1", q);
        }
        return "";
    }

    public void addDocuments() {
        if (enabled) {
            TextSegment segment1 = TextSegment.from("O Cavalo é branco. O nome dele é Isaias", new Metadata(Map.of("id", "123")));

            Embedding embedding1 = embeddingModel.embed(segment1).content();
            chromaEmbeddingStore.add(embedding1, segment1);

            TextSegment segment2 = TextSegment.from("A casa do padre é branca. O nome do Padre é Augusto", new Metadata(Map.of("id", "124")));
            Embedding embedding2 = embeddingModel.embed(segment2).content();
            chromaEmbeddingStore.add(embedding2, segment2);
        }
    }
}
