/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.skill.activation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
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
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.tool.ToolCallback;

import com.viglet.turing.genai.TurToolExecutionLoop;
import com.viglet.turing.genai.tool.TurDslToolService;
import com.viglet.turing.genai.tool.TurToolCallbackPipeline;
import com.viglet.turing.persistence.model.llm.TurLLMInstance;
import com.viglet.turing.persistence.model.skill.TurSkill;
import com.viglet.turing.persistence.repository.llm.TurLLMInstanceRepository;
import com.viglet.turing.resilience.llm.TurLlmModelFactory;
import com.viglet.turing.service.llm.tokenusage.TurLLMTokenUsageService;
import com.viglet.turing.system.TurGlobalSettingsService;
import com.viglet.turing.system.security.TurSecretCryptoService;

/**
 * T323 — verifies the skill delegation layer: gating on the sandbox AND a
 * configured Default LLM, the cheap parent prompt block + {@code run_skill}
 * callback, and that {@link TurSkillRunnerService#runSkill} drives the Default
 * LLM sub-loop and surfaces failures as text.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
@ExtendWith(MockitoExtension.class)
class TurSkillRunnerServiceTest {

    @Mock
    TurSkillActivationHarness harness;
    @Mock
    TurGlobalSettingsService globalSettingsService;
    @Mock
    TurLLMInstanceRepository llmInstanceRepository;
    @Mock
    TurLlmModelFactory llmModelFactory;
    @Mock
    TurSecretCryptoService secretCryptoService;
    @Mock
    TurLLMTokenUsageService tokenUsageService;
    @Mock
    TurToolCallbackPipeline toolCallbackPipeline;
    @Mock
    TurToolExecutionLoop toolExecutionLoop;

    private TurSkillRunnerService service;

    // Real instance (not a mock) so MethodToolCallbackProvider discovers the
    // genuine @Tool methods deterministically. Null deps are fine — tool
    // discovery only reflects over the methods, it never invokes them.
    private final TurDslToolService dslToolService =
            new TurDslToolService(null, null, null, null, null, null);

    @BeforeEach
    void setUp() {
        service = new TurSkillRunnerService(harness, globalSettingsService, llmInstanceRepository,
                llmModelFactory, secretCryptoService, tokenUsageService, toolCallbackPipeline,
                toolExecutionLoop, dslToolService);
    }

    private TurSkill skill(String id, String name) {
        TurSkill skill = new TurSkill();
        skill.setId(id);
        skill.setName(name);
        skill.setDescription(name + " does things");
        skill.setEnabled(1);
        return skill;
    }

    private TurLLMInstance enabledLlm() {
        TurLLMInstance instance = new TurLLMInstance();
        instance.setId("llm1");
        instance.setEnabled(1);
        instance.setApiKeyEncrypted("enc");
        return instance;
    }

    private void stubDefaultLlmAvailable() {
        when(harness.isAvailable()).thenReturn(true);
        when(globalSettingsService.getDefaultLlmId()).thenReturn("llm1");
        when(llmInstanceRepository.findById("llm1")).thenReturn(Optional.of(enabledLlm()));
    }

    @Test
    void unavailableWhenSandboxUnavailable() {
        when(harness.isAvailable()).thenReturn(false);
        assertThat(service.isAvailable()).isFalse();
    }

    @Test
    void unavailableWhenNoDefaultLlm() {
        when(harness.isAvailable()).thenReturn(true);
        when(globalSettingsService.getDefaultLlmId()).thenReturn("");
        assertThat(service.isAvailable()).isFalse();
    }

    @Test
    void unavailableWhenDefaultLlmDisabled() {
        when(harness.isAvailable()).thenReturn(true);
        when(globalSettingsService.getDefaultLlmId()).thenReturn("llm1");
        TurLLMInstance disabled = enabledLlm();
        disabled.setEnabled(0);
        when(llmInstanceRepository.findById("llm1")).thenReturn(Optional.of(disabled));
        assertThat(service.isAvailable()).isFalse();
    }

    @Test
    void promptBlockListsSkillsAndRunSkillProtocol() {
        stubDefaultLlmAvailable();
        String block = service.buildSystemPromptBlock(List.of(skill("2", "zebra"), skill("1", "alpha")));
        assertThat(block)
                .contains("# Skills")
                .contains("## alpha")
                .contains("## zebra")
                .contains("run_skill");
        assertThat(block.indexOf("## alpha")).isLessThan(block.indexOf("## zebra"));
    }

    @Test
    void promptBlockEmptyWhenUnavailableOrNoSkills() {
        when(harness.isAvailable()).thenReturn(false);
        assertThat(service.buildSystemPromptBlock(List.of(skill("1", "alpha")))).isEmpty();
    }

    @Test
    void toolCallbacksReturnsSingleRunSkill() {
        stubDefaultLlmAvailable();
        ToolCallback[] callbacks = service.buildToolCallbacks(List.of(skill("1", "alpha")));
        assertThat(callbacks).hasSize(1);
        assertThat(callbacks[0].getToolDefinition().name()).isEqualTo(TurRunSkillToolCallback.TOOL_NAME);
    }

    @Test
    void offeredSkillsBlankPinReturnsAllAvailable() {
        // T325 — a blank/null pin offers the full available set (legacy
        // progressive disclosure).
        List<TurSkill> all = List.of(skill("1", "alpha"), skill("2", "zebra"));
        when(harness.availableSkills()).thenReturn(all);
        assertThat(service.offeredSkills(null)).isEqualTo(all);
        assertThat(service.offeredSkills("  ")).isEqualTo(all);
    }

    @Test
    void offeredSkillsPinsSingleSkillByIdOrName() {
        // T325 — a pin narrows to exactly one skill; matches by id or by
        // case-insensitive name (the SDK-friendly form).
        List<TurSkill> all = List.of(skill("id-a", "alpha"), skill("id-z", "Zebra"));
        when(harness.availableSkills()).thenReturn(all);

        assertThat(service.offeredSkills("id-z")).singleElement()
                .extracting(TurSkill::getName).isEqualTo("Zebra");
        assertThat(service.offeredSkills("alpha")).singleElement()
                .extracting(TurSkill::getId).isEqualTo("id-a");
        assertThat(service.offeredSkills("ZEBRA")).singleElement()
                .extracting(TurSkill::getId).isEqualTo("id-z");
    }

    @Test
    void offeredSkillsUnknownPinReturnsEmptyNotAll() {
        // T325 — an unknown/disabled pin must NOT silently fall back to the
        // whole set (a pinned mode can't widen).
        when(harness.availableSkills()).thenReturn(List.of(skill("id-a", "alpha")));
        assertThat(service.offeredSkills("does-not-exist")).isEmpty();
    }

    @Test
    void runSkillUnavailableReturnsMessage() {
        when(harness.isAvailable()).thenReturn(false);
        assertThat(service.runSkill(skill("1", "alpha"), "task", "agentA", "conv1"))
                .contains("unavailable");
        verifyNoInteractions(llmModelFactory);
    }

    @Test
    void runSkillNullSkillReturnsMessage() {
        assertThat(service.runSkill(null, "task", "agentA", "conv1")).contains("no skill resolved");
        verifyNoInteractions(harness);
    }

    @Test
    void runSkillDrivesDefaultLlmSubLoop() {
        stubDefaultLlmAvailable();
        TurSkill alpha = skill("id-alpha", "alpha");
        when(secretCryptoService.decrypt("enc")).thenReturn("key");
        ChatModel chatModel = mock(ChatModel.class);
        when(llmModelFactory.createChatModel(any(TurLLMInstance.class), anyString())).thenReturn(chatModel);
        when(harness.buildSystemPromptBlock(any())).thenReturn("\n\n## alpha\nalpha does things");
        when(harness.buildToolCallbacks(any())).thenReturn(new ToolCallback[0]);
        when(toolCallbackPipeline.decorate(any(ToolCallback[].class))).thenReturn(new ToolCallback[0]);
        when(toolExecutionLoop.call(any(), any())).thenReturn(responseWith("the final result"));

        String out = service.runSkill(alpha, "make a chart", "agentA", "conv1");

        assertThat(out).isEqualTo("the final result");
        verify(toolExecutionLoop).call(any(), any());
        // Cost-governance: the skill sub-loop records usage via the 5-arg
        // overload, tagging the cost stage "chat.skill" (agentId null on this
        // path). any() — not anyString() — on the 4th arg so it matches null.
        verify(tokenUsageService).recordUsage(any(TurLLMInstance.class), any(ChatResponse.class),
                anyString(), any(), anyString());
    }

    @Test
    void runSkillExposesTuringSearchToolsToSubLoop() {
        // T324 — the sub-loop's raw toolset (handed to the pipeline) must include
        // the activation tools PLUS the Semantic Navigation DSL search tools, so a
        // skill can leverage Turing's own indexed data.
        stubDefaultLlmAvailable();
        TurSkill alpha = skill("id-alpha", "alpha");
        TurLoadSkillToolCallback loadSkill = new TurLoadSkillToolCallback(List.of(alpha), null);
        when(secretCryptoService.decrypt("enc")).thenReturn("key");
        when(llmModelFactory.createChatModel(any(TurLLMInstance.class), anyString()))
                .thenReturn(mock(ChatModel.class));
        when(harness.buildSystemPromptBlock(any())).thenReturn("block");
        when(harness.buildToolCallbacks(any())).thenReturn(new ToolCallback[] { loadSkill });
        ArgumentCaptor<ToolCallback[]> captor = ArgumentCaptor.forClass(ToolCallback[].class);
        when(toolCallbackPipeline.decorate(captor.capture())).thenReturn(new ToolCallback[0]);
        when(toolExecutionLoop.call(any(), any())).thenReturn(responseWith("done"));

        service.runSkill(alpha, "search the catalog", "agentA", "conv1");

        List<String> toolNames = java.util.Arrays.stream(captor.getValue())
                .map(cb -> cb.getToolDefinition().name())
                .toList();
        assertThat(toolNames)
                .contains(TurLoadSkillToolCallback.TOOL_NAME)
                .contains("dsl_list_indices", "dsl_search");
    }

    @Test
    void runSkillEmptyOutputReturnsPlaceholder() {
        stubDefaultLlmAvailable();
        when(secretCryptoService.decrypt(anyString())).thenReturn("key");
        ChatModel chatModel = mock(ChatModel.class);
        when(llmModelFactory.createChatModel(any(TurLLMInstance.class), anyString())).thenReturn(chatModel);
        when(harness.buildSystemPromptBlock(any())).thenReturn("block");
        when(harness.buildToolCallbacks(any())).thenReturn(new ToolCallback[0]);
        when(toolCallbackPipeline.decorate(any(ToolCallback[].class))).thenReturn(new ToolCallback[0]);
        when(toolExecutionLoop.call(any(), any())).thenReturn(responseWith("   "));

        assertThat(service.runSkill(skill("id-alpha", "alpha"), "t", "agentA", "conv1"))
                .contains("produced no output");
    }

    @Test
    void runSkillFailureReportedNotThrown() {
        stubDefaultLlmAvailable();
        when(secretCryptoService.decrypt(anyString())).thenThrow(new IllegalStateException("boom"));
        assertThat(service.runSkill(skill("id-alpha", "alpha"), "t", "agentA", "conv1"))
                .contains("Failed to run skill 'alpha'").contains("boom");
    }

    private ChatResponse responseWith(String text) {
        return new ChatResponse(List.of(new Generation(new AssistantMessage(text))),
                ChatResponseMetadata.builder().build());
    }
}
