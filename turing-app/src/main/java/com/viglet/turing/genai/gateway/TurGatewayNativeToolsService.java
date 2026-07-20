/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.gateway;

import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.springframework.stereotype.Service;

import com.viglet.turing.genai.TurAgentChatExecutor.ChatMessageItem;
import com.viglet.turing.genai.TurAgentChatExecutor.ChatResponse;
import com.viglet.turing.genai.nativeapi.TurNativeCapability;
import com.viglet.turing.genai.nativeapi.TurNativeCapabilityService;
import com.viglet.turing.genai.nativeapi.TurNativeCapabilityService.EnabledCapability;
import com.viglet.turing.genai.nativeapi.TurNativeProviderClient;
import com.viglet.turing.genai.nativeapi.anthropic.TurAnthropicMessagesService;
import com.viglet.turing.genai.nativeapi.gemini.TurGoogleGenAiNativeService;
import com.viglet.turing.genai.nativeapi.openai.TurOpenAiResponsesService;
import com.viglet.turing.genai.persona.TurPersonaModelCalibration;
import com.viglet.turing.genai.provider.llm.TurGenAiLlmProviderFactory;
import com.viglet.turing.persistence.model.llm.TurLLMInstance;

import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;

/**
 * T747 / §XLIX — exposes Turing's F.15 provider-native capabilities (web search,
 * web fetch, code execution, …) to a plain gateway chat call selected by the
 * {@code x-turing-tools} header — a unification pure proxies don't offer.
 *
 * <p>It reuses the vendor-native chat services directly (no {@code TurAIAgent}
 * required): the header's abstract function ids ({@code web_search}, mapped to
 * {@link com.viglet.turing.genai.nativeapi.capability.TurCapabilityFunctions})
 * are intersected with the capabilities an admin enabled on the resolved
 * instance ({@link TurNativeCapabilityService#enabledFor} — the level-1 matrix
 * gate), then dispatched per vendor. When nothing is enabled/supported the
 * gateway silently falls back to the plain model call.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Slf4j
@Service
public class TurGatewayNativeToolsService {

    private static final String TOKEN = "token";

    private final TurGenAiLlmProviderFactory providerFactory;
    private final TurNativeProviderClient nativeClient;
    private final TurNativeCapabilityService capabilityService;
    private final TurOpenAiResponsesService openAiResponses;
    private final TurAnthropicMessagesService anthropicMessages;
    private final TurGoogleGenAiNativeService geminiNative;

    public TurGatewayNativeToolsService(TurGenAiLlmProviderFactory providerFactory,
            TurNativeProviderClient nativeClient,
            TurNativeCapabilityService capabilityService,
            TurOpenAiResponsesService openAiResponses,
            TurAnthropicMessagesService anthropicMessages,
            TurGoogleGenAiNativeService geminiNative) {
        this.providerFactory = providerFactory;
        this.nativeClient = nativeClient;
        this.capabilityService = capabilityService;
        this.openAiResponses = openAiResponses;
        this.anthropicMessages = anthropicMessages;
        this.geminiNative = geminiNative;
    }

    /**
     * Whether the requested {@code functions} can be served by native tools on
     * this instance: at least one maps to an admin-enabled capability for the
     * instance's vendor AND the vendor client is available.
     */
    public boolean supports(TurLLMInstance instance, Set<String> functions) {
        if (functions == null || functions.isEmpty()) {
            return false;
        }
        try {
            String plugin = pluginType(instance);
            if (resolveCapabilities(instance, plugin, functions).isEmpty()) {
                return false;
            }
            return clientAvailable(plugin, instance);
        } catch (RuntimeException e) {
            log.debug("[Gateway] native-tools support check failed: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Runs a native-tool chat and returns the assembled assistant text (blocking).
     * Callers must gate on {@link #supports} first.
     */
    public String answer(TurLLMInstance instance, List<ChatMessageItem> history, String systemPrompt,
            Set<String> functions) {
        String plugin = pluginType(instance);
        List<EnabledCapability> capabilities = resolveCapabilities(instance, plugin, functions);
        Flux<ChatResponse> flux = dispatch(plugin, instance, history, systemPrompt, capabilities);
        List<String> parts = flux
                .filter(r -> TOKEN.equals(r.type()) && r.content() != null && !r.content().isEmpty())
                .map(ChatResponse::content)
                .collectList()
                .block();
        return parts == null ? "" : String.join("", parts);
    }

    // ---- Internals --------------------------------------------------------

    private Flux<ChatResponse> dispatch(String plugin, TurLLMInstance instance,
            List<ChatMessageItem> history, String systemPrompt, List<EnabledCapability> capabilities) {
        return switch (plugin) {
            case "openai" -> openAiResponses.chat(nativeClient.openAi(instance).orElseThrow(),
                    instance, history, systemPrompt, capabilities);
            case "anthropic" -> anthropicMessages.chat(nativeClient.anthropic(instance).orElseThrow(),
                    instance, history, systemPrompt, capabilities);
            case "gemini" -> geminiNative.chat(nativeClient.gemini(instance).orElseThrow(),
                    instance, history, systemPrompt, capabilities, TurPersonaModelCalibration.Params.NONE);
            default -> throw new IllegalStateException("Native tools unsupported for provider: " + plugin);
        };
    }

    /** Admin-enabled instance capabilities whose abstract function is in the request. */
    private List<EnabledCapability> resolveCapabilities(TurLLMInstance instance, String plugin,
            Set<String> functions) {
        return capabilityService.enabledFor(instance.getId(), plugin).stream()
                .filter(ec -> functions.contains(ec.capability().getFunction()))
                .toList();
    }

    private boolean clientAvailable(String plugin, TurLLMInstance instance) {
        return switch (plugin) {
            case "openai" -> nativeClient.openAi(instance).isPresent();
            case "anthropic" -> nativeClient.anthropic(instance).isPresent();
            case "gemini" -> nativeClient.gemini(instance).isPresent();
            default -> false;
        };
    }

    private String pluginType(TurLLMInstance instance) {
        return providerFactory.getProvider(instance).getPluginType().toLowerCase(Locale.ROOT);
    }

    // ---- Header → function-id normalization -------------------------------

    /**
     * Normalises one {@code x-turing-tools} token to the abstract function id used
     * by {@link TurNativeCapability#getFunction()} (hyphenated), tolerating common
     * spellings ({@code code_execution}/{@code code-interpreter} → {@code code-exec}).
     * Returns {@code null} for an empty token.
     */
    public static String normaliseFunction(String token) {
        if (token == null) {
            return null;
        }
        String s = token.trim().toLowerCase(Locale.ROOT).replace('_', '-');
        if (s.isEmpty()) {
            return null;
        }
        return switch (s) {
            case "web-search", "websearch", "search", "google-search" -> "web-search";
            case "web-fetch", "webfetch", "url-context", "fetch" -> "web-fetch";
            case "code-exec", "code-execution", "code-interpreter" -> "code-exec";
            case "file-search", "filesearch" -> "file-search";
            case "image-gen", "image-generation" -> "image-gen";
            default -> s;
        };
    }
}
