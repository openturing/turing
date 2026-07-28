/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.structuredoutputs;

import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Service;

import com.viglet.turing.genai.nativeapi.anthropic.TurAnthropicStructuredOutputsService;
import com.viglet.turing.genai.nativeapi.openai.TurOpenAiStructuredOutputsService;
import com.viglet.turing.persistence.model.llm.TurLLMInstance;
import com.viglet.turing.system.TurStructuredCompletionPort;

import lombok.extern.slf4j.Slf4j;

/**
 * T829 / §LXI.5 (Block BN) — the {@link TurStructuredCompletionPort}
 * implementation: OpenAI {@code json_schema} first, then Anthropic's forced-tool
 * input, and nothing otherwise.
 *
 * <p>This is the same routing {@code TurCopilotPlanJudge} (T819) does for query
 * planning, lifted into a reusable bean so any "the model must answer with THIS
 * object" caller gets provider-side enforcement instead of asking for JSON in the
 * prompt and hoping — the recipe the ROADMAP Non-goals rule out and that weaker
 * models are recorded truncating.
 *
 * <p>{@code strict=false} on the OpenAI leg: schemas generated from a Java type by
 * {@code BeanOutputConverter} are not authored to OpenAI's strict-mode subset
 * (which demands {@code additionalProperties:false} plus every property in
 * {@code required}), but {@code json_schema} without strict still forces a
 * JSON-object reply of the right shape — no prose, no fences.
 *
 * <p><b>Never throws.</b> A provider error is logged and reported as
 * {@link Optional#empty()} so the caller degrades to its own fallback rather than
 * failing the feature.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Slf4j
@Service
public class TurProviderStructuredCompletion implements TurStructuredCompletionPort {

    private final TurOpenAiStructuredOutputsService openAiStructured;
    private final TurAnthropicStructuredOutputsService anthropicStructured;

    public TurProviderStructuredCompletion(TurOpenAiStructuredOutputsService openAiStructured,
            TurAnthropicStructuredOutputsService anthropicStructured) {
        this.openAiStructured = openAiStructured;
        this.anthropicStructured = anthropicStructured;
    }

    @Override
    public Optional<String> complete(TurLLMInstance instance, String systemPrompt,
            String userInput, String schemaName, Map<String, Object> schema) {
        if (instance == null || schema == null || schema.isEmpty()) {
            return Optional.empty();
        }
        try {
            Optional<String> openAi = openAiStructured.complete(instance, systemPrompt, userInput,
                    schemaName, schema, false);
            if (openAi.isPresent()) {
                return openAi;
            }
            return anthropicStructured.complete(instance, systemPrompt, userInput, schemaName,
                    schema);
        } catch (RuntimeException e) {
            log.warn("[StructuredOutputs] schema '{}' call failed on instance '{}': {}",
                    schemaName, instance.getId(), e.getMessage());
            return Optional.empty();
        }
    }
}
