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
package com.viglet.turing.genai.nativeapi;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.google.genai.Client;
import com.google.genai.types.HttpOptions;
import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.viglet.turing.genai.provider.TurProviderOptionsParser;
import com.viglet.turing.genai.provider.llm.TurGenAiLlmProvider;
import com.viglet.turing.genai.provider.llm.TurGenAiLlmProviderFactory;
import com.viglet.turing.persistence.model.llm.TurLLMInstance;
import com.viglet.turing.system.security.TurSecretCryptoService;

import lombok.extern.slf4j.Slf4j;

/**
 * T130 / §X.2 — default {@link TurNativeProviderClient}. Builds the native
 * OpenAI / Anthropic SDK clients from a {@link TurLLMInstance}, reusing the
 * established plumbing: the provider plugin type decides the vendor, the API
 * key is decrypted through {@link TurSecretCryptoService}, and the base URL is
 * resolved from {@code providerOptionsJson.baseUrl} → {@code instance.url} →
 * the vendor default — the same precedence the Spring AI providers use.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
@Slf4j
@Component
public class TurNativeProviderClientImpl implements TurNativeProviderClient {

    private static final String OPENAI_DEFAULT_BASE_URL = "https://api.openai.com/v1";
    private static final String ANTHROPIC_DEFAULT_BASE_URL = "https://api.anthropic.com";
    /** Google AI Studio (Gemini Developer API) default endpoint. */
    private static final String GEMINI_DEFAULT_BASE_URL = "https://generativelanguage.googleapis.com";

    private final TurGenAiLlmProviderFactory providerFactory;
    private final TurSecretCryptoService secretCryptoService;
    private final TurProviderOptionsParser optionsParser;
    private final TurNativeCapabilityService capabilityService;

    public TurNativeProviderClientImpl(TurGenAiLlmProviderFactory providerFactory,
            TurSecretCryptoService secretCryptoService,
            TurProviderOptionsParser optionsParser,
            TurNativeCapabilityService capabilityService) {
        this.providerFactory = providerFactory;
        this.secretCryptoService = secretCryptoService;
        this.optionsParser = optionsParser;
        this.capabilityService = capabilityService;
    }

    @Override
    public Optional<OpenAIClient> openAi(TurLLMInstance instance) {
        if (!"openai".equals(pluginType(instance))) {
            return Optional.empty();
        }
        NativeCredentials creds = credentials(instance, OPENAI_DEFAULT_BASE_URL);
        if (!StringUtils.hasText(creds.apiKey())) {
            log.warn("[Native] OpenAI instance '{}' has no API key — native path unavailable",
                    instance.getId());
            return Optional.empty();
        }
        return Optional.of(OpenAIOkHttpClient.builder()
                .baseUrl(creds.baseUrl())
                .apiKey(creds.apiKey())
                .build());
    }

    @Override
    public Optional<AnthropicClient> anthropic(TurLLMInstance instance) {
        if (!"anthropic".equals(pluginType(instance))) {
            return Optional.empty();
        }
        NativeCredentials creds = credentials(instance, ANTHROPIC_DEFAULT_BASE_URL);
        if (!StringUtils.hasText(creds.apiKey())) {
            log.warn("[Native] Anthropic instance '{}' has no API key — native path unavailable",
                    instance.getId());
            return Optional.empty();
        }
        var builder = AnthropicOkHttpClient.builder().apiKey(creds.apiKey()).maxRetries(2);
        if (!ANTHROPIC_DEFAULT_BASE_URL.equals(creds.baseUrl())) {
            builder.baseUrl(creds.baseUrl());
        }
        return Optional.of(builder.build());
    }

    @Override
    public Optional<Client> gemini(TurLLMInstance instance) {
        if (!"gemini".equals(pluginType(instance))) {
            return Optional.empty();
        }
        NativeCredentials creds = credentials(instance, GEMINI_DEFAULT_BASE_URL);
        if (!StringUtils.hasText(creds.apiKey())) {
            log.warn("[Native] Gemini instance '{}' has no API key — native path unavailable",
                    instance.getId());
            return Optional.empty();
        }
        Client.Builder builder = Client.builder().apiKey(creds.apiKey());
        // Only override the endpoint when the instance configures a non-default
        // base URL (e.g. a proxy); otherwise let the SDK use its own default so
        // the Vertex/Developer-API resolution stays the SDK's responsibility.
        if (!GEMINI_DEFAULT_BASE_URL.equals(creds.baseUrl())) {
            builder.httpOptions(HttpOptions.builder().baseUrl(creds.baseUrl()).build());
        }
        return Optional.of(builder.build());
    }

    @Override
    public boolean hasCapability(String instanceId, TurNativeCapability capability) {
        return capabilityService.hasCapability(instanceId, capability);
    }

    @Override
    public NativeCredentials credentials(TurLLMInstance instance) {
        String defaultBaseUrl = switch (pluginType(instance)) {
            case "anthropic" -> ANTHROPIC_DEFAULT_BASE_URL;
            case "gemini" -> GEMINI_DEFAULT_BASE_URL;
            default -> OPENAI_DEFAULT_BASE_URL;
        };
        return credentials(instance, defaultBaseUrl);
    }

    private NativeCredentials credentials(TurLLMInstance instance, String defaultBaseUrl) {
        Map<String, Object> options = optionsParser.parse(instance.getProviderOptionsJson());
        String baseUrl = firstNonBlank(optionsParser.stringValue(options, "baseUrl"),
                instance.getUrl(), defaultBaseUrl);
        String apiKey = StringUtils.hasText(instance.getApiKeyEncrypted())
                ? secretCryptoService.decrypt(instance.getApiKeyEncrypted())
                : null;
        return new NativeCredentials(apiKey, baseUrl);
    }

    private String pluginType(TurLLMInstance instance) {
        TurGenAiLlmProvider provider = providerFactory.getProvider(instance);
        return provider.getPluginType().toLowerCase(Locale.ROOT);
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return null;
    }
}
