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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import com.viglet.turing.filesystem.commons.TurFileUtils;
import com.viglet.turing.nlp.TurNLPUtils;
import com.viglet.turing.nlp.output.blazon.RedactionScript;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.HttpHeaders;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Optional;

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

    private final String apiKey;

    private final TurNLPUtils turNLPUtils;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Inject
    public TurLlmAPI(@Value("${turing.openai.key}") String apiKey,
                     TurNLPUtils turNLPUtils) {
        super();
        this.apiKey = apiKey;
        this.turNLPUtils = turNLPUtils;
    }

    @PostMapping
    public TurLlmQa llm(@RequestParam("text") String createQA) throws IOException, InterruptedException {
        String base_prompt = "Create 25 questions and answers in json format for the text below:";
        TurLlmResponse response = chatGPT(base_prompt + createQA);
        return getFirstContentFromMessage(response).map(content -> {
            try {
                return objectMapper
                        .readValue(content,TurLlmQa.class);
            } catch (JsonProcessingException e) {
                log.error(e.getMessage(), e);
            }
            return new TurLlmQa();
        }).orElse(new TurLlmQa());
    }

    @PostMapping(value = "/validate/file/blazon", produces = MediaType.APPLICATION_XML_VALUE)
    public RedactionScript validateFile(@RequestParam("file") MultipartFile multipartFile)
            throws IOException, InterruptedException {
        final String text = TurFileUtils.documentToText(multipartFile);
        String base_prompt = """
                In the sentence below, give me the list of:
                - organization named entity
                - location named entity
                - person named entity
                - miscellaneous named entity.
                - phone named entity.
                - passport named entity.
                - document id named entity.
                Format the output in json with the following keys:
                - ORGANIZATION for organization named entity
                - LOCATION for location named entity
                - PERSON for person named entity
                - MISCELLANEOUS for miscellaneous named entity.
                - PHONE for phone named entity.
                - PASSPORT for passport named entity.
                - PERSONAL_ID for document id named entity.
                Sentence below:
                """;
        TurLlmResponse turLlmResponse = geOpenAiResponse(List.of(
                TurLlmRoleRequest.builder()
                        .role(USER)
                        .content(base_prompt + text)
                        .build()));
        return getFirstContentFromMessage(turLlmResponse).map(content -> {
            try {
                return turNLPUtils.createRedactionScript( objectMapper
                        .readValue(content,TurLlmEntities.class));
            } catch (JsonProcessingException e) {
                log.error(e.getMessage(), e);
            }
            return new RedactionScript();
        }).orElse(new RedactionScript());

    }

    public TurLlmResponse chatGPT(String prompt) throws IOException, InterruptedException {
        return geOpenAiResponse(List.of(
                TurLlmRoleRequest.builder()
                        .role(SYSTEM)
                        .content(DATA_SCHEMA_SYSTEM_ROLE + objectMapper.writer().writeValueAsString(getQaSample()))
                        .build(),
                TurLlmRoleRequest.builder()
                        .role(USER)
                        .content(prompt)
                        .build()));
    }

    private TurLlmResponse geOpenAiResponse(List<TurLlmRoleRequest> roles) throws IOException, InterruptedException {
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
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writer().writeValueAsString(llmRequest)))
                    .uri(URI.create(OPENAI_CHAT_COMPLETIONS)).build();
            return new ObjectMapper().readValue(client.send(request, HttpResponse.BodyHandlers.ofString()).body(),
                    TurLlmResponse.class);
        }
    }

    private static TurLlmQa getQaSample() {
        return TurLlmQa.builder()
                .questions(List.of(TurLlmQaQuestion.builder()
                        .answer(SAMPLE)
                        .question(SAMPLE)
                        .build())
                ).build();
    }

    private static Optional<String> getFirstContentFromMessage(TurLlmResponse response) {
        return Optional.ofNullable(response)
                .map(TurLlmResponse::getChoices)
                .map(List::getFirst)
                .map(TurLlmChoiceResponse::getMessage)
                .map(TurLlmMessageResponse::getContent);
    }
}