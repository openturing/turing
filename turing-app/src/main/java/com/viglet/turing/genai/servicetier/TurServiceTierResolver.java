/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.servicetier;

import java.util.Optional;

import org.springframework.stereotype.Component;

import com.viglet.turing.genai.provider.TurProviderOptionsParser;
import com.viglet.turing.persistence.model.agent.TurAIAgent;
import com.viglet.turing.persistence.model.llm.TurLLMInstance;
import com.viglet.turing.properties.TurConfigProperties;

/**
 * F.7 / §X.8.e — resolves the effective {@link TurServiceTier} for an
 * interactive (live) chat request.
 *
 * <p>Precedence: the agent's {@code service-tier} Request Option (T435 UI) wins;
 * then a per-instance {@code serviceTier} in {@code providerOptionsJson}; then
 * the global default {@code turing.genai.service-tier}. When nothing is set the
 * result is empty and the request builders leave {@code service_tier} unset, so
 * the provider applies its own default (byte-for-byte unchanged behaviour).
 *
 * <p>Background, non-interactive workloads (AI Insights, eval) no longer take
 * this path at all — they run on the Batch tier (T157/T159), which is the cost
 * counterpart to choosing {@code priority} here for live chat.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Component
public class TurServiceTierResolver {

    /** Per-agent Request Option key (matches the capability registry). */
    private static final String AGENT_OPTION_KEY = "service-tier";
    /** Per-instance providerOptionsJson key. */
    private static final String INSTANCE_OPTION_KEY = "serviceTier";

    private final TurProviderOptionsParser optionsParser;
    private final TurConfigProperties configProperties;

    public TurServiceTierResolver(TurProviderOptionsParser optionsParser,
            TurConfigProperties configProperties) {
        this.optionsParser = optionsParser;
        this.configProperties = configProperties;
    }

    /** Effective tier for a live chat turn, or empty to leave the provider default. */
    public Optional<TurServiceTier> resolveForChat(TurAIAgent agent, TurLLMInstance instance) {
        Optional<TurServiceTier> fromAgent = fromOptions(
                agent == null ? null : agent.getRequestOptionsJson(), AGENT_OPTION_KEY);
        if (fromAgent.isPresent()) {
            return fromAgent;
        }
        Optional<TurServiceTier> fromInstance = fromOptions(
                instance == null ? null : instance.getProviderOptionsJson(), INSTANCE_OPTION_KEY);
        if (fromInstance.isPresent()) {
            return fromInstance;
        }
        return TurServiceTier.fromOption(configProperties.getGenai().getServiceTier());
    }

    private Optional<TurServiceTier> fromOptions(String optionsJson, String key) {
        if (optionsJson == null || optionsJson.isBlank()) {
            return Optional.empty();
        }
        Object value = optionsParser.parse(optionsJson).get(key);
        return value == null ? Optional.empty() : TurServiceTier.fromOption(value.toString());
    }
}
