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

import org.springframework.stereotype.Component;

import com.viglet.turing.genai.provider.TurProviderOptionsParser;
import com.viglet.turing.persistence.model.agent.TurAIAgent;
import com.viglet.turing.persistence.model.llm.TurLLMInstance;

/**
 * F.10 / §X.11.a — resolves whether <b>OpenAI Predicted Outputs</b> are enabled
 * for an edit-style turn.
 *
 * <p>Precedence mirrors {@link com.viglet.turing.genai.servicetier.TurServiceTierResolver}:
 * the agent's {@code predicted-outputs} Request Option (T435 UI) wins; then a
 * per-instance {@code predictedOutputs} flag in {@code providerOptionsJson};
 * otherwise the feature is off. Opt-in by design — when nothing is set the
 * resolver returns {@code false} and callers run their normal completion path,
 * so behaviour is byte-for-byte unchanged.
 *
 * <p>Predicted Outputs is billed for rejected prediction tokens, so it must
 * never be on by default: it only pays off when the output mostly overlaps a
 * known base string (the document being edited, the previous answer being
 * rephrased — see {@link TurOpenAiPredictedOutputsService}).
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Component
public class TurPredictedOutputsResolver {

    /** Per-agent Request Option key (matches the capability registry). */
    private static final String AGENT_OPTION_KEY = "predicted-outputs";
    /** Per-instance providerOptionsJson key. */
    private static final String INSTANCE_OPTION_KEY = "predictedOutputs";

    private final TurProviderOptionsParser optionsParser;

    public TurPredictedOutputsResolver(TurProviderOptionsParser optionsParser) {
        this.optionsParser = optionsParser;
    }

    /** {@code true} when this agent/instance opted into Predicted Outputs. */
    public boolean enabledFor(TurAIAgent agent, TurLLMInstance instance) {
        Optional<Boolean> fromAgent = fromOptions(
                agent == null ? null : agent.getRequestOptionsJson(), AGENT_OPTION_KEY);
        if (fromAgent.isPresent()) {
            return fromAgent.get();
        }
        return fromOptions(instance == null ? null : instance.getProviderOptionsJson(),
                INSTANCE_OPTION_KEY).orElse(false);
    }

    private Optional<Boolean> fromOptions(String optionsJson, String key) {
        if (optionsJson == null || optionsJson.isBlank()) {
            return Optional.empty();
        }
        Object value = optionsParser.parse(optionsJson).get(key);
        return value == null ? Optional.empty() : Optional.of(Boolean.parseBoolean(value.toString()));
    }
}
