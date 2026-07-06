/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.persona.dialogue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import com.viglet.turing.genai.TurAgentChatExecutor;
import com.viglet.turing.genai.TurAgentChatExecutor.ChatResponse;
import com.viglet.turing.genai.TurAgentChatRequest;
import com.viglet.turing.persistence.model.llm.TurLLMInstance;
import com.viglet.turing.persistence.model.persona.TurPersona;
import com.viglet.turing.persistence.model.persona.TurPersonaKind;
import com.viglet.turing.persistence.repository.llm.TurLLMInstanceRepository;
import com.viglet.turing.persistence.repository.persona.TurPersonaRepository;
import com.viglet.turing.tenant.TurInfraTenantScope;

import reactor.core.publisher.Flux;

/**
 * Orchestration tests for the persona↔persona dialogue driver (Block AI /
 * §XXXII.8, T585). The chat executor is mocked to echo the active speaker + its
 * incoming turn, so we can assert the alternation, the message threading
 * (each persona replies to the other's last utterance), the turn budget, and
 * the guard rails — all without a Spring context or a real LLM.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@ExtendWith(MockitoExtension.class)
class TurPersonaDialogueServiceTest {

    @Mock
    private TurPersonaRepository personaRepository;
    @Mock
    private TurLLMInstanceRepository llmInstanceRepository;
    @Mock
    private TurInfraTenantScope tenantScope;
    @Mock
    private TurAgentChatExecutor agentChatExecutor;

    @InjectMocks
    private TurPersonaDialogueService service;

    private TurPersona speaker(String id, String name) {
        TurPersona p = new TurPersona();
        p.setId(id);
        p.setName(name);
        p.setEnabled(1);
        p.setPersonaKind(TurPersonaKind.SPEAKER);
        return p;
    }

    private TurLLMInstance llm() {
        TurLLMInstance llm = new TurLLMInstance();
        llm.setId("llm1");
        llm.setTitle("Test model");
        return llm;
    }

    private void wireHappyPath(TurPersona a, TurPersona b, TurLLMInstance llm) {
        when(personaRepository.findById("A")).thenReturn(Optional.of(a));
        when(personaRepository.findById("B")).thenReturn(Optional.of(b));
        when(llmInstanceRepository.findById("llm1")).thenReturn(Optional.of(llm));
        when(tenantScope.isVisibleToTenant(llm)).thenReturn(true);
        // Echo the active speaker and the incoming user turn so we can trace both
        // the alternation and the "reply to the other's last message" threading.
        when(agentChatExecutor.execute(any(TurAgentChatRequest.class))).thenAnswer(inv -> {
            TurAgentChatRequest req = inv.getArgument(0);
            String name = req.agent().getDefaultPersona().getName();
            String incoming = req.history().get(0).content();
            return Flux.just(new ChatResponse("assistant", name + "->" + incoming, "token"));
        });
    }

    @Test
    void alternatesSpeakersThreadsMessagesAndHonoursTurnBudget() {
        TurPersona a = speaker("A", "Alice");
        TurPersona b = speaker("B", "Bob");
        TurLLMInstance llm = llm();
        wireHappyPath(a, b, llm);

        TurDialogueTranscript result = service.run("weather", "A", "B", "llm1", 4);

        assertThat(result.turns()).isEqualTo(4);
        assertThat(result.llmError()).isFalse();
        assertThat(result.transcript()).hasSize(4);
        // Alternation A, B, A, B.
        assertThat(result.transcript()).extracting(TurDialogueTurn::personaId)
                .containsExactly("A", "B", "A", "B");
        // Threading: A opens on the topic; each subsequent speaker replies to the
        // previous utterance.
        assertThat(result.transcript().get(0).content()).isEqualTo("Alice->weather");
        assertThat(result.transcript().get(1).content()).isEqualTo("Bob->Alice->weather");
        assertThat(result.transcript().get(2).content()).isEqualTo("Alice->Bob->Alice->weather");
        assertThat(result.personaA().name()).isEqualTo("Alice");
        assertThat(result.personaB().name()).isEqualTo("Bob");
    }

    @Test
    void defaultsToTenTurnsAndClampsToTwenty() {
        TurPersona a = speaker("A", "Alice");
        TurPersona b = speaker("B", "Bob");
        wireHappyPath(a, b, llm());

        assertThat(service.run("t", "A", "B", "llm1", null).turns())
                .isEqualTo(TurPersonaDialogueService.DEFAULT_TURNS);
        assertThat(service.run("t", "A", "B", "llm1", 999).turns()).isEqualTo(20);
        assertThat(service.run("t", "A", "B", "llm1", 1).turns()).isEqualTo(2);
    }

    @Test
    void stopsEarlyAndFlagsWhenATurnEmits() {
        TurPersona a = speaker("A", "Alice");
        TurPersona b = speaker("B", "Bob");
        TurLLMInstance llm = llm();
        when(personaRepository.findById("A")).thenReturn(Optional.of(a));
        when(personaRepository.findById("B")).thenReturn(Optional.of(b));
        when(llmInstanceRepository.findById("llm1")).thenReturn(Optional.of(llm));
        when(tenantScope.isVisibleToTenant(llm)).thenReturn(true);
        when(agentChatExecutor.execute(any(TurAgentChatRequest.class)))
                .thenReturn(Flux.error(new IllegalStateException("provider down")));

        TurDialogueTranscript result = service.run("weather", "A", "B", "llm1", 4);

        assertThat(result.llmError()).isTrue();
        assertThat(result.transcript()).isEmpty();
        assertThat(result.error()).contains("provider down");
    }

    @Test
    void rejectsBlankTopic() {
        assertThatThrownBy(() -> service.run("  ", "A", "B", "llm1", 4))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void rejectsAudienceOnlySpeaker() {
        TurPersona audience = speaker("A", "Reader");
        audience.setPersonaKind(TurPersonaKind.AUDIENCE);
        when(personaRepository.findById("A")).thenReturn(Optional.of(audience));

        assertThatThrownBy(() -> service.run("weather", "A", "B", "llm1", 4))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void rejectsMissingPersona() {
        when(personaRepository.findById("A")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.run("weather", "A", "B", "llm1", 4))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void rejectsInvisibleLlm() {
        TurPersona a = speaker("A", "Alice");
        TurPersona b = speaker("B", "Bob");
        TurLLMInstance llm = llm();
        when(personaRepository.findById("A")).thenReturn(Optional.of(a));
        when(personaRepository.findById("B")).thenReturn(Optional.of(b));
        lenient().when(llmInstanceRepository.findById("llm1")).thenReturn(Optional.of(llm));
        when(tenantScope.isVisibleToTenant(llm)).thenReturn(false);

        assertThatThrownBy(() -> service.run("weather", "A", "B", "llm1", 4))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }
}
