/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed to the Apache Software Foundation (ASF) under one or more contributor license
 * agreements. See the NOTICE file distributed with this work for additional information regarding
 * copyright ownership. The ASF licenses this file to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package com.viglet.turing.kb;

import java.util.Map;
import java.util.Optional;

import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.viglet.turing.genai.nativeapi.anthropic.TurAnthropicStructuredOutputsService;
import com.viglet.turing.genai.nativeapi.openai.TurOpenAiStructuredOutputsService;
import com.viglet.turing.persistence.dto.kb.TurThesaurusDraft;
import com.viglet.turing.persistence.dto.kb.TurThesaurusGenerationRequest;
import com.viglet.turing.persistence.model.llm.TurLLMInstance;
import com.viglet.turing.persistence.repository.llm.TurLLMInstanceRepository;
import com.viglet.turing.system.TurGlobalSettingsService;

import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.json.JsonMapper;

/**
 * T675 / §XL (Block AQ) — the default {@link TurThesaurusHierarchyGenerator}:
 * drafts a hierarchy on the default LLM through the T426 strict-schema
 * structured-output path (OpenAI {@code json_schema} / Anthropic forced tool), so
 * the reply is schema-valid JSON by construction rather than the free-text JSON
 * that smaller models truncate. The bound schema is {@link TurThesaurusDraft}'s
 * own shape — the structured (JSON) form of the T673 authority-file contract.
 *
 * <p>No provider fallback to free-text: if the default LLM is not a provider that
 * supports structured output (or returns nothing), it throws
 * {@link IllegalStateException} so the caller surfaces a clear 4xx/5xx rather than
 * silently returning a malformed tree.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Slf4j
@Service
public class TurLlmThesaurusHierarchyGenerator implements TurThesaurusHierarchyGenerator {

    private static final JsonMapper SCHEMA_JSON = JsonMapper.builder().build();

    private final TurGlobalSettingsService globalSettings;
    private final TurLLMInstanceRepository llmRepository;
    private final TurOpenAiStructuredOutputsService openAiStructured;
    private final TurAnthropicStructuredOutputsService anthropicStructured;

    public TurLlmThesaurusHierarchyGenerator(TurGlobalSettingsService globalSettings,
            TurLLMInstanceRepository llmRepository,
            TurOpenAiStructuredOutputsService openAiStructured,
            TurAnthropicStructuredOutputsService anthropicStructured) {
        this.globalSettings = globalSettings;
        this.llmRepository = llmRepository;
        this.openAiStructured = openAiStructured;
        this.anthropicStructured = anthropicStructured;
    }

    @Override
    public TurThesaurusDraft generate(TurThesaurusGenerationRequest request) {
        TurLLMInstance instance = resolveDefaultInstance();
        BeanOutputConverter<TurThesaurusDraft> converter =
                new BeanOutputConverter<>(TurThesaurusDraft.class);
        Map<String, Object> schema = parseSchema(converter.getJsonSchema());
        if (schema == null) {
            throw new IllegalStateException("Could not build the thesaurus draft schema");
        }
        String system = systemPrompt(request);
        String input = userInput(request);
        try {
            Optional<String> json = openAiStructured.complete(instance, system, input,
                    "thesaurus_draft", schema, false);
            if (json.isEmpty()) {
                json = anthropicStructured.complete(instance, system, input, "thesaurus_draft", schema);
            }
            if (json.isEmpty() || json.get().isBlank()) {
                throw new IllegalStateException(
                        "The default LLM did not return a structured thesaurus draft "
                                + "(structured output is supported on OpenAI / Anthropic instances)");
            }
            return converter.convert(json.get());
        } catch (IllegalStateException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new IllegalStateException("Thesaurus generation failed: " + e.getMessage(), e);
        }
    }

    private String systemPrompt(TurThesaurusGenerationRequest request) {
        return """
                You are a controlled-vocabulary (thesaurus) editor. Build a small, well-formed
                hierarchical microthesaurus for the given domain and language.

                Rules:
                - Every term has a stable short `id` (e.g. "t1", "t2") used to wire relations.
                - `broader` is the id of the parent (broader) concept, or null for a root.
                  Model a genuine broader/narrower tree — do NOT put unrelated branches under
                  one another.
                - `related` lists ids of associatively related (but not broader/narrower) terms.
                - `variations` are recognition surface forms: spelling variants, common
                  synonyms, singular/plural. Keep case/accent sensitivity false unless it truly
                  matters.
                - Use the requested language for every label and variation.
                - Produce at most %d terms. Prefer a shallow, high-quality tree over a large one.
                """.formatted(request.effectiveMaxTerms());
    }

    private String userInput(TurThesaurusGenerationRequest request) {
        StringBuilder sb = new StringBuilder();
        sb.append("Domain: ").append(request.domain()).append('\n');
        sb.append("Language (ISO): ").append(request.language()).append('\n');
        sb.append("Maximum terms: ").append(request.effectiveMaxTerms()).append('\n');
        if (StringUtils.hasText(request.guidance())) {
            sb.append("Additional guidance: ").append(request.guidance().trim()).append('\n');
        }
        return sb.toString();
    }

    private Map<String, Object> parseSchema(String jsonSchema) {
        if (!StringUtils.hasText(jsonSchema)) {
            return null;
        }
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> map = SCHEMA_JSON.readValue(jsonSchema, Map.class);
            return map;
        } catch (RuntimeException e) {
            log.warn("[T675] Could not parse the thesaurus draft schema: {}", e.getMessage());
            return null;
        }
    }

    private TurLLMInstance resolveDefaultInstance() {
        String defaultLlmId = globalSettings.getDefaultLlmId();
        if (!StringUtils.hasText(defaultLlmId)) {
            throw new IllegalStateException("No default LLM configured in Global Settings");
        }
        return llmRepository.findById(defaultLlmId)
                .orElseThrow(() -> new IllegalStateException(
                        "Default LLM instance not found: " + defaultLlmId));
    }
}
