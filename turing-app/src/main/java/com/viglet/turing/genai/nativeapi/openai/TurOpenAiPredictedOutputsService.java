/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.nativeapi.openai;

import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.openai.client.OpenAIClient;
import com.openai.models.chat.completions.ChatCompletion;
import com.openai.models.chat.completions.ChatCompletionCreateParams;
import com.openai.models.chat.completions.ChatCompletionPredictionContent;
import com.viglet.turing.genai.nativeapi.TurNativeProviderClient;
import com.viglet.turing.persistence.model.llm.TurLLMInstance;

import lombok.extern.slf4j.Slf4j;

/**
 * F.10 / §X.11.a — runs an <b>edit-style</b> turn through the OpenAI
 * <b>Chat Completions</b> API ({@code /v1/chat/completions}) with the
 * <b>Predicted Outputs</b> ({@code prediction}) parameter.
 *
 * <p>When the desired output mostly overlaps a known base string, sending that
 * string as the prediction lets OpenAI skip re-generating the unchanged span,
 * cutting latency 3–5×. The classic use cases (§X.11) are "rephrase shorter" on
 * an existing answer and the regenerate-with-tweak loops in the chat-flow
 * variant generator / persona-prompt / locale-string editors (wired in T172).
 *
 * <p><b>Why Chat Completions and not the Responses API:</b> {@code prediction}
 * is a Chat Completions-only parameter — {@code ResponseCreateParams} exposes no
 * equivalent. The native agent chat turn ({@link TurOpenAiResponsesService}) is
 * unaffected; this is a dedicated, self-contained service for discrete edit
 * operations that already hold a base text, not for multi-turn agent chat.
 *
 * <p>The service is gated by {@link TurPredictedOutputsResolver}: callers only
 * route here when the agent/instance opted in (rejected prediction tokens are
 * billed). A non-OpenAI instance yields {@link Optional#empty()} so the caller
 * can fall back to its normal path.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Slf4j
@Service
public class TurOpenAiPredictedOutputsService {

    private static final String DEFAULT_MODEL = "gpt-4o-mini";

    private final TurNativeProviderClient providerClient;

    public TurOpenAiPredictedOutputsService(TurNativeProviderClient providerClient) {
        this.providerClient = providerClient;
    }

    /**
     * Produce an edited version of {@code predictedOutput} per the
     * {@code instruction}, sending the base text as the {@code prediction} so
     * OpenAI can fast-path the unchanged span.
     *
     * @param instance        the OpenAI-backed LLM instance (model, temperature,
     *                        credentials); a non-OpenAI instance → empty result
     * @param systemPrompt    optional system message (e.g. "You rewrite text…")
     * @param instruction     the user instruction describing the edit
     * @param predictedOutput the base text the result mostly overlaps; when blank
     *                        the call runs as a plain completion (no prediction)
     * @return the model's reply, or empty when the instance is not OpenAI / the
     *         model returned no content
     */
    public Optional<String> edit(TurLLMInstance instance, String systemPrompt, String instruction,
            String predictedOutput) {
        Optional<OpenAIClient> client = providerClient.openAi(instance);
        if (client.isEmpty()) {
            log.debug("[Native][OpenAI-Predicted] instance '{}' is not OpenAI — skipping prediction path",
                    instance == null ? null : instance.getId());
            return Optional.empty();
        }
        long t0 = System.currentTimeMillis();
        ChatCompletionCreateParams params = buildParams(instance, systemPrompt, instruction, predictedOutput);
        ChatCompletion completion = client.get().chat().completions().create(params);
        Optional<String> text = extractText(completion);
        log.info("[Native][OpenAI-Predicted] instance '{}' model '{}' predicted {} chars -> {} chars in {} ms",
                instance.getId(), resolveModel(instance),
                predictedOutput == null ? 0 : predictedOutput.length(),
                text.map(String::length).orElse(0), System.currentTimeMillis() - t0);
        return text;
    }

    ChatCompletionCreateParams buildParams(TurLLMInstance instance, String systemPrompt,
            String instruction, String predictedOutput) {
        ChatCompletionCreateParams.Builder builder = ChatCompletionCreateParams.builder()
                .model(resolveModel(instance));
        if (StringUtils.hasText(systemPrompt)) {
            builder.addSystemMessage(systemPrompt);
        }
        builder.addUserMessage(instruction == null ? "" : instruction);
        if (instance.getTemperature() != null) {
            builder.temperature(instance.getTemperature());
        }
        if (StringUtils.hasText(predictedOutput)) {
            builder.prediction(ChatCompletionPredictionContent.builder()
                    .content(predictedOutput)
                    .build());
        }
        return builder.build();
    }

    Optional<String> extractText(ChatCompletion completion) {
        if (completion == null || completion.choices().isEmpty()) {
            return Optional.empty();
        }
        return completion.choices().get(0).message().content();
    }

    private String resolveModel(TurLLMInstance instance) {
        return StringUtils.hasText(instance.getModelName()) ? instance.getModelName() : DEFAULT_MODEL;
    }
}
