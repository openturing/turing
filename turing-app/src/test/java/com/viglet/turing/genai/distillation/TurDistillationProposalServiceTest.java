/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.distillation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.viglet.turing.persistence.model.agent.TurAIAgent;
import com.viglet.turing.persistence.model.agent.TurAgentSuggestion;
import com.viglet.turing.persistence.model.llm.TurLLMInstance;
import com.viglet.turing.persistence.repository.agent.TurAIAgentRepository;
import com.viglet.turing.persistence.repository.agent.TurAgentSuggestionRepository;
import com.viglet.turing.persistence.repository.llm.TurLLMInstanceRepository;
import com.viglet.turing.system.TurGlobalSettingsService;

/**
 * T191 / §X.15.e — unit tests for {@link TurDistillationProposalService}: opening
 * a {@code DISTILLATION_CANDIDATE} suggestion, and the approve-time model swap
 * (resolving the agent's eval LLM instance the same way distillation does).
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@ExtendWith(MockitoExtension.class)
class TurDistillationProposalServiceTest {

    @Mock private TurAgentSuggestionRepository suggestionRepository;
    @Mock private TurAIAgentRepository agentRepository;
    @Mock private TurLLMInstanceRepository llmInstanceRepository;
    @Mock private TurGlobalSettingsService globalSettingsService;

    @InjectMocks private TurDistillationProposalService service;

    private static TurLLMInstance instance(String id, int enabled, String model) {
        TurLLMInstance i = new TurLLMInstance();
        i.setId(id);
        i.setEnabled(enabled);
        i.setModelName(model);
        return i;
    }

    private static TurAIAgent agentWith(TurLLMInstance... instances) {
        TurAIAgent a = new TurAIAgent();
        a.setId("agent-1");
        a.setTitle("Agent");
        a.setLlmInstances(Set.of(instances));
        return a;
    }

    @Test
    void createProposalOpensADistillationCandidateSuggestion() {
        when(suggestionRepository.save(any(TurAgentSuggestion.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        TurAgentSuggestion s = service.createProposal("agent-1", "gpt-4o-mini",
                "ft:gpt-4o-mini:org::abc", 0.6, 0.82, 42, 1234L);

        ArgumentCaptor<TurAgentSuggestion> captor = ArgumentCaptor.captor();
        verify(suggestionRepository).save(captor.capture());
        TurAgentSuggestion saved = captor.getValue();
        assertThat(saved.getKind()).isEqualTo(TurAgentSuggestion.Kind.DISTILLATION_CANDIDATE);
        assertThat(saved.getAgentId()).isEqualTo("agent-1");
        assertThat(saved.getCurrentValue()).isEqualTo("gpt-4o-mini");
        assertThat(saved.getProposedValue()).isEqualTo("ft:gpt-4o-mini:org::abc");
        assertThat(saved.getBaselineScore()).isEqualTo(0.6);
        assertThat(saved.getProposedScore()).isEqualTo(0.82);
        assertThat(saved.getStatus()).isEqualTo(TurAgentSuggestion.Status.PENDING);
        assertThat(saved.getCreatedAt()).isEqualTo(1234L);
        assertThat(saved.getRationale()).contains("42").contains("ft:gpt-4o-mini:org::abc");
        assertThat(s).isSameAs(saved);
    }

    @Test
    void applyCandidateSwapsTheEnabledAgentInstanceModel() {
        TurLLMInstance enabled = instance("inst-1", 1, "gpt-4o-mini");
        when(agentRepository.findById("agent-1")).thenReturn(Optional.of(agentWith(enabled)));

        boolean applied = service.applyCandidate("agent-1", "ft:gpt-4o-mini:org::abc");

        assertThat(applied).isTrue();
        assertThat(enabled.getModelName()).isEqualTo("ft:gpt-4o-mini:org::abc");
        verify(llmInstanceRepository).save(enabled);
    }

    @Test
    void applyCandidateFalseWhenBlankModel() {
        boolean applied = service.applyCandidate("agent-1", "  ");
        assertThat(applied).isFalse();
        verify(agentRepository, never()).findById(any());
    }

    @Test
    void applyCandidateFalseWhenNoInstanceResolves() {
        // Agent has no enabled LLM and no default configured → nothing to apply.
        when(agentRepository.findById("agent-1")).thenReturn(Optional.of(agentWith()));
        when(globalSettingsService.getDefaultLlmId()).thenReturn(null);

        boolean applied = service.applyCandidate("agent-1", "ft:model");

        assertThat(applied).isFalse();
        verify(llmInstanceRepository, never()).save(any());
    }
}
