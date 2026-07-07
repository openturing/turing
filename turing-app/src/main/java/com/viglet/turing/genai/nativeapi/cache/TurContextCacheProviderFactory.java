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
package com.viglet.turing.genai.nativeapi.cache;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.viglet.turing.persistence.model.llm.TurLLMInstance;

/**
 * T499 / §X.20 — resolves the {@link TurContextCacheProvider} for an instance by
 * its vendor plugin type, mirroring {@code TurGenAiLlmProviderFactory}. When no
 * vendor implementation matches (the common case — OpenAI, Ollama, …) it returns
 * the {@link TurNoOpContextCacheProvider}, so the caller transparently stays on
 * the default (uncached / automatic) path.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Component
public class TurContextCacheProviderFactory {

    private final Map<String, TurContextCacheProvider> byType;
    private final TurContextCacheProvider noOp;

    public TurContextCacheProviderFactory(List<TurContextCacheProvider> providers,
            TurNoOpContextCacheProvider noOp) {
        this.noOp = noOp;
        this.byType = providers.stream()
                .filter(p -> !TurNoOpContextCacheProvider.PLUGIN_TYPE.equals(p.getPluginType()))
                .collect(Collectors.toMap(
                        p -> p.getPluginType().toLowerCase(Locale.ROOT),
                        Function.identity(),
                        (a, b) -> a));
    }

    /**
     * The provider for the instance's vendor, or the no-op when none matches.
     * Never returns {@code null}.
     */
    public TurContextCacheProvider resolve(TurLLMInstance instance) {
        String pluginType = pluginType(instance);
        if (pluginType == null) {
            return noOp;
        }
        TurContextCacheProvider provider = byType.get(pluginType);
        if (provider != null && provider.supports(instance)) {
            return provider;
        }
        return noOp;
    }

    private static String pluginType(TurLLMInstance instance) {
        if (instance == null || instance.getTurLLMVendor() == null) {
            return null;
        }
        String plugin = instance.getTurLLMVendor().getPlugin();
        if (!StringUtils.hasText(plugin)) {
            plugin = instance.getTurLLMVendor().getId();
        }
        return StringUtils.hasText(plugin) ? plugin.toLowerCase(Locale.ROOT) : null;
    }
}
