/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.eval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;

import com.viglet.turing.genai.flow.TurChatFlowEngineService;
import com.viglet.turing.persistence.dto.agent.TurEvalDatasetRowDto;
import com.viglet.turing.persistence.model.agent.TurAIAgent;
import com.viglet.turing.persistence.model.agent.TurAgentEvalExpectedOutcome;
import com.viglet.turing.persistence.model.agent.TurEvalDataset;
import com.viglet.turing.persistence.model.agent.TurEvalDatasetRow;
import com.viglet.turing.persistence.model.llm.TurLLMInstance;
import com.viglet.turing.persistence.repository.agent.TurAIAgentRepository;
import com.viglet.turing.persistence.repository.agent.TurAIAgentSlotRepository;
import com.viglet.turing.persistence.repository.agent.TurChatFlowRepository;
import com.viglet.turing.persistence.repository.agent.TurEvalDatasetRepository;
import com.viglet.turing.persistence.repository.agent.TurEvalDatasetRowRepository;
import com.viglet.turing.persistence.repository.llm.TurLLMInstanceRepository;
import com.viglet.turing.resilience.llm.TurLlmModelFactory;
import com.viglet.turing.system.TurGlobalSettingsService;
import com.viglet.turing.system.security.TurSecretCryptoService;

/**
 * T597 / §XXXIII.12 — LLM-assisted eval-dataset generation &amp; augmentation:
 * grounds candidate rows on an agent's context, paraphrases existing rows, never
 * auto-persists (draft-only), fails open with no LLM, and saves reviewed rows
 * through the shared canonical-row persistence.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@ExtendWith(MockitoExtension.class)
class TurEvalDatasetGenerationServiceTest {

    @Mock
    private TurAIAgentRepository agentRepository;
    @Mock
    private TurAIAgentSlotRepository slotRepository;
    @Mock
    private TurChatFlowRepository chatFlowRepository;
    @Mock
    private TurChatFlowEngineService chatFlowEngineService;
    @Mock
    private TurEvalDatasetRepository datasetRepository;
    @Mock
    private TurEvalDatasetRowRepository datasetRowRepository;
    @Mock
    private TurGlobalSettingsService globalSettingsService;
    @Mock
    private TurLLMInstanceRepository llmInstanceRepository;
    @Mock
    private TurLlmModelFactory llmModelFactory;
    @Mock
    private TurSecretCryptoService secretCryptoService;

    private TurEvalDatasetGenerationService service(ChatModel chatModel) {
        if (chatModel != null) {
            lenient().when(secretCryptoService.decrypt(any())).thenReturn("key");
            lenient().when(llmModelFactory.createChatModel(any(), any())).thenReturn(chatModel);
        }
        TurEvalDatasetImportService importService =
                new TurEvalDatasetImportService(datasetRepository, datasetRowRepository);
        return new TurEvalDatasetGenerationService(agentRepository, slotRepository,
                chatFlowRepository, chatFlowEngineService, importService, datasetRowRepository,
                globalSettingsService, llmInstanceRepository, llmModelFactory, secretCryptoService);
    }

    private static ChatModel modelReturning(String text) {
        ChatModel model = mock(ChatModel.class);
        ChatResponse response = new ChatResponse(List.of(new Generation(new AssistantMessage(text))));
        when(model.call(any(Prompt.class))).thenReturn(response);
        return model;
    }

    private TurAIAgent agentWithEnabledLlm() {
        TurLLMInstance llm = new TurLLMInstance();
        llm.setEnabled(1);
        llm.setApiKeyEncrypted("enc");
        TurAIAgent agent = new TurAIAgent();
        agent.setId("a1");
        agent.setTitle("Concierge");
        agent.setSystemPrompt("You help students enroll.");
        agent.setLlmInstances(Set.of(llm));
        lenient().when(agentRepository.findById("a1")).thenReturn(java.util.Optional.of(agent));
        lenient().when(slotRepository.findByTurAIAgent_IdOrderByNameAsc("a1")).thenReturn(List.of());
        lenient().when(chatFlowRepository.findByTurAIAgent_IdOrderByNameAsc("a1")).thenReturn(List.of());
        return agent;
    }

    @Test
    void generateParsesGroundedDraftRows() {
        agentWithEnabledLlm();
        ChatModel model = modelReturning("""
                [
                  {"name":"happy path","turns":["I want to enroll","My name is Ana"],
                   "expectedOutcome":"CAPTURED","expectedNodeId":"n1",
                   "expectedSlots":{"name":"Ana"},"rubric":"greets the user"},
                  {"name":"off topic","turns":["what's the weather?"],"expectedOutcome":"ABANDONED"}
                ]
                """);

        List<TurEvalDatasetRowDto> drafts = service(model).generateFromAgent("a1", 10);

        assertThat(drafts).hasSize(2);
        TurEvalDatasetRowDto first = drafts.get(0);
        assertThat(first.id()).isNull(); // draft — not persisted
        assertThat(first.tags()).isEqualTo("generated");
        assertThat(first.seedTurnsJson()).contains("I want to enroll").contains("My name is Ana");
        assertThat(first.expectedOutcome()).isEqualTo("CAPTURED");
        assertThat(first.expectedNodeId()).isEqualTo("n1");
        assertThat(first.expectedSlotsJson()).contains("Ana");
        assertThat(first.metadataJson()).contains("llm-generated").contains("a1");
        assertThat(drafts.get(1).expectedOutcome()).isEqualTo("ABANDONED");
    }

    @Test
    void generateDropsRowsWithNoTurnsAndClampsCount() {
        agentWithEnabledLlm();
        ChatModel model = modelReturning("""
                [
                  {"name":"empty","turns":[],"expectedOutcome":"ANY"},
                  {"name":"good","turns":["hi there"],"expectedOutcome":"ANY"}
                ]
                """);

        List<TurEvalDatasetRowDto> drafts = service(model).generateFromAgent("a1", 1);

        // The empty-turns row is dropped; the count clamp keeps at most 1.
        assertThat(drafts).hasSize(1);
        assertThat(drafts.get(0).name()).isEqualTo("good");
    }

    @Test
    void generateUnknownOutcomeFallsBackToAny() {
        agentWithEnabledLlm();
        ChatModel model = modelReturning(
                "[{\"turns\":[\"hi\"],\"expectedOutcome\":\"NONSENSE\"}]");

        List<TurEvalDatasetRowDto> drafts = service(model).generateFromAgent("a1", 5);

        assertThat(drafts).hasSize(1);
        assertThat(drafts.get(0).expectedOutcome()).isEqualTo(TurAgentEvalExpectedOutcome.ANY.name());
    }

    @Test
    void generateFailsOpenWithNoLlm() {
        TurAIAgent agent = new TurAIAgent();
        agent.setId("a1");
        agent.setLlmInstances(Set.of()); // no attached LLM
        when(agentRepository.findById("a1")).thenReturn(java.util.Optional.of(agent));
        when(globalSettingsService.getDefaultLlmId()).thenReturn(null); // no default either

        assertThat(service(null).generateFromAgent("a1", 5)).isEmpty();
    }

    @Test
    void generateFailsOpenWhenLlmGarblesReply() {
        agentWithEnabledLlm();
        ChatModel model = modelReturning("I'm sorry, I can't do that.");

        assertThat(service(model).generateFromAgent("a1", 5)).isEmpty();
    }

    @Test
    void augmentParaphrasesRowsCarryingExpectations() {
        TurEvalDatasetRow source = new TurEvalDatasetRow();
        source.setId("r1");
        source.setName("enroll");
        source.setSeedTurnsJson("[\"I want to enroll\"]");
        source.setExpectedOutcome(TurAgentEvalExpectedOutcome.CAPTURED);
        source.setExpectedNodeId("n1");
        source.setReferenceAnswer("Welcome!");
        when(datasetRowRepository.findByTurEvalDataset_IdOrderBySortOrderAsc("d1"))
                .thenReturn(List.of(source));
        when(globalSettingsService.getDefaultLlmId()).thenReturn("llm1");
        TurLLMInstance llm = new TurLLMInstance();
        llm.setEnabled(1);
        llm.setApiKeyEncrypted("enc");
        when(llmInstanceRepository.findById("llm1")).thenReturn(java.util.Optional.of(llm));
        ChatModel model = modelReturning("""
                [
                  {"turns":["I'd like to sign up"]},
                  {"turns":["Can I register please?"]}
                ]
                """);

        List<TurEvalDatasetRowDto> drafts = service(model).augmentDataset("d1", 2);

        assertThat(drafts).hasSize(2);
        TurEvalDatasetRowDto variant = drafts.get(0);
        assertThat(variant.id()).isNull();
        assertThat(variant.tags()).isEqualTo("augmented");
        assertThat(variant.seedTurnsJson()).contains("sign up");
        assertThat(variant.expectedOutcome()).isEqualTo("CAPTURED"); // carried over
        assertThat(variant.expectedNodeId()).isEqualTo("n1");
        assertThat(variant.referenceAnswer()).isEqualTo("Welcome!");
        assertThat(variant.metadataJson()).contains("llm-augmented").contains("r1");
    }

    @Test
    void saveReviewedCreatesNewDatasetWhenNoDatasetId() {
        when(datasetRepository.save(any())).thenAnswer(inv -> {
            TurEvalDataset d = inv.getArgument(0);
            d.setId("new-ds");
            return d;
        });
        when(datasetRepository.findById("new-ds")).thenAnswer(inv -> {
            TurEvalDataset d = new TurEvalDataset();
            d.setId("new-ds");
            d.setName("my-set");
            return java.util.Optional.of(d);
        });
        when(datasetRowRepository.findByTurEvalDataset_IdOrderBySortOrderAsc("new-ds"))
                .thenReturn(List.of());

        TurEvalDatasetRowDto row = new TurEvalDatasetRowDto(null, "case", "[\"hi\"]", null,
                "CAPTURED", "n1", "rubric", "ref", "generated", "{\"source\":\"llm-generated\"}", 0);

        var saved = service(null).saveReviewed("my-set", null, List.of(row));

        assertThat(saved.id()).isEqualTo("new-ds");
    }
}
