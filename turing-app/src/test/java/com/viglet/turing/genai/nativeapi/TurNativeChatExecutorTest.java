/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.nativeapi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;

import com.anthropic.client.AnthropicClient;
import com.openai.client.OpenAIClient;
import com.viglet.turing.genai.TurChatPromptAssembler;
import com.viglet.turing.genai.TurChatPromptRequest;
import com.viglet.turing.genai.TurAgentChatExecutor.ChatMessageItem;
import com.viglet.turing.genai.TurAgentChatExecutor.ChatResponse;
import com.viglet.turing.genai.nativeapi.TurNativeCapabilityService.EnabledCapability;
import com.viglet.turing.genai.nativeapi.anthropic.TurAnthropicMessagesService;
import com.viglet.turing.genai.nativeapi.anthropic.computeruse.TurAnthropicComputerUseService;
import com.viglet.turing.genai.nativeapi.anthropic.editor.TurAnthropicEditorService;
import com.viglet.turing.genai.nativeapi.openai.TurOpenAiResponsesService;
import com.viglet.turing.genai.nativeapi.openai.computeruse.TurOpenAiComputerUseService;
import com.viglet.turing.genai.persona.TurAgentPersonaResolver;
import com.viglet.turing.genai.provider.llm.TurGenAiLlmProvider;
import com.viglet.turing.genai.provider.llm.TurGenAiLlmProviderFactory;
import com.viglet.turing.persistence.model.agent.TurAIAgent;
import com.viglet.turing.persistence.model.llm.TurLLMInstance;

import reactor.core.publisher.Flux;

@ExtendWith(MockitoExtension.class)
class TurNativeChatExecutorTest {

    @Mock private TurGenAiLlmProviderFactory providerFactory;
    @Mock private TurNativeProviderClient nativeClient;
    @Mock private TurNativeCapabilityService capabilityService;
    @Mock private TurOpenAiResponsesService openAiResponsesService;
    @Mock private TurOpenAiComputerUseService openAiComputerUseService;
    @Mock private TurAnthropicMessagesService anthropicMessagesService;
    @Mock private TurAnthropicComputerUseService anthropicComputerUseService;
    @Mock private TurAnthropicEditorService anthropicEditorService;
    @Mock private TurChatPromptAssembler promptAssembler;
    @Mock private TurAgentPersonaResolver personaResolver;
    @Mock private TurGenAiLlmProvider provider;
    @Mock private OpenAIClient openAiClient;
    @Mock private AnthropicClient anthropicClient;

    private TurNativeChatExecutor executor() {
        return new TurNativeChatExecutor(providerFactory, nativeClient, capabilityService,
                openAiResponsesService, openAiComputerUseService, anthropicMessagesService,
                anthropicComputerUseService, anthropicEditorService, promptAssembler, personaResolver);
    }

    private static TurLLMInstance instance() {
        TurLLMInstance instance = new TurLLMInstance();
        instance.setId("inst-1");
        instance.setTitle("OpenAI");
        return instance;
    }

    private static TurAIAgent agent() {
        TurAIAgent agent = new TurAIAgent();
        agent.setTitle("Agent");
        agent.setSystemPrompt("You are helpful.");
        return agent;
    }

    private final List<ChatMessageItem> history = List.of(new ChatMessageItem("user", "hello"));

    @Test
    void emptyWhenVendorHasNoNativePath() {
        // Only OpenAI (F.2) and Anthropic (F.3) have a native path; everything
        // else falls through to the Spring AI executor.
        TurLLMInstance instance = instance();
        when(providerFactory.getProvider(instance)).thenReturn(provider);
        when(provider.getPluginType()).thenReturn("ollama");

        assertThat(executor().tryExecute(agent(), instance, history, null, "conv-1")).isEmpty();
        verifyNoInteractions(anthropicMessagesService, openAiResponsesService);
    }

    @Test
    void routesToAnthropicMessagesServiceWhenAnthropicAndCapabilityEnabled() {
        TurLLMInstance instance = instance();
        when(providerFactory.getProvider(instance)).thenReturn(provider);
        when(provider.getPluginType()).thenReturn("anthropic");
        List<EnabledCapability> caps =
                List.of(new EnabledCapability(TurNativeCapability.ANTHROPIC_WEB_SEARCH, null));
        when(capabilityService.enabledFor("inst-1", "anthropic")).thenReturn(caps);
        when(nativeClient.anthropic(instance)).thenReturn(Optional.of(anthropicClient));
        when(promptAssembler.assemble(any(TurChatPromptRequest.class)))
                .thenReturn(List.of(new SystemMessage("FUSED"), new UserMessage("hello")));
        when(anthropicMessagesService.chat(eq(anthropicClient), eq(instance), anyList(),
                any(), anyList()))
                .thenReturn(Flux.just(new ChatResponse("assistant", "claude")));

        Optional<Flux<ChatResponse>> result =
                executor().tryExecute(agent(), instance, history, null, "conv-1");

        assertThat(result).isPresent();
        assertThat(result.get().blockFirst().content()).isEqualTo("claude");
        verifyNoInteractions(openAiResponsesService, openAiComputerUseService,
                anthropicComputerUseService, anthropicEditorService);
    }

    @Test
    void routesToAnthropicEditorLoopWhenTextEditorEnabled() {
        TurLLMInstance instance = instance();
        when(providerFactory.getProvider(instance)).thenReturn(provider);
        when(provider.getPluginType()).thenReturn("anthropic");
        List<EnabledCapability> caps =
                List.of(new EnabledCapability(TurNativeCapability.ANTHROPIC_TEXT_EDITOR, null));
        when(capabilityService.enabledFor("inst-1", "anthropic")).thenReturn(caps);
        when(nativeClient.anthropic(instance)).thenReturn(Optional.of(anthropicClient));
        when(promptAssembler.assemble(any(TurChatPromptRequest.class)))
                .thenReturn(List.of(new SystemMessage("FUSED"), new UserMessage("hello")));
        when(anthropicEditorService.chat(eq(anthropicClient), eq(instance), any(), eq("conv-1"),
                anyList(), any(), anyList()))
                .thenReturn(Flux.just(new ChatResponse("assistant", "authored")));

        Optional<Flux<ChatResponse>> result =
                executor().tryExecute(agent(), instance, history, null, "conv-1");

        assertThat(result).isPresent();
        assertThat(result.get().blockFirst().content()).isEqualTo("authored");
        verifyNoInteractions(anthropicMessagesService, anthropicComputerUseService,
                openAiResponsesService);
    }

    @Test
    void routesToAnthropicComputerUseLoopWhenComputerUseEnabled() {
        TurLLMInstance instance = instance();
        when(providerFactory.getProvider(instance)).thenReturn(provider);
        when(provider.getPluginType()).thenReturn("anthropic");
        EnabledCapability cap = new EnabledCapability(TurNativeCapability.ANTHROPIC_COMPUTER_USE, null);
        when(capabilityService.enabledFor("inst-1", "anthropic")).thenReturn(List.of(cap));
        when(nativeClient.anthropic(instance)).thenReturn(Optional.of(anthropicClient));
        when(promptAssembler.assemble(any(TurChatPromptRequest.class)))
                .thenReturn(List.of(new SystemMessage("FUSED"), new UserMessage("hello")));
        when(anthropicComputerUseService.chat(eq(anthropicClient), eq(instance), anyList(), any(), eq(cap)))
                .thenReturn(Flux.just(new ChatResponse("assistant", "operated")));

        Optional<Flux<ChatResponse>> result =
                executor().tryExecute(agent(), instance, history, null, "conv-1");

        assertThat(result).isPresent();
        assertThat(result.get().blockFirst().content()).isEqualTo("operated");
        verifyNoInteractions(anthropicMessagesService, anthropicEditorService,
                openAiResponsesService, openAiComputerUseService);
    }

    @Test
    void emptyWhenAnthropicClientUnavailable() {
        TurLLMInstance instance = instance();
        when(providerFactory.getProvider(instance)).thenReturn(provider);
        when(provider.getPluginType()).thenReturn("anthropic");
        when(capabilityService.enabledFor("inst-1", "anthropic"))
                .thenReturn(List.of(new EnabledCapability(TurNativeCapability.ANTHROPIC_WEB_SEARCH, null)));
        when(nativeClient.anthropic(instance)).thenReturn(Optional.empty());

        assertThat(executor().tryExecute(agent(), instance, history, null, "conv-1")).isEmpty();
    }

    @Test
    void emptyWhenNoCapabilityEnabled() {
        TurLLMInstance instance = instance();
        when(providerFactory.getProvider(instance)).thenReturn(provider);
        when(provider.getPluginType()).thenReturn("openai");
        when(capabilityService.enabledFor("inst-1", "openai")).thenReturn(List.of());

        assertThat(executor().tryExecute(agent(), instance, history, null, "conv-1")).isEmpty();
    }

    @Test
    void emptyWhenNativeClientUnavailable() {
        TurLLMInstance instance = instance();
        when(providerFactory.getProvider(instance)).thenReturn(provider);
        when(provider.getPluginType()).thenReturn("openai");
        when(capabilityService.enabledFor("inst-1", "openai"))
                .thenReturn(List.of(new EnabledCapability(TurNativeCapability.OPENAI_WEB_SEARCH, null)));
        when(nativeClient.openAi(instance)).thenReturn(Optional.empty());

        assertThat(executor().tryExecute(agent(), instance, history, null, "conv-1")).isEmpty();
    }

    @Test
    void routesToResponsesServiceWhenOpenAiAndCapabilityEnabled() {
        TurLLMInstance instance = instance();
        when(providerFactory.getProvider(instance)).thenReturn(provider);
        when(provider.getPluginType()).thenReturn("openai");
        List<EnabledCapability> caps =
                List.of(new EnabledCapability(TurNativeCapability.OPENAI_WEB_SEARCH, null));
        when(capabilityService.enabledFor("inst-1", "openai")).thenReturn(caps);
        when(nativeClient.openAi(instance)).thenReturn(Optional.of(openAiClient));
        // T131 — the assembler now composes the system prompt + history; return
        // a persona-fused system message + the same user turn so the executor
        // splits it back into instructions + a flat input list.
        when(promptAssembler.assemble(argThat((TurChatPromptRequest r) ->
                r != null && history.equals(r.history()) && "conv-1".equals(r.conversationId())
                        && "hello".equals(r.lastUserMessage())
                        && "You are helpful.".equals(r.baseSystemPrompt()))))
                .thenReturn(List.of(new SystemMessage("You are helpful. (persona voice)"),
                        new UserMessage("hello")));
        when(openAiResponsesService.chat(eq(openAiClient), eq(instance), anyList(),
                any(), anyList()))
                .thenReturn(Flux.just(new ChatResponse("assistant", "hi")));

        Optional<Flux<ChatResponse>> result =
                executor().tryExecute(agent(), instance, history, null, "conv-1");

        assertThat(result).isPresent();
        List<ChatResponse> emitted = result.get().collectList().block();
        assertThat(emitted).hasSize(1);
        assertThat(emitted.get(0).content()).isEqualTo("hi");
    }

    @Test
    void passesAssembledSystemPromptAndHistoryToResponsesService() {
        TurLLMInstance instance = instance();
        when(providerFactory.getProvider(instance)).thenReturn(provider);
        when(provider.getPluginType()).thenReturn("openai");
        when(capabilityService.enabledFor("inst-1", "openai"))
                .thenReturn(List.of(new EnabledCapability(TurNativeCapability.OPENAI_WEB_SEARCH, null)));
        when(nativeClient.openAi(instance)).thenReturn(Optional.of(openAiClient));
        when(promptAssembler.assemble(argThat((TurChatPromptRequest r) ->
                r != null && history.equals(r.history()) && "conv-1".equals(r.conversationId())
                        && "hello".equals(r.lastUserMessage())
                        && "You are helpful.".equals(r.baseSystemPrompt()))))
                .thenReturn(List.of(new SystemMessage("FUSED PROMPT"), new UserMessage("hello")));
        ArgumentCaptor<List<ChatMessageItem>> historyCaptor = ArgumentCaptor.captor();
        ArgumentCaptor<String> promptCaptor = ArgumentCaptor.captor();
        when(openAiResponsesService.chat(eq(openAiClient), eq(instance), historyCaptor.capture(),
                promptCaptor.capture(), anyList()))
                .thenReturn(Flux.just(new ChatResponse("assistant", "hi")));

        executor().tryExecute(agent(), instance, history, null, "conv-1").get()
                .collectList().block();

        assertThat(promptCaptor.getValue()).isEqualTo("FUSED PROMPT");
        assertThat(historyCaptor.getValue())
                .containsExactly(new ChatMessageItem("user", "hello"));
    }

    @Test
    void routesToComputerUseLoopWhenComputerUseEnabled() {
        TurLLMInstance instance = instance();
        when(providerFactory.getProvider(instance)).thenReturn(provider);
        when(provider.getPluginType()).thenReturn("openai");
        EnabledCapability cap = new EnabledCapability(TurNativeCapability.OPENAI_COMPUTER_USE, null);
        when(capabilityService.enabledFor("inst-1", "openai")).thenReturn(List.of(cap));
        when(nativeClient.openAi(instance)).thenReturn(Optional.of(openAiClient));
        when(promptAssembler.assemble(any(TurChatPromptRequest.class)))
                .thenReturn(List.of(new SystemMessage("FUSED"), new UserMessage("hello")));
        when(openAiComputerUseService.chat(eq(openAiClient), eq(instance), anyList(), any(), eq(cap)))
                .thenReturn(Flux.just(new ChatResponse("assistant", "done")));

        Optional<Flux<ChatResponse>> result =
                executor().tryExecute(agent(), instance, history, null, "conv-1");

        assertThat(result).isPresent();
        assertThat(result.get().blockFirst().content()).isEqualTo("done");
        verifyNoInteractions(openAiResponsesService);
    }

    @Test
    void emptyWhenProviderResolutionFails() {
        TurLLMInstance instance = instance();
        lenient().when(providerFactory.getProvider(instance))
                .thenThrow(new IllegalStateException("no vendor"));

        assertThat(executor().tryExecute(agent(), instance, history, null, "conv-1")).isEmpty();
    }
}
