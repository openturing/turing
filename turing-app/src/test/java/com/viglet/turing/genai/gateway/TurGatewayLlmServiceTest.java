/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.gateway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.viglet.turing.genai.gateway.TurOpenAiWire.ModelList;
import com.viglet.turing.persistence.model.llm.TurLLMInstance;
import com.viglet.turing.persistence.repository.llm.TurLLMInstanceRepository;
import com.viglet.turing.resilience.llm.TurLlmModelFactory;
import com.viglet.turing.service.llm.tokenusage.TurLLMTokenUsageService;
import com.viglet.turing.system.TurGlobalSettingsService;
import com.viglet.turing.system.security.TurSecretCryptoService;

import tools.jackson.databind.ObjectMapper;

/**
 * T740 / §XLIX — unit coverage for the gateway model resolution + listing (the
 * pure translation seam that does not require a live LLM). Full chat/embedding
 * round-trips are exercised by the gateway integration test.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@ExtendWith(MockitoExtension.class)
class TurGatewayLlmServiceTest {

    @Mock
    private TurLLMInstanceRepository llmInstanceRepository;
    @Mock
    private TurLlmModelFactory llmModelFactory;
    @Mock
    private TurSecretCryptoService secretCryptoService;
    @Mock
    private TurLLMTokenUsageService tokenUsageService;
    @Mock
    private TurGlobalSettingsService globalSettingsService;
    @Mock
    private TurGatewayKeyService gatewayKeyService;
    @Mock
    private TurGatewayRateLimiter gatewayRateLimiter;
    @Mock
    private com.viglet.turing.service.llm.budget.TurChatCostBudgetGate budgetGate;
    @Mock
    private TurGatewayResponseCache responseCache;
    @Mock
    private com.viglet.turing.genai.safety.TurModerationService moderationService;
    @Mock
    private TurGatewayModelRouter modelRouter;
    @Mock
    private TurGatewayNativeToolsService nativeToolsService;
    @Mock
    private TurGatewayRouterService routerService;
    @Mock
    private TurGatewayTrafficCaptureService trafficCapture;

    private TurGatewayLlmService service() {
        return new TurGatewayLlmService(llmInstanceRepository, llmModelFactory, secretCryptoService,
                tokenUsageService, globalSettingsService, gatewayKeyService, gatewayRateLimiter,
                budgetGate, responseCache, moderationService, modelRouter, nativeToolsService,
                routerService, trafficCapture, new ObjectMapper());
    }

    private TurLLMInstance instance(String id, String modelName, int enabled) {
        TurLLMInstance instance = new TurLLMInstance();
        instance.setId(id);
        instance.setModelName(modelName);
        instance.setEnabled(enabled);
        return instance;
    }

    @Test
    void listModelsAdvertisesOnlyEnabledInstancesById() {
        when(llmInstanceRepository.findAll()).thenReturn(List.of(
                instance("i-1", "gpt-4o", 1),
                instance("i-2", "claude-3", 0),
                instance("i-3", "llama-3", 1)));

        ModelList list = service().listModels();

        assertThat(list.object()).isEqualTo("list");
        assertThat(list.data()).extracting(TurOpenAiWire.ModelObject::id)
                .containsExactly("i-1", "i-3");
        assertThat(list.data()).allSatisfy(m -> assertThat(m.object()).isEqualTo("model"));
    }

    @Test
    void resolvesByInstanceIdWhenEnabled() {
        when(llmInstanceRepository.findById("i-1")).thenReturn(Optional.of(instance("i-1", "gpt-4o", 1)));

        assertThat(service().resolveChatInstance("i-1").getId()).isEqualTo("i-1");
    }

    @Test
    void resolvesByModelNameWhenIdMisses() {
        when(llmInstanceRepository.findById("gpt-4o")).thenReturn(Optional.empty());
        when(llmInstanceRepository.findAll()).thenReturn(List.of(
                instance("i-1", "gpt-4o", 1),
                instance("i-2", "claude-3", 1)));

        assertThat(service().resolveChatInstance("gpt-4o").getId()).isEqualTo("i-1");
    }

    @Test
    void fallsBackToDefaultWhenModelUnmatched() {
        when(llmInstanceRepository.findById("mystery")).thenReturn(Optional.empty());
        when(llmInstanceRepository.findAll()).thenReturn(List.of(instance("i-1", "gpt-4o", 1)));
        when(globalSettingsService.getDefaultLlmId()).thenReturn("i-default");
        when(llmInstanceRepository.findById("i-default"))
                .thenReturn(Optional.of(instance("i-default", "gpt-4o-mini", 1)));

        assertThat(service().resolveChatInstance("mystery").getId()).isEqualTo("i-default");
    }

    @Test
    void throwsWhenNoMatchAndNoDefault() {
        when(llmInstanceRepository.findById("mystery")).thenReturn(Optional.empty());
        when(llmInstanceRepository.findAll()).thenReturn(List.of());
        when(globalSettingsService.getDefaultLlmId()).thenReturn("");

        assertThatThrownBy(() -> service().resolveChatInstance("mystery"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown model");
    }

    @Test
    void guardrailsBlockFlaggedInput() {
        when(moderationService.moderateAlways(org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(new com.viglet.turing.genai.safety.TurModerationService.Verdict(
                        true, List.of("violence")));
        var request = new TurOpenAiWire.ChatCompletionRequest("gpt-4o",
                List.of(new TurOpenAiWire.WireMessage("user", "bad input")),
                false, null, null, null, null);

        assertThatThrownBy(() -> service().complete(request, "u",
                new TurGatewayOptions(false, true, null, java.util.Set.of())))
                .isInstanceOf(TurGatewayGuardrailException.class);
    }

    @Test
    void disabledInstanceIdIsNotResolvedDirectly() {
        // A disabled id must fall through to modelName/default, not be returned.
        when(llmInstanceRepository.findById("i-off")).thenReturn(Optional.of(instance("i-off", "gpt-4o", 0)));
        lenient().when(llmInstanceRepository.findAll()).thenReturn(List.of(instance("i-off", "gpt-4o", 0)));
        when(globalSettingsService.getDefaultLlmId()).thenReturn("");

        assertThatThrownBy(() -> service().resolveChatInstance("i-off"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
