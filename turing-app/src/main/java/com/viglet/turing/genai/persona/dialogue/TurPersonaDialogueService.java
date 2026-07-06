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
import java.util.ArrayList;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.viglet.turing.genai.TurAgentChatExecutor;
import com.viglet.turing.genai.TurAgentChatRequest;
import com.viglet.turing.genai.persona.dialogue.TurDialogueTranscript.TurPersonaRef;
import com.viglet.turing.persistence.model.agent.TurAIAgent;
import com.viglet.turing.persistence.model.llm.TurLLMInstance;
import com.viglet.turing.persistence.model.persona.TurPersona;
import com.viglet.turing.persistence.repository.llm.TurLLMInstanceRepository;
import com.viglet.turing.persistence.repository.persona.TurPersonaRepository;
import com.viglet.turing.tenant.TurInfraTenantScope;

import lombok.extern.slf4j.Slf4j;

/**
 * Automatic persona↔persona conversation — Block AI / §XXXII.8 (T585).
 *
 * <p>Given a <b>topic</b> and <b>two speaker personas</b>, this orchestrates a
 * bounded, turn-by-turn dialogue by driving the same shared
 * {@link TurAgentChatExecutor} the persona-chat MVP (T578) uses — one transient,
 * never-persisted {@link TurAIAgent} shell per turn, carrying the active
 * speaker's voice via {@code setDefaultPersona}. The topic is the opening user
 * turn to persona&nbsp;A; from then on each persona receives the other's last
 * utterance as its user turn and replies in character, alternating until the
 * turn budget is spent.
 *
 * <p>Two rules keep the conversation flowing (both configurable at the call
 * site): a shared <b>base system prompt</b> instructs every speaker to always
 * end its reply with a question to the other participant (so the dialogue never
 * stalls) — the persona's own voice is still fused <em>on top</em> of it by
 * {@link com.viglet.turing.genai.TurChatPromptAssembler}; and the number of
 * <b>turns</b> is bounded (default {@value #DEFAULT_TURNS}).
 *
 * <p>Each turn is <b>stateless</b> ({@code conversationId = null},
 * {@code flowId = null}) exactly like T578, so no flow-state / analytics /
 * chat-memory rows are written and a fresh agent never
 * {@code LazyInitializationException}s. Unlike the chat MVP no native tools are
 * loaded — a dialogue is a pure voice exercise, and tool loops would only add
 * latency and break character. The executor runs in its default {@code CALL}
 * mode, emitting each utterance as a single {@code "token"} event that we
 * collect and join.
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
    private static final int MAX_TURNS = 20;
    /** Per-turn upper bound on the blocking LLM call. */
    private static final Duration TURN_TIMEOUT = Duration.ofSeconds(120);

    /**
     * Base system prompt shared by every speaker. The persona's own voice is
     * fused on top of this by the prompt assembler, so it augments — not
     * replaces — the persona. The mandatory trailing question is what keeps the
     * exchange self-sustaining.
     */
    private static final String DIALOGUE_DIRECTIVE = """
            You are one of two personas having a live, turn-by-turn conversation about a topic. \
            Stay fully in character and speak in your own distinct voice. \
            Reply in the same language as the conversation, and keep each reply short and natural \
            (at most a few sentences). \
            IMPORTANT: every single time you reply you MUST end with a direct question addressed to \
            the other participant, so the dialogue always keeps going.""";

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
     * Run a bounded dialogue between {@code personaAId} and {@code personaBId}
     * about {@code topic} using {@code llmInstanceId}.
     *
     * @param topic          the subject that seeds persona A's opening turn
     * @param personaAId     the persona that speaks first (must be a speaker)
     * @param personaBId     the persona that replies (must be a speaker)
     * @param llmInstanceId  the LLM instance (validated against the tenant scope)
     * @param turnsRequested the desired number of turns; null → {@link #DEFAULT_TURNS},
     *                       clamped to [{@value #MIN_TURNS}, {@value #MAX_TURNS}]
     * @return the transcript (partial + flagged if a turn fails mid-dialogue)
     */
    public TurDialogueTranscript run(String topic, String personaAId, String personaBId,
            String llmInstanceId, Integer turnsRequested) {

        if (topic == null || topic.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A topic is required");
        }
        TurPersona personaA = loadSpeaker(personaAId);
        TurPersona personaB = loadSpeaker(personaBId);
        TurLLMInstance llm = loadLlm(llmInstanceId);
        int turns = clampTurns(turnsRequested);

        log.info("[PersonaDialogue] '{}' <-> '{}' on '{}' for {} turns via {}",
                personaA.getName(), personaB.getName(), topic, turns, llm.getTitle());

        List<TurDialogueTurn> transcript = new ArrayList<>();
        TurPersona[] speakers = {personaA, personaB};
        String incoming = topic;
        boolean llmError = false;
        String error = null;

        for (int i = 0; i < turns; i++) {
            TurPersona speaker = speakers[i % 2];
            String utterance;
            try {
                utterance = runTurn(speaker, llm, incoming);
            } catch (Exception e) {
                log.error("[PersonaDialogue] turn {} ({}) failed: {}", i, speaker.getName(), e.getMessage(), e);
                llmError = true;
                error = e.getMessage();
                break;
            }
            if (utterance == null || utterance.isBlank()) {
                llmError = true;
                error = "The model returned an empty turn";
                break;
            }
            transcript.add(new TurDialogueTurn(speaker.getId(), speaker.getName(), utterance));
            incoming = utterance;
        }

        return new TurDialogueTranscript(topic,
                new TurPersonaRef(personaA.getId(), personaA.getName()),
                new TurPersonaRef(personaB.getId(), personaB.getName()),
                turns, transcript, llmError, error);
    }

    /** One utterance: drive the executor for {@code speaker} replying to {@code incoming}. */
    private String runTurn(TurPersona speaker, TurLLMInstance llm, String incoming) {
        TurAIAgent agent = buildTransientAgent(speaker);
        List<TurAgentChatExecutor.ChatMessageItem> history =
                List.of(new TurAgentChatExecutor.ChatMessageItem("user", incoming));
        // Default CALL mode emits the full assistant text as a single "token"
        // event (side events like sources/options are filtered out); collect +
        // join into one utterance. Stateless: conversationId + flowId null.
        List<String> parts = agentChatExecutor.execute(
                        new TurAgentChatRequest(agent, llm, history, null, null, null, null))
                .filter(r -> "token".equals(r.type()))
                .map(TurAgentChatExecutor.ChatResponse::content)
                .collectList()
                .block(TURN_TIMEOUT);
        return parts == null ? null : String.join("", parts).trim();
    }

    /**
     * The never-persisted agent shell that carries a speaker's voice. Unlike the
     * chat MVP it sets a base {@link #DIALOGUE_DIRECTIVE} system prompt (the
     * persona is fused on top) and loads no native tools.
     */
    private TurAIAgent buildTransientAgent(TurPersona persona) {
        TurAIAgent agent = new TurAIAgent();
        agent.setTitle("Persona: " + persona.getName());
        agent.setEnabled(1);
        agent.setSystemPrompt(DIALOGUE_DIRECTIVE);
        agent.setDefaultPersona(persona);
        return agent;
    }

    private TurPersona loadSpeaker(String id) {
        if (id == null || id.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Two persona ids are required");
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
