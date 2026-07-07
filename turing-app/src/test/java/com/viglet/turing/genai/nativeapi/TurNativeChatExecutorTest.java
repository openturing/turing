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
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
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
    @Mock private com.viglet.turing.genai.nativeapi.anthropic.memory.TurAnthropicMemoryService anthropicMemoryService;
    @Mock private com.viglet.turing.genai.nativeapi.gemini.TurGoogleGenAiNativeService googleGenAiNativeService;
    @Mock private com.viglet.turing.genai.nativeapi.gemini.TurGeminiComputerUseService geminiComputerUseService;
    @Mock private TurChatPromptAssembler promptAssembler;
    @Mock private TurAgentPersonaResolver personaResolver;
    @Mock private com.viglet.turing.genai.nativeapi.capability.TurNativeCapabilityResolver capabilityResolver;
    @Mock private com.viglet.turing.genai.TurChatToolResolver toolResolver;
    @Mock private com.viglet.turing.genai.nativeapi.mcp.TurMcpFederationDirectory mcpFederationDirectory;
    @Mock private com.viglet.turing.genai.tool.TurRagSearchToolService ragSearchToolService;
    @Mock private com.viglet.turing.genai.citation.TurCitationDriftService citationDriftService;
    @Mock private com.viglet.turing.genai.nativeapi.files.TurNativeDocumentGroundingService nativeDocumentGroundingService;
    @Mock private com.viglet.turing.genai.nativeapi.anthropic.TurAnthropicContextManagement anthropicContextManagement;
    @Mock private TurGenAiLlmProvider provider;
    @Mock private OpenAIClient openAiClient;
    @Mock private AnthropicClient anthropicClient;
    @Mock private com.google.genai.Client geminiClient;

    private final com.viglet.turing.genai.provider.TurProviderOptionsParser optionsParser =
            new com.viglet.turing.genai.provider.TurProviderOptionsParser();

    // Real resolver over a default (empty) config → resolveForChat returns empty,
    // so existing assertions (no service_tier applied) are unchanged.
    private final com.viglet.turing.genai.servicetier.TurServiceTierResolver serviceTierResolver =
            new com.viglet.turing.genai.servicetier.TurServiceTierResolver(
                    optionsParser, new com.viglet.turing.properties.TurConfigProperties());

    // Real resolver (dependency-free); a default-scope agent returns the
    // conversation id verbatim, so the existing routing assertions are unchanged.
    private final com.viglet.turing.genai.nativeapi.anthropic.memory.TurAgentMemoryScopeResolver
            memoryScopeResolver =
            new com.viglet.turing.genai.nativeapi.anthropic.memory.TurAgentMemoryScopeResolver();

    // F.9 / T167 — real, dependency-free; a default agent has no stored-completions
    // option, so the directive is DISABLED and the existing routing/assertions hold.
    private final com.viglet.turing.genai.nativeapi.openai.TurStoredCompletionsService
            storedCompletionsService =
            new com.viglet.turing.genai.nativeapi.openai.TurStoredCompletionsService(optionsParser);

    // T179 — real, dependency-free per-stage reasoning-effort policy.
    private final com.viglet.turing.genai.nativeapi.openai.TurReasoningEffortResolver
            reasoningEffortResolver =
            new com.viglet.turing.genai.nativeapi.openai.TurReasoningEffortResolver();

    // T181 — disabled by default so no safety_identifier is resolved/asserted in
    // the existing routing tests (currentSafetyIdentifier() returns empty).
    @Mock private com.viglet.turing.genai.safety.TurSafetyIdentifierService safetyIdentifierService;

    // T187 — mock; default agent has liveAnswersEnabled=false so isEnabled() returns
    // false (Mockito boolean default) and the existing routing assertions are unchanged.
    @Mock private com.viglet.turing.genai.nativeapi.liveanswers.TurLiveAnswersService liveAnswersService;

    // T519 — Cohere native citation path mock; default agent has citations off so
    // the existing routing assertions are unchanged.
    @Mock private com.viglet.turing.genai.nativeapi.cohere.TurCohereMessagesService cohereMessagesService;

    // T618 — prompt-capture mock; default agent has promptCaptureEnabled=false so
    // isEnabled() returns false and no capture write happens on the routing tests.
    @Mock private com.viglet.turing.genai.capture.TurPromptCaptureService promptCaptureService;

    private TurNativeChatExecutor executor() {
        return new TurNativeChatExecutor(providerFactory, nativeClient, capabilityService,
                openAiResponsesService, openAiComputerUseService, anthropicMessagesService,
                anthropicComputerUseService, anthropicEditorService, anthropicMemoryService,
                googleGenAiNativeService, geminiComputerUseService, cohereMessagesService,
                promptAssembler, personaResolver,
                capabilityResolver, toolResolver, mcpFederationDirectory, ragSearchToolService,
                optionsParser, citationDriftService, serviceTierResolver, anthropicContextManagement,
                memoryScopeResolver, storedCompletionsService, nativeDocumentGroundingService,
                reasoningEffortResolver, safetyIdentifierService, liveAnswersService,
                promptCaptureService);
    }

    @BeforeEach
    void federationDefaultsToEmpty() {
        // T145 — no MCP federation unless a test opts in; keeps the existing
        // routing tests unchanged (the directory is consulted on every native turn).
        lenient().when(mcpFederationDirectory.federate(any(), any())).thenReturn(List.of());
        // T164/T165 — default to no context-management opt-in on every Anthropic turn.
        lenient().when(anthropicContextManagement.resolve(any())).thenReturn(
                com.viglet.turing.genai.nativeapi.anthropic.TurAnthropicContextManagement.Options.NONE);
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
                any(), anyList(), any(), anyList(), any(), any(), any()))
                .thenReturn(Flux.just(new ChatResponse("assistant", "claude")));

        Optional<Flux<ChatResponse>> result =
                executor().tryExecute(agent(), instance, history, null, "conv-1");

        assertThat(result).isPresent();
        assertThat(result.get().blockFirst().content()).isEqualTo("claude");
        verifyNoInteractions(openAiResponsesService, openAiComputerUseService,
                anthropicComputerUseService, anthropicEditorService);
    }

    @Test
    void citationsRequestOptionForcesAnthropicPathWithoutNativeCapabilities() {
        // T152 / §X.7.a — an agent with the "citations" request option takes the
        // native Anthropic path even when no native TOOL capability is enabled on
        // the instance; the retrieved passages flow to the Messages service.
        TurLLMInstance instance = instance();
        when(providerFactory.getProvider(instance)).thenReturn(provider);
        when(provider.getPluginType()).thenReturn("anthropic");
        when(capabilityService.enabledFor("inst-1", "anthropic")).thenReturn(List.of());
        when(nativeClient.anthropic(instance)).thenReturn(Optional.of(anthropicClient));
        when(promptAssembler.assemble(any(TurChatPromptRequest.class)))
                .thenReturn(List.of(new SystemMessage("FUSED"), new UserMessage("hello")));
        List<com.viglet.turing.genai.citation.TurCitationDocument> docs = List.of(
                new com.viglet.turing.genai.citation.TurCitationDocument("doc-a", "Doc A", "/u", "text"));
        when(ragSearchToolService.retrievePassagesForCitations(eq("hello"), anyInt())).thenReturn(docs);
        when(anthropicMessagesService.chat(eq(anthropicClient), eq(instance), anyList(), any(),
                anyList(), any(), eq(docs), any(), any(), any()))
                .thenReturn(Flux.just(new ChatResponse("assistant", "cited")));
        // T155 — with citations on, the flux is routed through drift persistence;
        // the no-op pass-through (disabled) returns the source unchanged.
        when(citationDriftService.tapForPersistence(any(), any(), any(), any(), anyInt()))
                .thenAnswer(inv -> inv.getArgument(0));

        TurAIAgent agent = agent();
        agent.setRequestOptionsJson("{\"citations\":\"true\"}");

        Optional<Flux<ChatResponse>> result =
                executor().tryExecute(agent, instance, history, null, "conv-1");

        assertThat(result).isPresent();
        assertThat(result.get().blockFirst().content()).isEqualTo("cited");
        verify(ragSearchToolService).retrievePassagesForCitations("hello", 5);
    }

    @Test
    void noCitationsWhenRequestOptionAbsentAndNoCapabilities() {
        // Without the citations option and with no native capabilities, the turn
        // falls through to the Spring AI executor (empty) — unchanged behaviour.
        TurLLMInstance instance = instance();
        when(providerFactory.getProvider(instance)).thenReturn(provider);
        when(provider.getPluginType()).thenReturn("anthropic");
        when(capabilityService.enabledFor("inst-1", "anthropic")).thenReturn(List.of());

        assertThat(executor().tryExecute(agent(), instance, history, null, "conv-1")).isEmpty();
        verifyNoInteractions(anthropicMessagesService, ragSearchToolService);
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
    void routesToAnthropicMemoryLoopWhenMemoryEnabled() {
        // T163 / §X.9.a — the memory tool is a client-executed multi-turn loop, so
        // it owns the turn (like the editor) when enabled.
        TurLLMInstance instance = instance();
        when(providerFactory.getProvider(instance)).thenReturn(provider);
        when(provider.getPluginType()).thenReturn("anthropic");
        List<EnabledCapability> caps =
                List.of(new EnabledCapability(TurNativeCapability.ANTHROPIC_MEMORY, null));
        when(capabilityService.enabledFor("inst-1", "anthropic")).thenReturn(caps);
        when(nativeClient.anthropic(instance)).thenReturn(Optional.of(anthropicClient));
        when(promptAssembler.assemble(any(TurChatPromptRequest.class)))
                .thenReturn(List.of(new SystemMessage("FUSED"), new UserMessage("hello")));
        when(anthropicMemoryService.chat(eq(anthropicClient), eq(instance), any(), eq("conv-1"),
                anyList(), any(), anyList(), any()))
                .thenReturn(Flux.just(new ChatResponse("assistant", "remembered")));

        Optional<Flux<ChatResponse>> result =
                executor().tryExecute(agent(), instance, history, null, "conv-1");

        assertThat(result).isPresent();
        assertThat(result.get().blockFirst().content()).isEqualTo("remembered");
        verifyNoInteractions(anthropicMessagesService, anthropicComputerUseService,
                anthropicEditorService, openAiResponsesService);
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
                any(), anyList(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(Flux.just(new ChatResponse("assistant", "hi")));

        Optional<Flux<ChatResponse>> result =
                executor().tryExecute(agent(), instance, history, null, "conv-1");

        assertThat(result).isPresent();
        List<ChatResponse> emitted = result.get().collectList().block();
        assertThat(emitted).hasSize(1);
        assertThat(emitted.get(0).content()).isEqualTo("hi");
    }

    @Test
    void liveAnswersAugmentsSystemPromptOnOpenAiPath() {
        // T187 / §X.15.a — when live-answers is on, the executor runs the assembled
        // system prompt through TurLiveAnswersService.augment and passes the
        // grounded prompt to the Responses service. groundWithDocuments is false on
        // the OpenAI path, so the [KB-n] block is injected here (the leg the
        // Responses path otherwise lacks) rather than as Anthropic citation blocks.
        TurLLMInstance instance = instance();
        when(providerFactory.getProvider(instance)).thenReturn(provider);
        when(provider.getPluginType()).thenReturn("openai");
        List<EnabledCapability> caps =
                List.of(new EnabledCapability(TurNativeCapability.OPENAI_WEB_SEARCH, null));
        when(capabilityService.enabledFor("inst-1", "openai")).thenReturn(caps);
        when(nativeClient.openAi(instance)).thenReturn(Optional.of(openAiClient));
        when(promptAssembler.assemble(any(TurChatPromptRequest.class)))
                .thenReturn(List.of(new SystemMessage("BASE"), new UserMessage("hello")));
        TurAIAgent agent = agent();
        agent.setLiveAnswersEnabled(true);
        when(liveAnswersService.isEnabled(agent)).thenReturn(true);
        when(liveAnswersService.augment("BASE", "hello", false)).thenReturn("BASE + GROUNDED");
        ArgumentCaptor<String> systemPromptCaptor = ArgumentCaptor.captor();
        when(openAiResponsesService.chat(eq(openAiClient), eq(instance), anyList(),
                systemPromptCaptor.capture(), anyList(), any(), any(), any(), any(), any(), any(),
                any(), any()))
                .thenReturn(Flux.just(new ChatResponse("assistant", "fused")));

        Optional<Flux<ChatResponse>> result =
                executor().tryExecute(agent, instance, history, null, "conv-1");

        assertThat(result).isPresent();
        assertThat(result.get().blockFirst().content()).isEqualTo("fused");
        assertThat(systemPromptCaptor.getValue()).isEqualTo("BASE + GROUNDED");
        verify(liveAnswersService).augment("BASE", "hello", false);
    }

    @Test
    void reasoningSummaryRequestOptionForcesResponsesPathOnReasoningModel() {
        // T178 / §X.13.a — an agent with the "reasoning-summary" request option on
        // a reasoning model (o3) takes the native Responses path even with no
        // native capability enabled, and the summary mode rides the request.
        TurLLMInstance instance = instance();
        instance.setModelName("o3");
        TurAIAgent agent = agent();
        agent.setRequestOptionsJson("{\"reasoning-summary\":\"detailed\"}");
        when(providerFactory.getProvider(instance)).thenReturn(provider);
        when(provider.getPluginType()).thenReturn("openai");
        when(capabilityService.enabledFor("inst-1", "openai")).thenReturn(List.of());
        when(nativeClient.openAi(instance)).thenReturn(Optional.of(openAiClient));
        when(promptAssembler.assemble(any(TurChatPromptRequest.class)))
                .thenReturn(List.of(new SystemMessage("FUSED"), new UserMessage("hello")));
        ArgumentCaptor<TurOpenAiResponsesService.ReasoningOptions> reasoningCaptor =
                ArgumentCaptor.captor();
        when(openAiResponsesService.chat(eq(openAiClient), eq(instance), anyList(),
                any(), anyList(), any(), any(), any(), any(), any(), reasoningCaptor.capture(),
                any(), any()))
                .thenReturn(Flux.just(new ChatResponse("assistant", "hi")));

        Optional<Flux<ChatResponse>> result =
                executor().tryExecute(agent, instance, history, null, "conv-1");

        assertThat(result).isPresent();
        assertThat(reasoningCaptor.getValue().summary()).isEqualTo("detailed");
        // T179 — summary-only opt-in still gets the LIVE-stage effort default (low).
        assertThat(reasoningCaptor.getValue().effort()).isEqualTo("low");
    }

    @Test
    void reasoningEffortRequestOptionForcesResponsesPathAndOverridesDefault() {
        // T179 / §X.13.b — the reasoning-effort option alone forces the native
        // Responses path on a reasoning model and its explicit value overrides
        // the LIVE-stage default; no summary mode means no summary event.
        TurLLMInstance instance = instance();
        instance.setModelName("o4-mini");
        TurAIAgent agent = agent();
        agent.setRequestOptionsJson("{\"reasoning-effort\":\"high\"}");
        when(providerFactory.getProvider(instance)).thenReturn(provider);
        when(provider.getPluginType()).thenReturn("openai");
        when(capabilityService.enabledFor("inst-1", "openai")).thenReturn(List.of());
        when(nativeClient.openAi(instance)).thenReturn(Optional.of(openAiClient));
        when(promptAssembler.assemble(any(TurChatPromptRequest.class)))
                .thenReturn(List.of(new SystemMessage("FUSED"), new UserMessage("hello")));
        ArgumentCaptor<TurOpenAiResponsesService.ReasoningOptions> reasoningCaptor =
                ArgumentCaptor.captor();
        when(openAiResponsesService.chat(eq(openAiClient), eq(instance), anyList(),
                any(), anyList(), any(), any(), any(), any(), any(), reasoningCaptor.capture(),
                any(), any()))
                .thenReturn(Flux.just(new ChatResponse("assistant", "hi")));

        Optional<Flux<ChatResponse>> result =
                executor().tryExecute(agent, instance, history, null, "conv-1");

        assertThat(result).isPresent();
        assertThat(reasoningCaptor.getValue().effort()).isEqualTo("high");
        assertThat(reasoningCaptor.getValue().hasSummary()).isFalse();
    }

    @Test
    void reasoningSummaryRequestOptionIgnoredOnChatModel() {
        // T178 — on a non-reasoning model the option does NOT force the native
        // path; with no capability the turn defers to the Spring AI executor.
        TurLLMInstance instance = instance();
        instance.setModelName("gpt-4o-mini");
        TurAIAgent agent = agent();
        agent.setRequestOptionsJson("{\"reasoning-summary\":\"auto\"}");
        when(providerFactory.getProvider(instance)).thenReturn(provider);
        when(provider.getPluginType()).thenReturn("openai");
        when(capabilityService.enabledFor("inst-1", "openai")).thenReturn(List.of());

        assertThat(executor().tryExecute(agent, instance, history, null, "conv-1")).isEmpty();
        verifyNoInteractions(openAiResponsesService);
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
                promptCaptor.capture(), anyList(), any(), any(), any(), any(), any(), any(), any(),
                any()))
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
    void routesToGeminiNativeServiceWhenGeminiAndCapabilityEnabled() {
        // T489 / §X.19 — a gemini instance with an enabled native capability
        // routes the turn to the Gemini native service with the fused prompt +
        // history. (The real gemini capabilities arrive in T490; here any
        // non-empty matrix list flips the branch on — the executor passes the
        // list straight through to the native service.)
        TurLLMInstance instance = instance();
        when(providerFactory.getProvider(instance)).thenReturn(provider);
        when(provider.getPluginType()).thenReturn("gemini");
        List<EnabledCapability> caps =
                List.of(new EnabledCapability(TurNativeCapability.OPENAI_WEB_SEARCH, null));
        when(capabilityService.enabledFor("inst-1", "gemini")).thenReturn(caps);
        when(nativeClient.gemini(instance)).thenReturn(Optional.of(geminiClient));
        when(promptAssembler.assemble(any(TurChatPromptRequest.class)))
                .thenReturn(List.of(new SystemMessage("FUSED"), new UserMessage("hello")));
        ArgumentCaptor<List<ChatMessageItem>> historyCaptor = ArgumentCaptor.captor();
        ArgumentCaptor<String> promptCaptor = ArgumentCaptor.captor();
        when(googleGenAiNativeService.chat(eq(geminiClient), eq(instance), historyCaptor.capture(),
                promptCaptor.capture(), anyList(), any()))
                .thenReturn(Flux.just(new ChatResponse("assistant", "gemini")));

        Optional<Flux<ChatResponse>> result =
                executor().tryExecute(agent(), instance, history, null, "conv-1");

        assertThat(result).isPresent();
        assertThat(result.get().blockFirst().content()).isEqualTo("gemini");
        assertThat(promptCaptor.getValue()).isEqualTo("FUSED");
        assertThat(historyCaptor.getValue()).containsExactly(new ChatMessageItem("user", "hello"));
        verifyNoInteractions(openAiResponsesService, anthropicMessagesService);
    }

    @Test
    void routesToGeminiComputerUseLoopWhenComputerUseEnabled() {
        // T494 / §X.19 — Gemini computer_use is a multi-turn loop that owns the turn.
        TurLLMInstance instance = instance();
        when(providerFactory.getProvider(instance)).thenReturn(provider);
        when(provider.getPluginType()).thenReturn("gemini");
        EnabledCapability cap = new EnabledCapability(TurNativeCapability.GEMINI_COMPUTER_USE, null);
        when(capabilityService.enabledFor("inst-1", "gemini")).thenReturn(List.of(cap));
        when(nativeClient.gemini(instance)).thenReturn(Optional.of(geminiClient));
        when(promptAssembler.assemble(any(TurChatPromptRequest.class)))
                .thenReturn(List.of(new SystemMessage("FUSED"), new UserMessage("hello")));
        when(geminiComputerUseService.chat(eq(geminiClient), eq(instance), anyList(), any(), eq(cap)))
                .thenReturn(Flux.just(new ChatResponse("assistant", "operated")));

        Optional<Flux<ChatResponse>> result =
                executor().tryExecute(agent(), instance, history, null, "conv-1");

        assertThat(result).isPresent();
        assertThat(result.get().blockFirst().content()).isEqualTo("operated");
        verifyNoInteractions(googleGenAiNativeService);
    }

    @Test
    void emptyWhenGeminiHasNoCapabilityEnabled() {
        // Foundation behaviour: a gemini instance with an empty capability matrix
        // falls through to the Spring AI path — unchanged for every existing
        // Gemini agent.
        TurLLMInstance instance = instance();
        when(providerFactory.getProvider(instance)).thenReturn(provider);
        when(provider.getPluginType()).thenReturn("gemini");
        when(capabilityService.enabledFor("inst-1", "gemini")).thenReturn(List.of());

        assertThat(executor().tryExecute(agent(), instance, history, null, "conv-1")).isEmpty();
        verifyNoInteractions(googleGenAiNativeService);
    }

    @Test
    void emptyWhenGeminiClientUnavailable() {
        TurLLMInstance instance = instance();
        when(providerFactory.getProvider(instance)).thenReturn(provider);
        when(provider.getPluginType()).thenReturn("gemini");
        when(capabilityService.enabledFor("inst-1", "gemini"))
                .thenReturn(List.of(new EnabledCapability(TurNativeCapability.OPENAI_WEB_SEARCH, null)));
        when(nativeClient.gemini(instance)).thenReturn(Optional.empty());

        assertThat(executor().tryExecute(agent(), instance, history, null, "conv-1")).isEmpty();
        verifyNoInteractions(googleGenAiNativeService);
    }

    @Test
    void emptyWhenProviderResolutionFails() {
        TurLLMInstance instance = instance();
        lenient().when(providerFactory.getProvider(instance))
                .thenThrow(new IllegalStateException("no vendor"));

        assertThat(executor().tryExecute(agent(), instance, history, null, "conv-1")).isEmpty();
    }
}
