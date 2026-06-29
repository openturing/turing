/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.handoff;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.viglet.turing.genai.TurAgentChatExecutor;
import com.viglet.turing.persistence.model.agent.TurAIAgent;
import com.viglet.turing.persistence.repository.agent.TurAIAgentRepository;
import com.viglet.turing.persistence.repository.llm.TurLLMInstanceRepository;

@ExtendWith(MockitoExtension.class)
class TurAgentHandoffServiceTest {

    @Mock private TurAgentChatExecutor executor;
    @Mock private TurAIAgentRepository agentRepository;
    @Mock private TurLLMInstanceRepository llmRepository;

    private TurAgentHandoffService service() {
        return new TurAgentHandoffService(executor, agentRepository, llmRepository);
    }

    private static TurAIAgent agent(String id, String title) {
        TurAIAgent a = new TurAIAgent();
        a.setId(id);
        a.setTitle(title);
        a.setEnabled(1);
        return a;
    }

    private TurAIAgent router(boolean enabled, String specialistCsv) {
        TurAIAgent r = agent("router", "Router");
        r.setAgentHandoffEnabled(enabled);
        r.setSpecialistAgentIds(specialistCsv);
        return r;
    }

    @Test
    void disabledFlagYieldsNoTool() {
        TurAIAgent r = router(false, "spec1");
        assertThat(service().isEnabled(r)).isFalse();
        assertThat(service().buildToolCallbacks(r)).isEmpty();
    }

    @Test
    void enabledButEmptyAllowlistYieldsNoTool() {
        TurAIAgent r = router(true, "  ");
        assertThat(service().isEnabled(r)).isFalse();
        assertThat(service().buildToolCallbacks(r)).isEmpty();
    }

    @Test
    void buildsOneToolWhenSpecialistResolves() {
        when(agentRepository.findById("spec1")).thenReturn(Optional.of(agent("spec1", "Returns")));
        TurAIAgent r = router(true, "spec1");
        assertThat(service().isEnabled(r)).isTrue();
        assertThat(service().buildToolCallbacks(r)).hasSize(1);
    }

    @Test
    void skipsSelfAndDisabledAndMissingSpecialists() {
        // "router" = self (skip), "ghost" = missing, "off" = disabled, "spec1" = ok.
        lenient().when(agentRepository.findById("ghost")).thenReturn(Optional.empty());
        TurAIAgent off = agent("off", "Off");
        off.setEnabled(0);
        lenient().when(agentRepository.findById("off")).thenReturn(Optional.of(off));
        when(agentRepository.findById("spec1")).thenReturn(Optional.of(agent("spec1", "Returns")));

        TurAIAgent r = router(true, "router, ghost, off, spec1");
        assertThat(service().isEnabled(r)).isTrue();
        assertThat(service().buildToolCallbacks(r)).hasSize(1);
    }
}
