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

import java.util.Comparator;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.viglet.turing.genai.nativeapi.TurNativeCapability;
import com.viglet.turing.genai.nativeapi.TurNativeCapabilityService;
import com.viglet.turing.genai.nativeapi.TurNativeCapabilityService.EnabledCapability;
import com.viglet.turing.persistence.model.agent.TurAIAgent;
import com.viglet.turing.persistence.model.llm.TurLLMInstance;
import com.viglet.turing.persistence.repository.agent.TurAIAgentRepository;
import com.viglet.turing.persistence.repository.llm.TurLLMInstanceRepository;

import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * T148 / §X.6.b — orchestrates a real-time voice session for an AI agent: it
 * resolves the agent's LLM instance, verifies the {@code OPENAI_REALTIME_VOICE}
 * capability is enabled on that instance, primes the session instructions with
 * the agent's system prompt, and delegates the ephemeral-token minting to the
 * vendor's {@link TurRealtimeVoiceProvider}.
 *
 * <p>The session is minted out-of-band from the text-chat turn (it is not a
 * {@code TurNativeChatExecutor} dispatch): the browser then holds the WebSocket
 * transport to the vendor directly. Pre-conditions that the caller can fix
 * (agent disabled, no instance, capability off, vendor without voice) surface as
 * {@link IllegalArgumentException} (→ 400); transport/credential failures from
 * the provider surface as {@link TurRealtimeVoiceException} (→ 502).
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Slf4j
@Service
public class TurRealtimeVoiceSessionService {

    private final TurAIAgentRepository agentRepository;
    private final TurLLMInstanceRepository llmInstanceRepository;
    private final TurNativeCapabilityService capabilityService;
    private final TurRealtimeVoiceProviderFactory providerFactory;
    private final ObjectMapper objectMapper = JsonMapper.builder().build();

    public TurRealtimeVoiceSessionService(TurAIAgentRepository agentRepository,
            TurLLMInstanceRepository llmInstanceRepository,
            TurNativeCapabilityService capabilityService,
            TurRealtimeVoiceProviderFactory providerFactory) {
        this.agentRepository = agentRepository;
        this.llmInstanceRepository = llmInstanceRepository;
        this.capabilityService = capabilityService;
        this.providerFactory = providerFactory;
    }

    /** Whether voice can be served for the agent + (optional) instance, plus the resolved model/voice. */
    public record VoiceAvailability(boolean available, String model, String voice, String reason) {
        static VoiceAvailability unavailable(String reason) {
            return new VoiceAvailability(false, null, null, reason);
        }
    }

    /**
     * Mint a voice session for {@code agentId}. {@code model}/{@code voice}/{@code locale}
     * are optional hints; blank values fall back to the capability config and then
     * the provider defaults.
     */
    @Transactional(readOnly = true)
    public TurRealtimeVoiceSession createForAgent(String agentId, String llmInstanceId,
            String model, String voice, String locale) {
        TurAIAgent agent = enabledAgent(agentId);
        TurLLMInstance instance = resolveInstance(agent, llmInstanceId);

        TurRealtimeVoiceProvider provider = providerFactory.getProvider(instance)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Voice is not supported for the LLM instance bound to agent '" + agentId + "'"));

        Optional<EnabledCapability> voiceCap = enabledVoiceCapability(instance);
        if (voiceCap.isEmpty()) {
            throw new IllegalArgumentException(
                    "Realtime voice capability is not enabled on the LLM instance for agent '"
                            + agentId + "'");
        }

        VoiceConfig config = parseConfig(voiceCap.get().configJson());
        String resolvedModel = firstNonBlank(model, config.model(), provider.defaultModel());
        String resolvedVoice = firstNonBlank(voice, config.voice());
        String instructions = StringUtils.hasText(agent.getSystemPrompt())
                ? agent.getSystemPrompt() : null;

        log.info("[Voice] minting session for agent '{}' instance '{}' model '{}'",
                agent.getTitle(), instance.getId(), resolvedModel);
        return provider.createSession(instance,
                new TurRealtimeVoiceSessionRequest(resolvedModel, resolvedVoice, instructions, locale));
    }

    /**
     * Read-only probe used by the frontend to decide whether to show the voice
     * button. Never throws for the "voice not available" case — returns a
     * structured negative result instead.
     */
    @Transactional(readOnly = true)
    public VoiceAvailability availability(String agentId, String llmInstanceId) {
        TurAIAgent agent;
        try {
            agent = enabledAgent(agentId);
        } catch (IllegalArgumentException e) {
            return VoiceAvailability.unavailable(e.getMessage());
        }
        TurLLMInstance instance;
        try {
            instance = resolveInstance(agent, llmInstanceId);
        } catch (IllegalArgumentException e) {
            return VoiceAvailability.unavailable(e.getMessage());
        }
        Optional<TurRealtimeVoiceProvider> provider = providerFactory.getProvider(instance);
        if (provider.isEmpty()) {
            return VoiceAvailability.unavailable("vendor-unsupported");
        }
        Optional<EnabledCapability> voiceCap = enabledVoiceCapability(instance);
        if (voiceCap.isEmpty()) {
            return VoiceAvailability.unavailable("capability-disabled");
        }
        VoiceConfig config = parseConfig(voiceCap.get().configJson());
        String resolvedModel = firstNonBlank(config.model(), provider.get().defaultModel());
        return new VoiceAvailability(true, resolvedModel, config.voice(), null);
    }

    private TurAIAgent enabledAgent(String agentId) {
        TurAIAgent agent = agentRepository.findById(agentId)
                .orElseThrow(() -> new IllegalArgumentException("AI Agent not found: " + agentId));
        if (agent.getEnabled() != 1) {
            throw new IllegalArgumentException("AI Agent is disabled: " + agentId);
        }
        return agent;
    }

    /**
     * Resolves the LLM instance for the turn, mirroring {@code TurAIAgentChatAPI}:
     * an explicit (allow-listed) instance, or the agent's lowest-id default.
     */
    private TurLLMInstance resolveInstance(TurAIAgent agent, String llmInstanceId) {
        if (!StringUtils.hasText(llmInstanceId)) {
            return agent.getLlmInstances().stream()
                    .min(Comparator.comparing(TurLLMInstance::getId))
                    .orElseThrow(() -> new IllegalArgumentException(
                            "AI Agent '" + agent.getId() + "' has no LLM instance configured"));
        }
        TurLLMInstance instance = llmInstanceRepository.findById(llmInstanceId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "LLM instance not found: " + llmInstanceId));
        boolean allowed = agent.getLlmInstances().stream()
                .anyMatch(llm -> llm.getId().equals(llmInstanceId));
        if (!allowed) {
            throw new IllegalArgumentException("LLM instance '" + llmInstanceId
                    + "' is not configured for agent '" + agent.getId() + "'");
        }
        return instance;
    }

    private Optional<EnabledCapability> enabledVoiceCapability(TurLLMInstance instance) {
        return capabilityService.enabledFor(instance.getId(), "openai").stream()
                .filter(c -> c.capability() == TurNativeCapability.OPENAI_REALTIME_VOICE)
                .findFirst();
    }

    /** Per-capability {@code configJson} may carry preferred {@code model} / {@code voice}. */
    private VoiceConfig parseConfig(String configJson) {
        if (!StringUtils.hasText(configJson)) {
            return new VoiceConfig(null, null);
        }
        try {
            JsonNode node = objectMapper.readTree(configJson);
            return new VoiceConfig(textOrNull(node, "model"), textOrNull(node, "voice"));
        } catch (RuntimeException e) {
            log.debug("[Voice] could not parse capability configJson: {}", e.getMessage());
            return new VoiceConfig(null, null);
        }
    }

    private static String textOrNull(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isString() ? value.asString() : null;
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return null;
    }

    private record VoiceConfig(String model, String voice) {
    }
}
