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
package com.viglet.turing.genai.nativeapi.voice;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

import org.springframework.stereotype.Component;

import com.viglet.turing.genai.provider.llm.TurGenAiLlmProviderFactory;
import com.viglet.turing.persistence.model.llm.TurLLMInstance;

import lombok.extern.slf4j.Slf4j;

/**
 * T147 / §X.6.a — resolves the {@link TurRealtimeVoiceProvider} for a
 * {@link TurLLMInstance} by its plugin type, mirroring
 * {@code TurGenAiLlmProviderFactory}. Returns {@link Optional#empty()} when the
 * instance's vendor has no voice transport (today: anything other than OpenAI),
 * so the caller maps that to "voice unsupported for this agent's model".
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Slf4j
@Component
public class TurRealtimeVoiceProviderFactory {

    private final List<TurRealtimeVoiceProvider> providers;
    private final TurGenAiLlmProviderFactory llmProviderFactory;

    public TurRealtimeVoiceProviderFactory(List<TurRealtimeVoiceProvider> providers,
            TurGenAiLlmProviderFactory llmProviderFactory) {
        this.providers = providers;
        this.llmProviderFactory = llmProviderFactory;
    }

    /** The voice provider for {@code instance}, or empty when its vendor has none. */
    public Optional<TurRealtimeVoiceProvider> getProvider(TurLLMInstance instance) {
        if (instance == null) {
            return Optional.empty();
        }
        String pluginType;
        try {
            pluginType = llmProviderFactory.getProvider(instance).getPluginType()
                    .toLowerCase(Locale.ROOT);
        } catch (RuntimeException e) {
            log.debug("[Voice] could not resolve provider for instance '{}': {}",
                    instance.getId(), e.getMessage());
            return Optional.empty();
        }
        return getProviderForPluginType(pluginType);
    }

    /** The voice provider registered for {@code pluginType}, or empty. */
    public Optional<TurRealtimeVoiceProvider> getProviderForPluginType(String pluginType) {
        if (pluginType == null) {
            return Optional.empty();
        }
        String normalized = pluginType.toLowerCase(Locale.ROOT);
        return providers.stream()
                .filter(p -> normalized.equals(p.getPluginType()))
                .findFirst();
    }

    /** Whether any voice provider can serve {@code instance}. */
    public boolean isVoiceSupported(TurLLMInstance instance) {
        return getProvider(instance).isPresent();
    }
}
