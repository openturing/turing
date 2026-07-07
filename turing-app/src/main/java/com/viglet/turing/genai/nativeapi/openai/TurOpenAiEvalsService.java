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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.openai.client.OpenAIClient;
import com.openai.core.JsonValue;
import com.openai.models.evals.EvalCreateParams;
import com.openai.models.evals.EvalCreateResponse;
import com.openai.models.evals.runs.RunCreateParams;
import com.openai.models.evals.runs.RunCreateResponse;
import com.viglet.turing.genai.nativeapi.TurNativeProviderClient;
import com.viglet.turing.genai.nativeapi.openai.TurOpenAiEvalsPayload.EvalFixture;
import com.viglet.turing.genai.provider.llm.TurGenAiLlmProviderFactory;
import com.viglet.turing.persistence.model.llm.TurLLMInstance;
import com.viglet.turing.properties.TurConfigProperties;

import lombok.extern.slf4j.Slf4j;

/**
 * F.9 / §X.10.b — T168. Pushes an agent's rubric fixtures to the <b>OpenAI
 * Evals API</b> and starts a run whose server-side {@code label_model} grader
 * judges each fixture, offloading the rubric judging from Turing's local LLM
 * judge (T286) to OpenAI's infrastructure.
 *
 * <p>Two calls: {@code POST /v1/evals} registers the eval (custom item schema +
 * grader, built by {@link TurOpenAiEvalsPayload}); {@code POST /v1/evals/{id}/runs}
 * submits the fixtures as inline JSONL. Returns the eval/run ids + the report
 * URL so the operator can open the graded results on OpenAI's dashboard.
 *
 * <p>Strictly opt-in and fail-open: returns {@link Optional#empty()} when the
 * tier is disabled ({@code turing.evals.enabled=false}), the eval LLM is not
 * OpenAI-backed, no native client is available, there are no fixtures, or the
 * API call fails — the caller then keeps using the synchronous local judge.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Slf4j
@Service
public class TurOpenAiEvalsService {

    private final TurNativeProviderClient nativeClient;
    private final TurGenAiLlmProviderFactory providerFactory;
    private final TurConfigProperties configProperties;

    public TurOpenAiEvalsService(TurNativeProviderClient nativeClient,
            TurGenAiLlmProviderFactory providerFactory,
            TurConfigProperties configProperties) {
        this.nativeClient = nativeClient;
        this.providerFactory = providerFactory;
        this.configProperties = configProperties;
    }

    /** The ids + dashboard URL of a submitted eval run. */
    public record EvalSubmission(String evalId, String runId, String reportUrl, int fixtureCount) {
    }

    /** True when the OpenAI Evals push is enabled and {@code instance} is OpenAI-backed. */
    public boolean isSupported(TurLLMInstance instance) {
        return configProperties.getEvals().isEnabled() && isOpenAi(instance);
    }

    /**
     * Register an eval and start a run over {@code fixtures}. Returns empty when
     * the tier is off, the instance isn't OpenAI, no client resolves, fixtures
     * are empty, or the API rejects the request (all logged, never thrown).
     */
    public Optional<EvalSubmission> submit(TurLLMInstance instance, String evalLabel,
            List<EvalFixture> fixtures) {
        if (!isSupported(instance) || fixtures == null || fixtures.isEmpty()) {
            return Optional.empty();
        }
        Optional<OpenAIClient> client = nativeClient.openAi(instance);
        if (client.isEmpty()) {
            log.info("[Evals] no native OpenAI client for instance '{}' — skipping push",
                    instance.getId());
            return Optional.empty();
        }
        String graderModel = resolveGraderModel(instance);
        String name = "turing-eval:" + evalLabel;
        try {
            EvalCreateResponse eval = client.get().evals().create(EvalCreateParams.builder()
                    .putAllAdditionalBodyProperties(toJsonValues(
                            TurOpenAiEvalsPayload.evalBody(name, graderModel)))
                    .build());
            RunCreateResponse run = client.get().evals().runs().create(RunCreateParams.builder()
                    .evalId(eval.id())
                    .putAllAdditionalBodyProperties(toJsonValues(
                            TurOpenAiEvalsPayload.runBody(name + ":run", fixtures)))
                    .build());
            log.info("[Evals] pushed {} fixture(s) for '{}' — eval '{}', run '{}' ({})",
                    fixtures.size(), evalLabel, eval.id(), run.id(), run.status());
            return Optional.of(new EvalSubmission(eval.id(), run.id(), run.reportUrl(),
                    fixtures.size()));
        } catch (RuntimeException e) {
            log.warn("[Evals] push failed for '{}': {}", evalLabel, e.getMessage());
            return Optional.empty();
        }
    }

    private String resolveGraderModel(TurLLMInstance instance) {
        String configured = configProperties.getEvals().getGraderModel();
        if (StringUtils.hasText(configured)) {
            return configured;
        }
        return StringUtils.hasText(instance.getModelName()) ? instance.getModelName() : "gpt-4o-mini";
    }

    private boolean isOpenAi(TurLLMInstance instance) {
        try {
            return "openai".equals(providerFactory.getProvider(instance)
                    .getPluginType().toLowerCase(Locale.ROOT));
        } catch (RuntimeException e) {
            return false;
        }
    }

    private static Map<String, JsonValue> toJsonValues(Map<String, Object> body) {
        Map<String, JsonValue> values = new LinkedHashMap<>();
        body.forEach((key, value) -> values.put(key, JsonValue.from(value)));
        return values;
    }
}
