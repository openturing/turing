/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.transcription;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

/**
 * T687 / §XLII.1 — resolves the config-selected {@link TurTranscriptionProvider},
 * mirroring {@code TurRagRerankStrategyFactory} (collect implementations by their
 * declared type, route by an enum read from config).
 *
 * <p>{@link #resolveActive()} is the single entry point callers use — it reads
 * the effective strategy from {@link TurTranscriptionConfigResolver} and returns
 * the matching provider. Falls back to {@link TurTranscriptionProviderType#OPENAI}
 * when the requested type has no registered implementation, so an incomplete
 * config degrades to the always-present OpenAI-compatible path instead of
 * throwing.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Slf4j
@Component
public class TurTranscriptionProviderFactory {

    private final Map<TurTranscriptionProviderType, TurTranscriptionProvider> byType =
            new EnumMap<>(TurTranscriptionProviderType.class);
    private final TurTranscriptionConfigResolver configResolver;

    public TurTranscriptionProviderFactory(List<TurTranscriptionProvider> providers,
            TurTranscriptionConfigResolver configResolver) {
        for (TurTranscriptionProvider provider : providers) {
            byType.put(provider.getType(), provider);
        }
        this.configResolver = configResolver;
        log.info("[Transcription] providers registered: {}", byType.keySet());
    }

    /**
     * @return the provider for {@code type}, or the {@code OPENAI} provider when
     *         {@code type} has no registered implementation.
     */
    public TurTranscriptionProvider resolve(TurTranscriptionProviderType type) {
        TurTranscriptionProvider provider = byType.get(type);
        if (provider == null) {
            log.warn("[Transcription] no provider for {}; falling back to OPENAI", type);
            provider = byType.get(TurTranscriptionProviderType.OPENAI);
        }
        return provider;
    }

    /** Resolve the provider for the currently configured (effective) strategy. */
    public TurTranscriptionProvider resolveActive() {
        return resolve(configResolver.resolve().type());
    }
}
