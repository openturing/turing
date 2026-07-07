/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.viglet.turing.genai.nativeapi.openai.TurOpenAiPredictedOutputsService;
import com.viglet.turing.genai.nativeapi.openai.TurPredictedOutputsResolver;
import com.viglet.turing.persistence.model.agent.TurAIAgent;
import com.viglet.turing.persistence.model.llm.TurLLMInstance;
import com.viglet.turing.persistence.repository.agent.TurAIAgentRepository;
import com.viglet.turing.resilience.llm.TurLlmModelFactory;
import com.viglet.turing.system.security.TurSecretCryptoService;

/**
 * F.10 / §X.11.b — verifies the OpenAI prediction fast-path, the provider-
 * agnostic fallback, and input validation of {@link TurChatRephraseService}.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
class TurChatRephraseServiceTest {

    private TurAIAgentRepository agentRepository;
    private TurPredictedOutputsResolver predictedOutputsResolver;
    private TurOpenAiPredictedOutputsService predictedOutputsService;
    private TurLlmModelFactory llmModelFactory;
    private TurSecretCryptoService secretCryptoService;
    private TurChatRephraseService service;

    @BeforeEach
    void setUp() {
        agentRepository = mock(TurAIAgentRepository.class);
        predictedOutputsResolver = mock(TurPredictedOutputsResolver.class);
        predictedOutputsService = mock(TurOpenAiPredictedOutputsService.class);
        llmModelFactory = mock(TurLlmModelFactory.class);
        secretCryptoService = mock(TurSecretCryptoService.class);
        service = new TurChatRephraseService(agentRepository, predictedOutputsResolver,
                predictedOutputsService, llmModelFactory, secretCryptoService);
    }

    private TurAIAgent agentWithInstance(String instanceId) {
        TurLLMInstance instance = new TurLLMInstance();
        instance.setId(instanceId);
        instance.setModelName("gpt-4o");
        TurAIAgent agent = new TurAIAgent();
        agent.setId("agent1");
        agent.getLlmInstances().add(instance);
        when(agentRepository.findById("agent1")).thenReturn(Optional.of(agent));
        return agent;
    }

    @Test
    void usesPredictionFastPath_whenOpenAiAndOptedIn() {
        agentWithInstance("llm1");
        when(predictedOutputsResolver.enabledFor(any(), any())).thenReturn(true);
        when(predictedOutputsService.edit(any(), any(), any(), eq("The long original answer.")))
                .thenReturn(Optional.of("Short answer."));

        TurChatRephraseService.RephraseResult result = service.rephrase("agent1", null,
                "What is X?", "The long original answer.", "shorter");

        assertThat(result.success()).isTrue();
        assertThat(result.usedPrediction()).isTrue();
        assertThat(result.rephrased()).isEqualTo("Short answer.");
    }

    @Test
    void runsOpenAiWithoutPrediction_whenNotOptedIn() {
        agentWithInstance("llm1");
        when(predictedOutputsResolver.enabledFor(any(), any())).thenReturn(false);
        // not opted in -> predictedOutput passed as null
        when(predictedOutputsService.edit(any(), any(), any(), isNull()))
                .thenReturn(Optional.of("Shorter."));

        TurChatRephraseService.RephraseResult result = service.rephrase("agent1", null,
                null, "Original.", "shorter");

        assertThat(result.success()).isTrue();
        assertThat(result.usedPrediction()).isFalse();
        assertThat(result.rephrased()).isEqualTo("Shorter.");
    }

    @Test
    void fallsBackToChatModel_whenNotOpenAi() {
        agentWithInstance("llm1");
        when(predictedOutputsResolver.enabledFor(any(), any())).thenReturn(false);
        // non-OpenAI instance -> edit() returns empty -> generic fallback
        when(predictedOutputsService.edit(any(), any(), any(), any())).thenReturn(Optional.empty());
        when(secretCryptoService.decrypt(any())).thenReturn("key");
        var chatModel = mock(org.springframework.ai.chat.model.ChatModel.class, RETURNS_DEEP_STUBS);
        when(chatModel.call(any(org.springframework.ai.chat.prompt.Prompt.class))
                .getResult().getOutput().getText()).thenReturn("  Generic shorter.  ");
        when(llmModelFactory.createChatModel(any(), any())).thenReturn(chatModel);

        TurChatRephraseService.RephraseResult result = service.rephrase("agent1", null,
                "q", "Original answer.", "simpler");

        assertThat(result.success()).isTrue();
        assertThat(result.usedPrediction()).isFalse();
        assertThat(result.rephrased()).isEqualTo("Generic shorter.");
    }

    @Test
    void failsOnBlankAnswer() {
        TurChatRephraseService.RephraseResult result = service.rephrase("agent1", null, "q", "  ", "shorter");
        assertThat(result.success()).isFalse();
        assertThat(result.error()).contains("Nothing to rephrase");
    }

    @Test
    void failsWhenAgentMissing() {
        when(agentRepository.findById("ghost")).thenReturn(Optional.empty());
        TurChatRephraseService.RephraseResult result = service.rephrase("ghost", null, "q", "answer", "shorter");
        assertThat(result.success()).isFalse();
        assertThat(result.error()).contains("not found");
    }

    @Test
    void buildInstruction_embedsAnswerAndDirective() {
        String out = service.buildInstruction("shorter", "Why?", "Because reasons.");
        assertThat(out).contains("Because reasons.").contains("shorter").contains("The user originally asked");
        // unknown style passes through
        assertThat(service.buildInstruction("snappier", null, "x")).contains("snappier");
    }
}
