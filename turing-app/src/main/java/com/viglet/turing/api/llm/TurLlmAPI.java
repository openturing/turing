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
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.QuestionAnswerAdvisor;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@Slf4j
@RestController
@RequestMapping("/api/llm")
@Tag(name = "LLM", description = "LLM")
public class TurLlmAPI {
    private final VectorStore chromaVectorStore;
    private final ChatModel chatModel;

    @Inject
    public TurLlmAPI(VectorStore chromaVectorStore, ChatModel chatModel) {
        super();
        this.chromaVectorStore = chromaVectorStore;
        this.chatModel = chatModel;
    }

    @GetMapping("chat")
    public AssistantMessage chat(@RequestParam(required = false, name = TurSNParamType.QUERY) String q) {
        return Objects.requireNonNull(ChatClient.builder(chatModel)
                .build().prompt()
                .advisors(new QuestionAnswerAdvisor(this.chromaVectorStore,
                        SearchRequest.builder().similarityThreshold(0.8d).topK(6).build()))
                .system("Using only this rag data. Answer in Portuguese.  You are a helpful assistant that can answer questions about the PDF document that uploaded by the user")
                .user(q)
                .call()
                .chatResponse()).getResults().getFirst().getOutput();
    }

    @GetMapping("chat-test")
    public AssistantMessage chatTest(@RequestParam(required = false, name = TurSNParamType.QUERY) String q) {
        addDocuments();
        return Objects.requireNonNull(ChatClient.builder(chatModel)
                .build().prompt()
                .advisors(new QuestionAnswerAdvisor(this.chromaVectorStore,
                        SearchRequest.builder().similarityThreshold(0.8d).topK(6).build()))
                .system("Using only this rag data. Answer in Portuguese.  You are a helpful assistant that can answer questions about the PDF document that uploaded by the user")
                .user(q)
                .call()
                .chatResponse()).getResults().getFirst().getOutput();
    }

    public void addDocuments() {
        chromaVectorStore.delete(Collections.singletonList("123"));
        List<Document> documents = new ArrayList<>();
        documents.add(new Document("123", """
                titulo: O Cavalo é branco.
                texto: Ele se chama Isaias.
                """, Map.of("id", "123")));
        chromaVectorStore.add(splitDocuments(documents));
    }

    public List<Document> splitDocuments(List<Document> documents) {
        TokenTextSplitter splitter = new TokenTextSplitter();
        return splitter.apply(documents);
    }
}
