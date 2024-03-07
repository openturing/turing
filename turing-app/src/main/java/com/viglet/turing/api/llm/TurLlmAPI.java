/*
 * Copyright (C) 2016-2022 the original author or authors.
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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.HttpHeaders;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/llm")
@Tag(name = "LLM", description = "LLM")
public class TurLlmAPI {
    public static final String SAMPLE = "sample";
    public static final String GPT_3_5_TURBO = "gpt-3.5-turbo";
    public static final String SYSTEM = "system";
    public static final String USER = "user";
    public static final String BEARER = "Bearer ";
    public static final String JSON_OBJECT = "json_object";
    public static final String OPENAI_CHAT_COMPLETIONS = "https://api.openai.com/v1/chat/completions";
    public static final String DATA_SCHEMA_SYSTEM_ROLE = "Provide output in valid JSON. The data schema should be like this:";
    public static final String PROMPT = "Crie 25 perguntas e respostas em formato json para o texto abaixo:";
    private final String apiKey;

    public TurLlmAPI(@Value("${turing.openai.key}") String apiKey) {
        super();
        this.apiKey = apiKey;
    }

    @PostMapping
    public TurLlmQa llm(@RequestParam("text") String createQA) throws IOException, InterruptedException {
        TurLlmResponse response = chatGPT(PROMPT + createQA);
        return new ObjectMapper().readValue(response.getChoices().getFirst().getMessage().getContent(), TurLlmQa.class);
    }

    public TurLlmResponse chatGPT(String prompt) throws IOException, InterruptedException {

        TurLlmQa qa = TurLlmQa.builder()
                .questions(List.of(TurLlmQaQuestion.builder()
                        .answer(SAMPLE)
                        .question(SAMPLE)
                        .build())
                ).build();
        ObjectWriter ow = new ObjectMapper().writer().withDefaultPrettyPrinter();
        List<TurLlmRoleRequest> roles = List.of(
                TurLlmRoleRequest.builder()
                        .role(SYSTEM)
                        .content(DATA_SCHEMA_SYSTEM_ROLE + ow.writeValueAsString(qa))
                        .build(),
                TurLlmRoleRequest.builder()
                        .role(USER)
                        .content(prompt)
                        .build()
        );
        TurLlmRequest llmRequest = TurLlmRequest.builder()
                .model(GPT_3_5_TURBO)
                .responseFormat(TurLlmResponseFormatRequest.builder()
                        .type(JSON_OBJECT).build())
                .messages(roles)
                .build();
        try (HttpClient client = HttpClient.newBuilder().build()) {
            HttpRequest request = HttpRequest.newBuilder()
                    .setHeader(HttpHeaders.AUTHORIZATION, BEARER + this.apiKey)
                    .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .POST(HttpRequest.BodyPublishers.ofString(ow.writeValueAsString(llmRequest)))
                    .uri(URI.create(OPENAI_CHAT_COMPLETIONS)).build();
            return new ObjectMapper().readValue(client.send(request, HttpResponse.BodyHandlers.ofString()).body(),
                    TurLlmResponse.class);
        }

    }

}