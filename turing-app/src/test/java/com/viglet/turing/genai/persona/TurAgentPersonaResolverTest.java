/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.persona;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.viglet.turing.persistence.model.agent.TurAIAgent;
import com.viglet.turing.persistence.model.agent.TurChatFlowState;
import com.viglet.turing.persistence.model.persona.TurPersona;
import com.viglet.turing.persistence.model.persona.TurPersonaKind;
import com.viglet.turing.persistence.repository.agent.TurChatFlowStateRepository;

/**
 * Unit tests for {@link TurAgentPersonaResolver}, focused on the T633
 * per-request persona override (the anonymous public SN chat path) and its
 * precedence relative to a flow-state override.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@ExtendWith(MockitoExtension.class)
class TurAgentPersonaResolverTest {

    private static final String CONV = "conv-1";
    private static final String AGENT_ID = "agent-1";

    @Mock
    private TurChatFlowStateRepository flowStateRepository;

    private TurAgentPersonaResolver resolver;

    private TurPersona speaker;
    private TurPersona other;
    private TurPersona audienceOnly;
    private TurPersona defaultPersona;
    private TurAIAgent agent;

    @BeforeEach
    void setUp() {
        resolver = new TurAgentPersonaResolver(flowStateRepository);
        speaker = persona("p-speaker", TurPersonaKind.SPEAKER);
        other = persona("p-other", TurPersonaKind.SPEAKER);
        audienceOnly = persona("p-audience", TurPersonaKind.AUDIENCE);
        defaultPersona = persona("p-default", TurPersonaKind.SPEAKER);

        agent = new TurAIAgent();
        agent.setId(AGENT_ID);
        agent.setPersonas(Set.of(speaker, other, audienceOnly, defaultPersona));
        agent.setDefaultPersona(defaultPersona);
        // No flow state by default — the anonymous RAG turn has none.
        lenient().when(flowStateRepository
                .findByConversationIdAndFlow_TurAIAgent_Id(CONV, AGENT_ID))
                .thenReturn(List.of());
    }

    @Test
    void requestPersonaInCatalogIsHonoured() {
        assertThat(resolver.resolve(agent, CONV, "p-speaker")).isEqualTo(speaker);
    }

    @Test
    void unknownRequestPersonaFallsBackToDefault() {
        assertThat(resolver.resolve(agent, CONV, "does-not-exist")).isEqualTo(defaultPersona);
    }

    @Test
    void blankOrNullRequestPersonaFallsBackToDefault() {
        assertThat(resolver.resolve(agent, CONV, null)).isEqualTo(defaultPersona);
        assertThat(resolver.resolve(agent, CONV, "   ")).isEqualTo(defaultPersona);
    }

    @Test
    void audienceOnlyRequestPersonaIsRejectedAsVoice() {
        // In catalog but not usable as a speaker → asSpeaker guard drops it to
        // the LLM's default voice (null), never composes an empty voice.
        assertThat(resolver.resolve(agent, CONV, "p-audience")).isNull();
    }

    @Test
    void flowOverrideWinsOverRequestPersona() {
        TurChatFlowState state = new TurChatFlowState();
        state.setConversationId(CONV);
        state.setVariablesJson("{\"__activePersonaId\":\"p-other\"}");
        when(flowStateRepository.findByConversationIdAndFlow_TurAIAgent_Id(CONV, AGENT_ID))
                .thenReturn(List.of(state));

        // A flow explicitly switched persona → it stays authoritative even
        // when the request asks for a different (valid) persona.
        assertThat(resolver.resolve(agent, CONV, "p-speaker")).isEqualTo(other);
    }

    @Test
    void twoArgResolveStillReturnsDefault() {
        assertThat(resolver.resolve(agent, CONV)).isEqualTo(defaultPersona);
    }

    @Test
    void validateCatalogPersonaIdReturnsIdOnlyForCatalogMembers() {
        assertThat(resolver.validateCatalogPersonaId(agent, "p-speaker")).isEqualTo("p-speaker");
        assertThat(resolver.validateCatalogPersonaId(agent, "does-not-exist")).isNull();
        assertThat(resolver.validateCatalogPersonaId(agent, null)).isNull();
        assertThat(resolver.validateCatalogPersonaId(null, "p-speaker")).isNull();
    }

    private static TurPersona persona(String id, TurPersonaKind kind) {
        TurPersona p = new TurPersona();
        p.setId(id);
        p.setName(id);
        p.setPersonaKind(kind);
        return p;
    }
}
