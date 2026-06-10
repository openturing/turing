/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.service.chatmemory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.test.util.ReflectionTestUtils;

import com.viglet.turing.genai.TurAgentChatExecutor.ChatMessageItem;
import com.viglet.turing.genai.workspace.TurAgentWorkspace;
import com.viglet.turing.persistence.model.agent.TurAIAgent;
import com.viglet.turing.persistence.model.llm.TurLLMInstance;
import com.viglet.turing.persistence.repository.llm.TurLLMInstanceRepository;
import com.viglet.turing.resilience.llm.TurLlmModelFactory;
import com.viglet.turing.service.llm.tokenusage.TurLLMTokenUsageService;
import com.viglet.turing.system.TurGlobalSettingsService;
import com.viglet.turing.system.security.TurSecretCryptoService;

/**
 * Unit tests for {@link TurChatMemoryCompressionService} (T115) — the
 * workspace-backed memory-compression service that summarizes the older pool
 * of a conversation and returns a compact block to prepend to the prompt.
 */
class TurChatMemoryCompressionServiceTest {

    private static final String CONV = "conv-115";
    private static final String LLM_ID = "llm-cheap";
    private static final String SUMMARY = "The user wants X; the assistant agreed to Y.";

    private TurChatMemoryStore store;
    private TurAgentWorkspace workspace;
    private TurLLMInstanceRepository llmInstanceRepository;
    private TurLlmModelFactory llmModelFactory;
    private TurSecretCryptoService secretCryptoService;
    private TurLLMTokenUsageService tokenUsageService;
    private TurGlobalSettingsService globalSettingsService;
    private ChatModel chatModel;

    private TurChatMemoryCompressionService service;

    @BeforeEach
    void setUp() {
        store = mock(TurChatMemoryStore.class);
        workspace = mock(TurAgentWorkspace.class);
        llmInstanceRepository = mock(TurLLMInstanceRepository.class);
        llmModelFactory = mock(TurLlmModelFactory.class);
        secretCryptoService = mock(TurSecretCryptoService.class);
        tokenUsageService = mock(TurLLMTokenUsageService.class);
        globalSettingsService = mock(TurGlobalSettingsService.class);
        chatModel = mock(ChatModel.class);

        service = new TurChatMemoryCompressionService(store, workspace, llmInstanceRepository,
                llmModelFactory, secretCryptoService, tokenUsageService, globalSettingsService);
        // @PostConstruct doesn't run outside Spring — set the prompt directly.
        ReflectionTestUtils.setField(service, "compressionSystemPrompt", "Summarize the older turns.");

        TurLLMInstance instance = new TurLLMInstance();
        instance.setId(LLM_ID);
        instance.setEnabled(1);
        instance.setApiKeyEncrypted("enc");
        lenient().when(llmInstanceRepository.findById(LLM_ID)).thenReturn(Optional.of(instance));
        lenient().when(secretCryptoService.decrypt("enc")).thenReturn("plain-key");
        lenient().when(llmModelFactory.createChatModel(any(), any())).thenReturn(chatModel);
        lenient().when(chatModel.call(any(Prompt.class)))
                .thenReturn(new ChatResponse(List.of(new Generation(new AssistantMessage(SUMMARY)))));
        lenient().when(store.isEnabled()).thenReturn(true);
    }

    private static TurAIAgent agent() {
        TurAIAgent a = new TurAIAgent();
        a.setId("agent-1");
        a.setChatMemoryEnabled(true);
        a.setChatMemoryCompressionEnabled(true);
        a.setChatMemoryRecentN(2);
        a.setChatMemoryMaxMessages(100);
        a.setChatMemoryCompressionThresholdTokens(10);
        a.setChatMemoryCompressionInterval("PT1H");
        a.setChatMemoryCompressionLlmId(LLM_ID);
        return a;
    }

    /** 8 persisted turns; recentN=2 → 6 older turns, each comfortably long. */
    private static List<Map<String, Object>> history(int count) {
        List<Map<String, Object>> list = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            list.add(Map.of(
                    "role", i % 2 == 0 ? "user" : "assistant",
                    "content", "This is older turn number " + i + " carrying some real content to summarize.",
                    "timestamp", "2026-06-10T10:0" + (i % 10) + ":00Z"));
        }
        return list;
    }

    @Test
    void summarizesOlderPoolAndReturnsBlock() {
        when(store.findMessages(eq(CONV), anyInt())).thenReturn(history(8));

        Optional<ChatMessageItem> block = service.summaryBlock(agent(), CONV);

        assertThat(block).isPresent();
        assertThat(block.get().role()).isEqualTo("user");
        assertThat(block.get().content())
                .contains("[Summary of earlier conversation (turns 1–6)]")
                .contains(SUMMARY);

        verify(chatModel).call(any(Prompt.class));
        // The summary was persisted to the conversation workspace under memory/.
        verify(workspace).put(eq("agent-1"), eq(CONV), eq("memory/summary-1-6.md"),
                any(byte[].class), eq("text/markdown"));
    }

    @Test
    void bypassesWhenCompressionDisabled() {
        TurAIAgent a = agent();
        a.setChatMemoryCompressionEnabled(false);

        assertThat(service.summaryBlock(a, CONV)).isEmpty();
        verifyNoInteractions(workspace);
        verify(chatModel, never()).call(any(Prompt.class));
    }

    @Test
    void bypassesWhenOlderPoolTooSmall() {
        // 5 turns, recentN=2 → 3 older < MIN_OLDER_TURNS (4)
        when(store.findMessages(eq(CONV), anyInt())).thenReturn(history(5));

        assertThat(service.summaryBlock(agent(), CONV)).isEmpty();
        verify(chatModel, never()).call(any(Prompt.class));
    }

    @Test
    void bypassesWhenUnderTokenThreshold() {
        TurAIAgent a = agent();
        a.setChatMemoryCompressionThresholdTokens(1_000_000);
        when(store.findMessages(eq(CONV), anyInt())).thenReturn(history(8));

        assertThat(service.summaryBlock(a, CONV)).isEmpty();
        verify(chatModel, never()).call(any(Prompt.class));
    }

    @Test
    void bypassesWhenStoreDisabled() {
        when(store.isEnabled()).thenReturn(false);

        assertThat(service.summaryBlock(agent(), CONV)).isEmpty();
        verify(chatModel, never()).call(any(Prompt.class));
    }

    @Test
    void reusesCachedSummaryWithinInterval() {
        when(store.findMessages(eq(CONV), anyInt())).thenReturn(history(8));
        TurAIAgent a = agent();

        Optional<ChatMessageItem> first = service.summaryBlock(a, CONV);
        Optional<ChatMessageItem> second = service.summaryBlock(a, CONV);

        assertThat(first).isPresent();
        assertThat(second).isPresent();
        assertThat(second.get().content()).contains(SUMMARY);
        // Interval guard: the LLM is called only once across the two turns.
        verify(chatModel, times(1)).call(any(Prompt.class));
    }

    @Test
    void fallsBackToGlobalDefaultLlmWhenCompressionLlmIdBlank() {
        TurAIAgent a = agent();
        a.setChatMemoryCompressionLlmId(null);
        when(globalSettingsService.getDefaultLlmId()).thenReturn(LLM_ID);
        when(store.findMessages(eq(CONV), anyInt())).thenReturn(history(8));

        assertThat(service.summaryBlock(a, CONV)).isPresent();
        verify(chatModel).call(any(Prompt.class));
    }

    @Test
    void bypassesWhenNoLlmResolves() {
        TurAIAgent a = agent();
        a.setChatMemoryCompressionLlmId(null);
        when(globalSettingsService.getDefaultLlmId()).thenReturn(null);
        when(store.findMessages(eq(CONV), anyInt())).thenReturn(history(8));

        assertThat(service.summaryBlock(a, CONV)).isEmpty();
        verify(chatModel, never()).call(any(Prompt.class));
    }
}
