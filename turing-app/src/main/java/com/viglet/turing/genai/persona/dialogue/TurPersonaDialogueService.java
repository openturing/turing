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

import java.time.Duration;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.viglet.turing.genai.TurAgentChatExecutor;
import com.viglet.turing.genai.TurAgentChatRequest;
import com.viglet.turing.persistence.model.agent.TurAIAgent;
import com.viglet.turing.persistence.model.llm.TurLLMInstance;
import com.viglet.turing.persistence.model.persona.TurPersona;
import com.viglet.turing.persistence.repository.llm.TurLLMInstanceRepository;
import com.viglet.turing.persistence.repository.persona.TurPersonaRepository;
import com.viglet.turing.tenant.TurInfraTenantScope;

import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

/**
 * Automatic persona↔persona conversation — Block AI / §XXXII.8 (T585; N-persona
 * + streaming in T604).
 *
 * <p>Given a <b>topic</b> and <b>two or more speaker personas</b>, this
 * orchestrates a bounded, <b>round-robin</b> dialogue by driving the same shared
 * {@link TurAgentChatExecutor} the persona-chat MVP (T578) uses — one transient,
 * never-persisted {@link TurAIAgent} shell per turn, carrying the active
 * speaker's voice via {@code setDefaultPersona}. The topic is the opening user
 * turn to the first persona; from then on each persona receives the previous
 * speaker's utterance as its user turn and replies in character, cycling through
 * the roster (speaker {@code i % N}) until the turn budget is spent.
 *
 * <p>Turns are streamed as {@link TurDialogueEvent}s so the client renders the
 * conversation <b>live</b> instead of waiting for the whole transcript: each
 * completed utterance is one {@code TURN} event, followed by a {@code DONE}
 * (or {@code ERROR} + the partial thread). The blocking per-turn LLM calls run
 * on a bounded-elastic worker so the SSE thread is never parked.
 *
 * <p>Two rules keep the conversation flowing: a shared <b>base system prompt</b>
 * instructs every speaker to always end its reply with a question to the other
 * participant(s) (so the dialogue never stalls) — the persona's own voice is
 * still fused <em>on top</em> of it by
 * {@link com.viglet.turing.genai.TurChatPromptAssembler}; and the number of
 * <b>turns</b> is bounded (default {@value #DEFAULT_TURNS}).
 *
 * <p>Each turn is <b>stateless</b> ({@code conversationId = null},
 * {@code flowId = null}) exactly like T578, with no native tools (a dialogue is
 * a pure voice exercise). The executor runs in its default {@code CALL} mode,
 * emitting each utterance as a single {@code "token"} event that we collect and
 * join.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Slf4j
@Service
public class TurPersonaDialogueService {

    /** Default number of turns (utterances) when the caller doesn't specify. */
    public static final int DEFAULT_TURNS = 10;
    private static final int MIN_TURNS = 2;
    // Total-utterance safety cap. Raised from 40 so a project's rounds × personas
    // (rounds ≤ 20) isn't clamped — clamping would truncate the final round and
    // leave a persona without a turn (Block AU). Still bounds runaway cost.
    private static final int MAX_TURNS = 300;
    private static final int MIN_PERSONAS = 2;
    /** Per-turn upper bound on the blocking LLM call. */
    private static final Duration TURN_TIMEOUT = Duration.ofSeconds(120);

    /**
     * Base system prompt shared by every speaker. The persona's own voice is
     * fused on top of this by the prompt assembler, so it augments — not
     * replaces — the persona. The mandatory trailing question is what keeps the
     * exchange self-sustaining.
     */
    private static final String DIALOGUE_DIRECTIVE = """
            You are one of several personas having a live, turn-by-turn conversation about a topic. \
            Stay fully in character and speak in your own distinct voice. \
            Reply in the same language as the conversation, and keep each reply short and natural \
            (at most a few sentences). \
            IMPORTANT: every single time you reply you MUST end with a direct question addressed to \
            the other participant(s), so the dialogue always keeps going.""";

    /**
     * Directive for the <b>final</b> utterance. Nobody will reply after it, so a
     * trailing question would dangle — instead the last speaker gives a brief,
     * natural closing remark that wraps up the conversation.
     */
    private static final String DIALOGUE_CLOSING_DIRECTIVE = """
            You are one of several personas having a live, turn-by-turn conversation about a topic. \
            Stay fully in character and speak in your own distinct voice. \
            Reply in the same language as the conversation, and keep each reply short and natural \
            (at most a few sentences). \
            IMPORTANT: this is the FINAL turn of the conversation — do NOT ask any question. \
            Instead, give a brief, natural closing remark that wraps up your view and ends the \
            conversation gracefully.""";

    private final TurPersonaRepository personaRepository;
    private final TurLLMInstanceRepository llmInstanceRepository;
    private final TurInfraTenantScope tenantScope;
    private final TurAgentChatExecutor agentChatExecutor;

    public TurPersonaDialogueService(TurPersonaRepository personaRepository,
            TurLLMInstanceRepository llmInstanceRepository,
            TurInfraTenantScope tenantScope,
            TurAgentChatExecutor agentChatExecutor) {
        this.personaRepository = personaRepository;
        this.llmInstanceRepository = llmInstanceRepository;
        this.tenantScope = tenantScope;
        this.agentChatExecutor = agentChatExecutor;
    }

    /**
     * Stream a bounded round-robin dialogue between {@code personaIds} (two or
     * more) about {@code topic} using {@code llmInstanceId}.
     *
     * <p>Validation is synchronous (throws {@link ResponseStatusException} before
     * the stream starts, so the client gets a proper 4xx); the turn loop then
     * runs on a bounded-elastic worker, emitting one {@code TURN} event per
     * completed utterance and a terminal {@code DONE}/{@code ERROR}.
     *
     * @param topic          the subject that seeds the first persona's opening turn
     * @param personaIds     the ordered roster of speaker personas (size ≥ 2)
     * @param llmInstanceId  the LLM instance (validated against the tenant scope)
     * @param turnsRequested the desired number of turns; null → {@link #DEFAULT_TURNS},
     *                       clamped to [{@value #MIN_TURNS}, {@value #MAX_TURNS}]
     */
    public Flux<TurDialogueEvent> stream(String topic, List<String> personaIds,
            String llmInstanceId, Integer turnsRequested) {

        if (topic == null || topic.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A topic is required");
        }
        if (personaIds == null || personaIds.size() < MIN_PERSONAS) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "At least " + MIN_PERSONAS + " personas are required for a dialogue");
        }
        List<TurPersona> speakers = personaIds.stream().map(this::loadSpeaker).toList();
        TurLLMInstance llm = loadLlm(llmInstanceId);
        int turns = clampTurns(turnsRequested);

        log.info("[PersonaDialogue] {} personas on '{}' for {} turns via {}",
                speakers.size(), topic, turns, llm.getTitle());

        return Flux.<TurDialogueEvent>create(sink -> {
            String incoming = topic;
            for (int i = 0; i < turns; i++) {
                if (sink.isCancelled()) {
                    return;
                }
                TurPersona speaker = speakers.get(i % speakers.size());
                boolean isLast = i == turns - 1;
                String utterance;
                try {
                    utterance = runTurn(speaker, llm, incoming, isLast);
                } catch (Exception e) {
                    log.error("[PersonaDialogue] turn {} ({}) failed: {}", i, speaker.getName(), e.getMessage(), e);
                    sink.next(TurDialogueEvent.error(i, e.getMessage()));
                    sink.complete();
                    return;
                }
                if (utterance == null || utterance.isBlank()) {
                    sink.next(TurDialogueEvent.error(i, "The model returned an empty turn"));
                    sink.complete();
                    return;
                }
                sink.next(TurDialogueEvent.turn(i, speaker.getId(), speaker.getName(), utterance));
                incoming = utterance;
            }
            sink.next(TurDialogueEvent.done(turns));
            sink.complete();
        }).subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * One utterance: drive the executor for {@code speaker} replying to
     * {@code incoming}. {@code isLast} swaps in the closing directive (no trailing
     * question) for the final turn.
     */
    private String runTurn(TurPersona speaker, TurLLMInstance llm, String incoming, boolean isLast) {
        TurAIAgent agent = buildTransientAgent(speaker, isLast);
        List<TurAgentChatExecutor.ChatMessageItem> history =
                List.of(new TurAgentChatExecutor.ChatMessageItem("user", incoming));
        // Default CALL mode emits the full assistant text as a single "token"
        // event (side events like sources/options are filtered out); collect +
        // join into one utterance. Stateless: conversationId + flowId null.
        List<String> parts = agentChatExecutor.execute(
                        new TurAgentChatRequest(agent, llm, history, null, null, null, null, null))
                .filter(r -> "token".equals(r.type()))
                .map(TurAgentChatExecutor.ChatResponse::content)
                .collectList()
                .block(TURN_TIMEOUT);
        return parts == null ? null : String.join("", parts).trim();
    }

    /**
     * The never-persisted agent shell that carries a speaker's voice. Unlike the
     * chat MVP it sets a base {@link #DIALOGUE_DIRECTIVE} system prompt (the
     * persona is fused on top) and loads no native tools. The final turn uses the
     * {@link #DIALOGUE_CLOSING_DIRECTIVE closing} directive so it doesn't end on a
     * dangling question.
     */
    private TurAIAgent buildTransientAgent(TurPersona persona, boolean isLast) {
        TurAIAgent agent = new TurAIAgent();
        agent.setTitle("Persona: " + persona.getName());
        agent.setEnabled(1);
        agent.setSystemPrompt(isLast ? DIALOGUE_CLOSING_DIRECTIVE : DIALOGUE_DIRECTIVE);
        agent.setDefaultPersona(persona);
        return agent;
    }

    private TurPersona loadSpeaker(String id) {
        if (id == null || id.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A persona id is required");
        }
        // @TenantId auto-filters, so a persona from another tenant 404s here too.
        TurPersona persona = personaRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Persona not found: " + id));
        if (persona.getEnabled() != 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Persona is disabled: " + id);
        }
        if (!persona.isUsableAsSpeaker()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Persona '" + id + "' is audience-only and cannot be a speaker");
        }
        return persona;
    }

    private TurLLMInstance loadLlm(String id) {
        if (id == null || id.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "An LLM instance id is required to run a dialogue");
        }
        return llmInstanceRepository.findById(id)
                .filter(tenantScope::isVisibleToTenant)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "LLM instance not found: " + id));
    }

    private int clampTurns(Integer requested) {
        int turns = requested == null ? DEFAULT_TURNS : requested;
        return Math.max(MIN_TURNS, Math.min(MAX_TURNS, turns));
    }
}
