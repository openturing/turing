/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.nativeapi.voice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.viglet.turing.genai.nativeapi.TurNativeCapability;
import com.viglet.turing.genai.nativeapi.TurNativeCapabilityService;
import com.viglet.turing.genai.nativeapi.TurNativeCapabilityService.EnabledCapability;
import com.viglet.turing.genai.nativeapi.voice.TurRealtimeVoiceSessionService.VoiceAvailability;
import com.viglet.turing.persistence.model.agent.TurAIAgent;
import com.viglet.turing.persistence.model.llm.TurLLMInstance;
import com.viglet.turing.persistence.repository.agent.TurAIAgentRepository;
import com.viglet.turing.persistence.repository.llm.TurLLMInstanceRepository;

@ExtendWith(MockitoExtension.class)
class TurRealtimeVoiceSessionServiceTest {

    @Mock private TurAIAgentRepository agentRepository;
    @Mock private TurLLMInstanceRepository llmInstanceRepository;
    @Mock private TurNativeCapabilityService capabilityService;
    @Mock private TurRealtimeVoiceProviderFactory providerFactory;
    @Mock private TurRealtimeVoiceProvider provider;

    private TurRealtimeVoiceSessionService service;

    private TurLLMInstance instance;
    private TurAIAgent agent;

    @BeforeEach
    void setUp() {
        service = new TurRealtimeVoiceSessionService(agentRepository, llmInstanceRepository,
                capabilityService, providerFactory);
        instance = new TurLLMInstance();
        instance.setId("inst-1");
        agent = new TurAIAgent();
        agent.setId("agent-1");
        agent.setTitle("Voice Agent");
        agent.setEnabled(1);
        agent.setSystemPrompt("You are Marina, a helpful concierge.");
        agent.setLlmInstances(Set.of(instance));
    }

    private void stubVoiceEnabled(String configJson) {
        when(capabilityService.enabledFor("inst-1", "openai")).thenReturn(List.of(
                new EnabledCapability(TurNativeCapability.OPENAI_REALTIME_VOICE, configJson)));
    }

    @Test
    void createForAgentMintsWithResolvedModelAndInstructions() {
        when(agentRepository.findById("agent-1")).thenReturn(Optional.of(agent));
        when(providerFactory.getProvider(instance)).thenReturn(Optional.of(provider));
        when(provider.defaultModel()).thenReturn("gpt-realtime");
        stubVoiceEnabled(null);
        TurRealtimeVoiceSession minted = new TurRealtimeVoiceSession("openai", "ek_x", 1L,
                "gpt-realtime", "marin", "wss://api.openai.com/v1/realtime?model=gpt-realtime");
        when(provider.createSession(eq(instance), any())).thenReturn(minted);

        TurRealtimeVoiceSession result = service.createForAgent("agent-1", null, null, null, "pt-BR");

        assertThat(result).isSameAs(minted);
        ArgumentCaptor<TurRealtimeVoiceSessionRequest> captor =
                ArgumentCaptor.forClass(TurRealtimeVoiceSessionRequest.class);
        org.mockito.Mockito.verify(provider).createSession(eq(instance), captor.capture());
        TurRealtimeVoiceSessionRequest sent = captor.getValue();
        assertThat(sent.model()).isEqualTo("gpt-realtime");
        assertThat(sent.instructions()).contains("Marina");
        assertThat(sent.locale()).isEqualTo("pt-BR");
    }

    @Test
    void capabilityConfigOverridesModelAndVoiceWhenRequestBlank() {
        when(agentRepository.findById("agent-1")).thenReturn(Optional.of(agent));
        when(providerFactory.getProvider(instance)).thenReturn(Optional.of(provider));
        lenient().when(provider.defaultModel()).thenReturn("gpt-realtime");
        stubVoiceEnabled("{\"model\":\"gpt-realtime-mini\",\"voice\":\"cedar\"}");
        when(provider.createSession(eq(instance), any()))
                .thenReturn(new TurRealtimeVoiceSession("openai", "ek_x", 1L,
                        "gpt-realtime-mini", "cedar", "wss://x"));

        service.createForAgent("agent-1", null, null, null, null);

        ArgumentCaptor<TurRealtimeVoiceSessionRequest> captor =
                ArgumentCaptor.forClass(TurRealtimeVoiceSessionRequest.class);
        org.mockito.Mockito.verify(provider).createSession(eq(instance), captor.capture());
        assertThat(captor.getValue().model()).isEqualTo("gpt-realtime-mini");
        assertThat(captor.getValue().voice()).isEqualTo("cedar");
    }

    @Test
    void throwsWhenAgentNotFound() {
        when(agentRepository.findById("missing")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.createForAgent("missing", null, null, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not found");
    }

    @Test
    void throwsWhenAgentDisabled() {
        agent.setEnabled(0);
        when(agentRepository.findById("agent-1")).thenReturn(Optional.of(agent));
        assertThatThrownBy(() -> service.createForAgent("agent-1", null, null, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("disabled");
    }

    @Test
    void throwsWhenVendorHasNoVoiceProvider() {
        when(agentRepository.findById("agent-1")).thenReturn(Optional.of(agent));
        when(providerFactory.getProvider(instance)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.createForAgent("agent-1", null, null, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not supported");
    }

    @Test
    void throwsWhenCapabilityDisabled() {
        when(agentRepository.findById("agent-1")).thenReturn(Optional.of(agent));
        when(providerFactory.getProvider(instance)).thenReturn(Optional.of(provider));
        when(capabilityService.enabledFor("inst-1", "openai")).thenReturn(List.of());
        assertThatThrownBy(() -> service.createForAgent("agent-1", null, null, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not enabled");
    }

    @Test
    void availabilityIsTrueWhenEnabled() {
        when(agentRepository.findById("agent-1")).thenReturn(Optional.of(agent));
        when(providerFactory.getProvider(instance)).thenReturn(Optional.of(provider));
        when(provider.defaultModel()).thenReturn("gpt-realtime");
        stubVoiceEnabled(null);

        VoiceAvailability availability = service.availability("agent-1", null);
        assertThat(availability.available()).isTrue();
        assertThat(availability.model()).isEqualTo("gpt-realtime");
    }

    @Test
    void availabilityIsFalseWhenCapabilityDisabled() {
        when(agentRepository.findById("agent-1")).thenReturn(Optional.of(agent));
        when(providerFactory.getProvider(instance)).thenReturn(Optional.of(provider));
        when(capabilityService.enabledFor("inst-1", "openai")).thenReturn(List.of());

        VoiceAvailability availability = service.availability("agent-1", null);
        assertThat(availability.available()).isFalse();
        assertThat(availability.reason()).isEqualTo("capability-disabled");
    }

    @Test
    void availabilityIsFalseWhenAgentMissing() {
        when(agentRepository.findById("nope")).thenReturn(Optional.empty());
        VoiceAvailability availability = service.availability("nope", null);
        assertThat(availability.available()).isFalse();
    }
}
