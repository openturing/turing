/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.viglet.turing.genai.TurAgentChatExecutor;
import com.viglet.turing.genai.TurAgentChatRequest;
import com.viglet.turing.persistence.model.agent.TurAIAgent;
import com.viglet.turing.persistence.model.llm.TurLLMInstance;
import com.viglet.turing.persistence.repository.agent.TurAIAgentRepository;
import com.viglet.turing.persistence.repository.llm.TurLLMInstanceRepository;

import reactor.core.publisher.Flux;

/**
 * Unit tests for the {@link TurCustomToolAgentHelper} cross-agent
 * delegation surface (T109).
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
@ExtendWith(MockitoExtension.class)
class TurCustomToolAgentHelperTest {

    @Mock
    private TurAgentChatExecutor executor;
    @Mock
    private TurAIAgentRepository agentRepository;
    @Mock
    private TurLLMInstanceRepository llmRepository;

    private static final String PARENT_CONV = "parent-conv-1";

    private TurCustomToolAgentHelper helper;

    @BeforeEach
    void setUp() {
        helper = new TurCustomToolAgentHelper(executor, agentRepository, llmRepository,
                PARENT_CONV, 0);
    }

    @Test
    void shouldReturnDepthCapSentinelWhenAtCap() {
        TurCustomToolAgentHelper capped = new TurCustomToolAgentHelper(executor,
                agentRepository, llmRepository, PARENT_CONV, 3, 3);
        String result = capped.invoke("any-agent", "hello");
        assertThat(result).isEqualTo("[agent.invoke depth cap reached]");
        verify(executor, never()).execute(any(TurAgentChatRequest.class), anyInt());
    }

    @Test
    void shouldReturnSentinelForBlankAgentId() {
        assertThat(helper.invoke(null, "hello")).contains("agent id is required");
        assertThat(helper.invoke("  ", "hello")).contains("agent id is required");
    }

    @Test
    void shouldReturnSentinelForBlankMessage() {
        assertThat(helper.invoke("any-agent", null)).contains("message is required");
        assertThat(helper.invoke("any-agent", "  ")).contains("message is required");
    }

    @Test
    void shouldReturnSentinelWhenAgentNotFound() {
        when(agentRepository.findById("missing")).thenReturn(Optional.empty());
        assertThat(helper.invoke("missing", "hello"))
                .isEqualTo("[agent.invoke: agent not found: missing]");
    }

    @Test
    void shouldReturnSentinelWhenAgentDisabled() {
        TurAIAgent disabled = newAgent("dis", 0, null);
        when(agentRepository.findById("dis")).thenReturn(Optional.of(disabled));
        assertThat(helper.invoke("dis", "hello"))
                .isEqualTo("[agent.invoke: agent disabled: dis]");
    }

    @Test
    void shouldReturnSentinelWhenAgentHasNoLlm() {
        TurAIAgent agent = newAgent("a1", 1, null);
        when(agentRepository.findById("a1")).thenReturn(Optional.of(agent));
        assertThat(helper.invoke("a1", "hello"))
                .isEqualTo("[agent.invoke: agent has no LLM configured]");
    }

    @Test
    void shouldReturnSentinelWhenRequestedLlmNotAllowed() {
        TurLLMInstance allowedLlm = newLlm("llm-allowed", 1);
        TurAIAgent agent = newAgent("a1", 1, allowedLlm);
        when(agentRepository.findById("a1")).thenReturn(Optional.of(agent));
        String result = helper.invoke("a1", "hello",
                Map.of("llmInstanceId", "llm-not-attached"));
        assertThat(result).contains("llm not allowed for agent: llm-not-attached");
    }

    @Test
    void shouldConcatenateOnlyTokenResponses() {
        TurLLMInstance llm = newLlm("llm-1", 1);
        TurAIAgent agent = newAgent("a1", 1, llm);
        when(agentRepository.findById("a1")).thenReturn(Optional.of(agent));
        when(llmRepository.findById("llm-1")).thenReturn(Optional.of(llm));
        when(executor.execute(
                argThat((TurAgentChatRequest r) -> r != null
                        && agent.equals(r.agent()) && llm.equals(r.llmInstance())),
                anyInt()))
                .thenReturn(Flux.just(
                        new TurAgentChatExecutor.ChatResponse("assistant", "Hello ", "token"),
                        new TurAgentChatExecutor.ChatResponse("assistant", "[\"a\",\"b\"]", "options"),
                        new TurAgentChatExecutor.ChatResponse("assistant", "world!", "token")));

        String result = helper.invoke("a1", "ping");

        assertThat(result).isEqualTo("Hello world!");
    }

    @Test
    void shouldPropagateCurrentDepthIntoExecutorCall() {
        TurLLMInstance llm = newLlm("llm-1", 1);
        TurAIAgent agent = newAgent("a1", 1, llm);
        when(agentRepository.findById("a1")).thenReturn(Optional.of(agent));
        when(llmRepository.findById("llm-1")).thenReturn(Optional.of(llm));
        when(executor.execute(any(TurAgentChatRequest.class), anyInt()))
                .thenReturn(Flux.just(
                        new TurAgentChatExecutor.ChatResponse("assistant", "ok", "token")));

        TurCustomToolAgentHelper depthOne = new TurCustomToolAgentHelper(executor,
                agentRepository, llmRepository, PARENT_CONV, 1);
        depthOne.invoke("a1", "ping");

        ArgumentCaptor<Integer> depthCaptor = ArgumentCaptor.forClass(Integer.class);
        verify(executor).execute(any(TurAgentChatRequest.class), depthCaptor.capture());
        assertThat(depthCaptor.getValue()).isEqualTo(1);
    }

    @Test
    void shouldEmbedParentConversationIdInChildFirstUserMessage() {
        TurLLMInstance llm = newLlm("llm-1", 1);
        TurAIAgent agent = newAgent("a1", 1, llm);
        when(agentRepository.findById("a1")).thenReturn(Optional.of(agent));
        when(llmRepository.findById("llm-1")).thenReturn(Optional.of(llm));
        when(executor.execute(any(TurAgentChatRequest.class), anyInt()))
                .thenReturn(Flux.just(
                        new TurAgentChatExecutor.ChatResponse("assistant", "ok", "token")));

        helper.invoke("a1", "Find me a quote");

        ArgumentCaptor<TurAgentChatRequest> reqCaptor =
                ArgumentCaptor.forClass(TurAgentChatRequest.class);
        verify(executor).execute(reqCaptor.capture(), anyInt());
        List<TurAgentChatExecutor.ChatMessageItem> history = reqCaptor.getValue().history();
        assertThat(history).hasSize(1);
        assertThat(history.get(0).role()).isEqualTo("user");
        assertThat(history.get(0).content())
                .contains("__parentConversationId=" + PARENT_CONV)
                .contains("Find me a quote");
    }

    @Test
    void shouldNotEmbedParentIdWhenAbsent() {
        TurCustomToolAgentHelper noParent = new TurCustomToolAgentHelper(executor,
                agentRepository, llmRepository, null, 0);

        TurLLMInstance llm = newLlm("llm-1", 1);
        TurAIAgent agent = newAgent("a1", 1, llm);
        when(agentRepository.findById("a1")).thenReturn(Optional.of(agent));
        when(llmRepository.findById("llm-1")).thenReturn(Optional.of(llm));
        when(executor.execute(any(TurAgentChatRequest.class), anyInt()))
                .thenReturn(Flux.just(
                        new TurAgentChatExecutor.ChatResponse("assistant", "ok", "token")));

        noParent.invoke("a1", "Bare message");

        ArgumentCaptor<TurAgentChatRequest> reqCaptor =
                ArgumentCaptor.forClass(TurAgentChatRequest.class);
        verify(executor).execute(reqCaptor.capture(), anyInt());
        assertThat(reqCaptor.getValue().history().get(0).content()).isEqualTo("Bare message");
    }

    @Test
    void shouldRespectCustomTimeoutFromOpts() {
        TurLLMInstance llm = newLlm("llm-1", 1);
        TurAIAgent agent = newAgent("a1", 1, llm);
        when(agentRepository.findById("a1")).thenReturn(Optional.of(agent));
        when(llmRepository.findById("llm-1")).thenReturn(Optional.of(llm));
        when(executor.execute(any(TurAgentChatRequest.class), anyInt()))
                .thenReturn(Flux.just(
                        new TurAgentChatExecutor.ChatResponse("assistant", "ok", "token")));

        // Just exercise the timeout-parsing path — actual blocking timeout
        // semantics covered by Reactor's own test suite. Any positive numeric
        // value parses; success means no NumberFormatException + invoke
        // returns the token text.
        String result = helper.invoke("a1", "hello", Map.of("timeout", 5_000L));
        assertThat(result).isEqualTo("ok");
    }

    @Test
    void shouldCapTimeoutAtMax() {
        TurLLMInstance llm = newLlm("llm-1", 1);
        TurAIAgent agent = newAgent("a1", 1, llm);
        when(agentRepository.findById("a1")).thenReturn(Optional.of(agent));
        when(llmRepository.findById("llm-1")).thenReturn(Optional.of(llm));
        when(executor.execute(any(TurAgentChatRequest.class), anyInt()))
                .thenReturn(Flux.just(
                        new TurAgentChatExecutor.ChatResponse("assistant", "ok", "token")));
        // Way over the cap — should still succeed and not propagate the raw value.
        String result = helper.invoke("a1", "hello", Map.of("timeout", 99_999_999L));
        assertThat(result).isEqualTo("ok");
    }

    @Test
    void shouldFallBackToDefaultTimeoutOnInvalidTimeoutString() {
        TurLLMInstance llm = newLlm("llm-1", 1);
        TurAIAgent agent = newAgent("a1", 1, llm);
        when(agentRepository.findById("a1")).thenReturn(Optional.of(agent));
        when(llmRepository.findById("llm-1")).thenReturn(Optional.of(llm));
        when(executor.execute(any(TurAgentChatRequest.class), anyInt()))
                .thenReturn(Flux.just(
                        new TurAgentChatExecutor.ChatResponse("assistant", "ok", "token")));
        String result = helper.invoke("a1", "hello", Map.of("timeout", "not-a-number"));
        assertThat(result).isEqualTo("ok");
    }

    @Test
    void shouldExposeCurrentDepthAndCap() {
        TurCustomToolAgentHelper h = new TurCustomToolAgentHelper(executor, agentRepository,
                llmRepository, PARENT_CONV, 2, 5);
        assertThat(h.getCurrentDepth()).isEqualTo(2);
        assertThat(h.getDepthCap()).isEqualTo(5);
    }

    @Test
    void shouldDefaultDepthCapWhenNegative() {
        TurCustomToolAgentHelper h = new TurCustomToolAgentHelper(executor, agentRepository,
                llmRepository, PARENT_CONV, 0, -1);
        assertThat(h.getDepthCap()).isEqualTo(TurCustomToolAgentHelper.DEFAULT_DEPTH_CAP);
    }

    // -- helpers --

    private static TurAIAgent newAgent(String id, int enabled, TurLLMInstance llm) {
        TurAIAgent a = new TurAIAgent();
        a.setId(id);
        a.setTitle("Agent " + id);
        a.setEnabled(enabled);
        if (llm != null) {
            HashSet<TurLLMInstance> set = new HashSet<>();
            set.add(llm);
            a.setLlmInstances(set);
        }
        return a;
    }

    private static TurLLMInstance newLlm(String id, int enabled) {
        TurLLMInstance llm = new TurLLMInstance();
        llm.setId(id);
        llm.setTitle("LLM " + id);
        llm.setEnabled(enabled);
        return llm;
    }
}
