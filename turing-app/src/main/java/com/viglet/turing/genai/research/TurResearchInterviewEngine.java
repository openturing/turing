/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.research;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import com.viglet.turing.genai.TurAgentChatExecutor;
import com.viglet.turing.genai.TurAgentChatExecutor.ChatMessageItem;
import com.viglet.turing.genai.TurAgentChatExecutor.ChatResponse;
import com.viglet.turing.genai.TurAgentChatRequest;
import com.viglet.turing.genai.research.dto.TurResearchTurnDto;
import com.viglet.turing.genai.tool.TurNativeToolService;
import com.viglet.turing.persistence.model.agent.TurAIAgent;
import com.viglet.turing.persistence.model.llm.TurLLMInstance;
import com.viglet.turing.persistence.model.persona.TurPersona;
import com.viglet.turing.persistence.model.research.TurResearchProtocol;
import com.viglet.turing.persistence.model.research.TurResearchStudy;

import lombok.extern.slf4j.Slf4j;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * The interview engine (Block AW / §XLVI.2, T720) — runs one synthetic-user
 * interview for a single persona and returns its transcript. The three protocols
 * ({@link TurResearchProtocol}) all reuse the <b>T578 persona-chat executor
 * unchanged</b> to produce each answer: a transient, never-persisted
 * {@link TurAIAgent} carries the persona (fused via {@code setDefaultPersona}) and
 * the generic native tool loadout, and grounding (T718) is applied automatically
 * inside the executor's prompt-assembly pipeline. No new chat engine is forked.
 *
 * <p>Question selection differs per protocol:
 * <ul>
 *   <li>{@code CUSTOM_SCRIPT} — the author's fixed question list, asked verbatim
 *       in order;</li>
 *   <li>{@code DYNAMIC_SCRIPT} — a goal-driven <em>interviewer sub-loop</em>: a
 *       second, tool-less transient agent proposes the next question given the
 *       study goal/hypothesis and the transcript so far, until it replies
 *       {@code DONE} or the {@code maxQuestions} cap is reached;</li>
 *   <li>{@code CONCEPT_TEST} — the same adaptive loop, seeded with the proposed
 *       concept the participant reacts to.</li>
 * </ul>
 *
 * <p>Each executor turn is stateless ({@code conversationId = flowId = null}): no
 * flow-state, analytics or chat-memory rows are written. The engine is
 * synchronous (blocks on the single-emission executor {@link reactor.core.publisher.Flux});
 * the runner (T721) invokes it bounded-parallel on a worker pool.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Slf4j
@Service
public class TurResearchInterviewEngine {

    /** Absolute ceiling on adaptive follow-ups regardless of the study cap. */
    private static final int MAX_QUESTIONS_HARD_CAP = 20;

    /** Max wait for one executor turn before giving up on that answer. */
    private static final Duration TURN_TIMEOUT = Duration.ofSeconds(120);

    private static final JsonMapper MAPPER = JsonMapper.builder().build();
    private static final TypeReference<List<String>> QUESTIONS_TYPE = new TypeReference<>() {};

    private final TurAgentChatExecutor executor;
    private final TurNativeToolService nativeToolService;

    public TurResearchInterviewEngine(TurAgentChatExecutor executor,
            TurNativeToolService nativeToolService) {
        this.executor = executor;
        this.nativeToolService = nativeToolService;
    }

    /**
     * Conduct one interview and return its transcript. Never returns {@code null};
     * an interview that produces no turns (e.g. an empty custom script) yields an
     * empty list. Propagates a {@link RuntimeException} only on an unexpected
     * failure — the runner catches it and records the interview as {@code FAILED}.
     */
    public List<TurResearchTurnDto> interview(TurResearchStudy study, TurPersona persona,
            TurLLMInstance llm) {
        TurResearchProtocol protocol = study.getProtocol() == null
                ? TurResearchProtocol.DYNAMIC_SCRIPT
                : study.getProtocol();
        return switch (protocol) {
            case CUSTOM_SCRIPT -> runCustomScript(study, persona, llm);
            case CONCEPT_TEST, DYNAMIC_SCRIPT -> runAdaptive(study, persona, llm, protocol);
        };
    }

    // ---- protocols ---------------------------------------------------------

    private List<TurResearchTurnDto> runCustomScript(TurResearchStudy study, TurPersona persona,
            TurLLMInstance llm) {
        List<String> questions = parseQuestions(study.getQuestionsJson());
        TurAIAgent personaAgent = buildPersonaAgent(persona);
        List<TurResearchTurnDto> turns = new ArrayList<>(questions.size());
        int index = 0;
        for (String question : questions) {
            if (StringUtils.isBlank(question)) {
                continue;
            }
            String answer = askPersona(personaAgent, llm, turns, question.trim());
            turns.add(new TurResearchTurnDto(index++, question.trim(), answer));
        }
        return turns;
    }

    private List<TurResearchTurnDto> runAdaptive(TurResearchStudy study, TurPersona persona,
            TurLLMInstance llm, TurResearchProtocol protocol) {
        int cap = Math.min(Math.max(study.getMaxQuestions(), 1), MAX_QUESTIONS_HARD_CAP);
        TurAIAgent personaAgent = buildPersonaAgent(persona);
        TurAIAgent interviewerAgent = buildInterviewerAgent();
        String interviewerSystem = interviewerSystemPrompt(study, protocol);

        List<TurResearchTurnDto> turns = new ArrayList<>(cap);
        for (int index = 0; index < cap; index++) {
            String question = nextQuestion(interviewerAgent, interviewerSystem, llm, turns);
            if (StringUtils.isBlank(question) || isDone(question)) {
                break;
            }
            String answer = askPersona(personaAgent, llm, turns, question);
            turns.add(new TurResearchTurnDto(index, question, answer));
            if (StringUtils.isBlank(answer)) {
                break;
            }
        }
        return turns;
    }

    // ---- executor turns ----------------------------------------------------

    /** Ask the interviewer LLM for the next question given the transcript so far. */
    private String nextQuestion(TurAIAgent interviewerAgent, String systemPrompt,
            TurLLMInstance llm, List<TurResearchTurnDto> turns) {
        String userPrompt = interviewerUserPrompt(turns);
        List<ChatMessageItem> history = List.of(new ChatMessageItem("user", userPrompt));
        String raw = collectAnswer(interviewerAgent, llm, history, systemPrompt);
        return StringUtils.trimToEmpty(stripQuotes(raw));
    }

    /** Ask the persona-fused agent the current question, carrying prior turns as context. */
    private String askPersona(TurAIAgent personaAgent, TurLLMInstance llm,
            List<TurResearchTurnDto> priorTurns, String question) {
        List<ChatMessageItem> history = new ArrayList<>(priorTurns.size() * 2 + 1);
        for (TurResearchTurnDto turn : priorTurns) {
            history.add(new ChatMessageItem("user", turn.question()));
            history.add(new ChatMessageItem("assistant", turn.answer()));
        }
        history.add(new ChatMessageItem("user", question));
        return collectAnswer(personaAgent, llm, history, null);
    }

    /**
     * Drive the shared executor for one turn and concatenate the assistant's text
     * emissions. The executor is call-not-stream (single emission per turn), so a
     * blocking collect is the intended consumption for a headless caller.
     */
    private String collectAnswer(TurAIAgent agent, TurLLMInstance llm,
            List<ChatMessageItem> history, String systemPromptOverride) {
        List<ChatResponse> out = executor.execute(new TurAgentChatRequest(agent, llm, history,
                        systemPromptOverride, null, null, null, null))
                .filter(r -> "assistant".equals(r.role()) && isText(r.type()))
                .collectList()
                .block(TURN_TIMEOUT);
        if (out == null || out.isEmpty()) {
            return "";
        }
        return out.stream()
                .map(ChatResponse::content)
                .filter(StringUtils::isNotBlank)
                .collect(Collectors.joining())
                .trim();
    }

    // ---- transient agents --------------------------------------------------

    /**
     * The never-persisted persona shell — identical in spirit to
     * {@code TurPersonaChatAPI#buildTransientAgent}: the full native tool loadout
     * and the persona fused so the executor's persona resolver + grounding
     * contributor apply. Any persona kind may answer (the {@code isUsableAsSpeaker}
     * guard lives in the persona-chat REST layer, not the executor).
     */
    private TurAIAgent buildPersonaAgent(TurPersona persona) {
        TurAIAgent agent = new TurAIAgent();
        agent.setTitle("Research participant: " + persona.getName());
        agent.setEnabled(1);
        agent.setNativeTools(allNativeToolNamesCsv());
        agent.setDefaultPersona(persona);
        return agent;
    }

    /** The interviewer shell — no persona, no tools; a plain LLM turn. */
    private TurAIAgent buildInterviewerAgent() {
        TurAIAgent agent = new TurAIAgent();
        agent.setTitle("Research interviewer");
        agent.setEnabled(1);
        agent.setNativeTools("");
        return agent;
    }

    private String allNativeToolNamesCsv() {
        return nativeToolService.getAllTools().stream()
                .map(TurNativeToolService.NativeToolDescriptor::name)
                .collect(Collectors.joining(","));
    }

    // ---- prompt building ---------------------------------------------------

    private String interviewerSystemPrompt(TurResearchStudy study, TurResearchProtocol protocol) {
        StringBuilder sb = new StringBuilder();
        sb.append("You are an expert UX researcher conducting a one-on-one interview with a ")
                .append("synthetic user. Your job is to elicit honest, concrete, useful insight.\n\n");
        appendIfPresent(sb, "Research goal", study.getGoal());
        appendIfPresent(sb, "Hypothesis to probe", study.getHypothesis());
        if (protocol == TurResearchProtocol.CONCEPT_TEST) {
            appendIfPresent(sb, "Concept the participant is reacting to", study.getConceptText());
            sb.append("Probe the participant's genuine reaction to the concept above — what is ")
                    .append("clear, confusing, appealing or missing for them.\n");
        }
        sb.append("\nAsk EXACTLY ONE next question at a time. Keep it short, open-ended and ")
                .append("neutral (never leading). Build on the participant's previous answers. ")
                .append("Ask in the same language as the research goal. Output ONLY the question ")
                .append("text with no numbering, preamble or quotes. When you have gathered ")
                .append("enough to satisfy the goal, reply with exactly the single word DONE.");
        return sb.toString();
    }

    private String interviewerUserPrompt(List<TurResearchTurnDto> turns) {
        if (turns.isEmpty()) {
            return "The interview has not started yet. Provide your first question.";
        }
        StringBuilder sb = new StringBuilder("Interview transcript so far:\n\n");
        for (TurResearchTurnDto turn : turns) {
            sb.append("Q: ").append(turn.question()).append('\n');
            sb.append("A: ").append(StringUtils.defaultString(turn.answer())).append("\n\n");
        }
        sb.append("Provide the next question, or reply DONE if you have enough.");
        return sb.toString();
    }

    private void appendIfPresent(StringBuilder sb, String label, String value) {
        if (StringUtils.isNotBlank(value)) {
            sb.append(label).append(": ").append(value.trim()).append("\n\n");
        }
    }

    // ---- helpers -----------------------------------------------------------

    private List<String> parseQuestions(String json) {
        if (StringUtils.isBlank(json)) {
            return List.of();
        }
        try {
            List<String> parsed = MAPPER.readValue(json, QUESTIONS_TYPE);
            return parsed == null ? List.of() : parsed;
        } catch (RuntimeException e) {
            log.warn("[ResearchInterview] could not parse questions_json: {}", e.getMessage());
            return List.of();
        }
    }

    private static boolean isText(String type) {
        return type == null || "token".equals(type);
    }

    private static boolean isDone(String question) {
        String normalized = question.strip();
        if (normalized.length() > 8) {
            return false;
        }
        return "DONE".equalsIgnoreCase(normalized.replace(".", "").strip());
    }

    private static String stripQuotes(String raw) {
        if (raw == null) {
            return "";
        }
        String trimmed = raw.strip();
        if (trimmed.length() >= 2
                && ((trimmed.startsWith("\"") && trimmed.endsWith("\""))
                        || (trimmed.startsWith("'") && trimmed.endsWith("'")))) {
            return trimmed.substring(1, trimmed.length() - 1).strip();
        }
        return trimmed;
    }
}
