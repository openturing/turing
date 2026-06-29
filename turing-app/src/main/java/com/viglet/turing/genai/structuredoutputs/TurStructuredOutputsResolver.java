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
package com.viglet.turing.genai.structuredoutputs;

import java.util.Optional;

import org.springframework.stereotype.Component;

import com.viglet.turing.genai.provider.TurProviderOptionsParser;
import com.viglet.turing.persistence.model.agent.TurAIAgent;
import com.viglet.turing.persistence.model.llm.TurLLMInstance;

/**
 * F.10 / §X.11.c–d — resolves whether <b>strict structured outputs</b> are
 * enabled for a turn. Cross-provider: the {@code structured-outputs} Request
 * Option is registered for <em>any</em> vendor, so this single resolver feeds
 * both the OpenAI leg (T173,
 * {@link com.viglet.turing.genai.nativeapi.openai.TurOpenAiStructuredOutputsService})
 * and the Anthropic leg (T174).
 *
 * <p>Precedence mirrors {@link com.viglet.turing.genai.servicetier.TurServiceTierResolver}:
 * the agent's {@code structured-outputs} Request Option (T435 UI) wins; then a
 * per-instance {@code structuredOutputs} flag in {@code providerOptionsJson};
 * otherwise off. Opt-in by design — when nothing is set the result is
 * {@code false} and callers keep their existing JSON-mode / tolerant-parse path,
 * so behaviour is unchanged.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Component
public class TurStructuredOutputsResolver {

    /** Per-agent Request Option key (matches the capability registry). */
    private static final String AGENT_OPTION_KEY = "structured-outputs";
    /** Per-instance providerOptionsJson key. */
    private static final String INSTANCE_OPTION_KEY = "structuredOutputs";

    private final TurProviderOptionsParser optionsParser;

    public TurStructuredOutputsResolver(TurProviderOptionsParser optionsParser) {
        this.optionsParser = optionsParser;
    }

    /** {@code true} when this agent/instance opted into strict structured outputs. */
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
