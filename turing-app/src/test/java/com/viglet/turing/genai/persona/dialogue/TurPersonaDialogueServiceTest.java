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

import java.util.List;
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
import com.viglet.turing.genai.persona.dialogue.TurDialogueEvent.TurDialogueEventType;
import com.viglet.turing.persistence.model.llm.TurLLMInstance;
import com.viglet.turing.persistence.model.persona.TurPersona;
import com.viglet.turing.persistence.model.persona.TurPersonaKind;
import com.viglet.turing.persistence.repository.llm.TurLLMInstanceRepository;
import com.viglet.turing.persistence.repository.persona.TurPersonaRepository;
import com.viglet.turing.tenant.TurInfraTenantScope;

import reactor.core.publisher.Flux;

/**
 * Orchestration tests for the streaming persona↔persona dialogue driver (Block
 * AI / §XXXII.8, T585; N-persona + streaming, T604). The chat executor is mocked
 * to echo the active speaker + its incoming turn, so we can assert the
 * round-robin over N personas, the message threading (each persona replies to
 * the previous utterance), the turn budget, the streamed event sequence, and the
 * guard rails — all without a Spring context or a real LLM.
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

    private void echoExecutor() {
        // Echo the active speaker and the incoming user turn so we can trace both
        // the round-robin and the "reply to the previous message" threading.
        when(agentChatExecutor.execute(any(TurAgentChatRequest.class))).thenAnswer(inv -> {
            TurAgentChatRequest req = inv.getArgument(0);
            String name = req.agent().getDefaultPersona().getName();
            String incoming = req.history().get(0).content();
            return Flux.just(new ChatResponse("assistant", name + "->" + incoming, "token"));
        });
    }

    private List<TurDialogueEvent> collect(Flux<TurDialogueEvent> flux) {
        return flux.collectList().block();
    }

    @Test
    void roundRobinsThreePersonasThreadsMessagesAndEmitsDone() {
        when(personaRepository.findById("A")).thenReturn(Optional.of(speaker("A", "Alice")));
        when(personaRepository.findById("B")).thenReturn(Optional.of(speaker("B", "Bob")));
        when(personaRepository.findById("C")).thenReturn(Optional.of(speaker("C", "Cleo")));
        TurLLMInstance llm = llm();
        when(llmInstanceRepository.findById("llm1")).thenReturn(Optional.of(llm));
        when(tenantScope.isVisibleToTenant(llm)).thenReturn(true);
        echoExecutor();

        List<TurDialogueEvent> events = collect(
                service.stream("weather", List.of("A", "B", "C"), "llm1", 4));

        // 4 TURN events + 1 DONE.
        assertThat(events).hasSize(5);
        List<TurDialogueEvent> turns = events.stream()
                .filter(e -> e.type() == TurDialogueEventType.TURN).toList();
        assertThat(turns).extracting(TurDialogueEvent::personaId)
                .containsExactly("A", "B", "C", "A"); // round-robin over 3
        // Threading: A opens on the topic; each subsequent speaker replies to the
        // previous utterance.
        assertThat(turns.get(0).content()).isEqualTo("Alice->weather");
        assertThat(turns.get(1).content()).isEqualTo("Bob->Alice->weather");
        assertThat(turns.get(2).content()).isEqualTo("Cleo->Bob->Alice->weather");
        TurDialogueEvent done = events.get(events.size() - 1);
        assertThat(done.type()).isEqualTo(TurDialogueEventType.DONE);
        assertThat(done.index()).isEqualTo(4);
    }

    @Test
    void defaultsToTenTurnsAndClampsToMax() {
        when(personaRepository.findById("A")).thenReturn(Optional.of(speaker("A", "Alice")));
        when(personaRepository.findById("B")).thenReturn(Optional.of(speaker("B", "Bob")));
        TurLLMInstance llm = llm();
        when(llmInstanceRepository.findById("llm1")).thenReturn(Optional.of(llm));
        when(tenantScope.isVisibleToTenant(llm)).thenReturn(true);
        echoExecutor();

        assertThat(turnCount(service.stream("t", List.of("A", "B"), "llm1", null)))
                .isEqualTo(TurPersonaDialogueService.DEFAULT_TURNS);
        assertThat(turnCount(service.stream("t", List.of("A", "B"), "llm1", 999))).isEqualTo(300);
        assertThat(turnCount(service.stream("t", List.of("A", "B"), "llm1", 1))).isEqualTo(2);
    }

    private long turnCount(Flux<TurDialogueEvent> flux) {
        return collect(flux).stream().filter(e -> e.type() == TurDialogueEventType.TURN).count();
    }

    @Test
    void emitsErrorEventAndStopsWhenATurnFails() {
        when(personaRepository.findById("A")).thenReturn(Optional.of(speaker("A", "Alice")));
        when(personaRepository.findById("B")).thenReturn(Optional.of(speaker("B", "Bob")));
        TurLLMInstance llm = llm();
        when(llmInstanceRepository.findById("llm1")).thenReturn(Optional.of(llm));
        when(tenantScope.isVisibleToTenant(llm)).thenReturn(true);
        when(agentChatExecutor.execute(any(TurAgentChatRequest.class)))
                .thenReturn(Flux.error(new IllegalStateException("provider down")));

        List<TurDialogueEvent> events = collect(
                service.stream("weather", List.of("A", "B"), "llm1", 4));

        assertThat(events).hasSize(1);
        assertThat(events.get(0).type()).isEqualTo(TurDialogueEventType.ERROR);
        assertThat(events.get(0).error()).contains("provider down");
    }

    @Test
    void rejectsBlankTopic() {
        assertThatThrownBy(() -> service.stream("  ", List.of("A", "B"), "llm1", 4))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void rejectsFewerThanTwoPersonas() {
        assertThatThrownBy(() -> service.stream("weather", List.of("A"), "llm1", 4))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void rejectsAudienceOnlySpeaker() {
        TurPersona audience = speaker("A", "Reader");
        audience.setPersonaKind(TurPersonaKind.AUDIENCE);
        when(personaRepository.findById("A")).thenReturn(Optional.of(audience));

        assertThatThrownBy(() -> service.stream("weather", List.of("A", "B"), "llm1", 4))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void rejectsMissingPersona() {
        when(personaRepository.findById("A")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.stream("weather", List.of("A", "B"), "llm1", 4))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void rejectsInvisibleLlm() {
        when(personaRepository.findById("A")).thenReturn(Optional.of(speaker("A", "Alice")));
        when(personaRepository.findById("B")).thenReturn(Optional.of(speaker("B", "Bob")));
        TurLLMInstance llm = llm();
        lenient().when(llmInstanceRepository.findById("llm1")).thenReturn(Optional.of(llm));
        when(tenantScope.isVisibleToTenant(llm)).thenReturn(false);

        assertThatThrownBy(() -> service.stream("weather", List.of("A", "B"), "llm1", 4))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }
}
