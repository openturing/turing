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
import com.viglet.turing.persistence.repository.agent.TurAIAgentRepository;

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
 * <p><strong>T726 — interview your own deployed agent (§XLVI.4).</strong> When the
 * study carries a {@code targetAgentId}, the roles invert: the roster persona
 * becomes the synthetic <em>user</em> (a persona-fused simulator drives the
 * conversation toward the study goal) and the <em>answerer</em> is the live
 * deployed {@link TurAIAgent} resolved from the repository — so the study
 * UX-tests / red-teams the actual shipped assistant with its real system prompt,
 * tools and RAG. Only Turing can do this, because it owns both the research panel
 * and the agent under test. Like persona-chat (T578) the turn stays stateless
 * ({@code conversationId = flowId = null}): the agent answers with its real prompt
 * + tools + RAG, but a multi-step chat-<em>flow</em> is not driven — a future task
 * can add a stateful-flow interview mode. When {@code targetAgentId} is null the
 * legacy behaviour is byte-identical: the persona is the interviewee answered by a
 * bare LLM.
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
    private final TurAIAgentRepository agentRepository;

    public TurResearchInterviewEngine(TurAgentChatExecutor executor,
            TurNativeToolService nativeToolService,
            TurAIAgentRepository agentRepository) {
        this.executor = executor;
        this.nativeToolService = nativeToolService;
        this.agentRepository = agentRepository;
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
        // T726 — a live target agent (its real prompt/tools/RAG) answers instead of a
        // bare persona-fused LLM; null keeps the legacy persona-as-interviewee flow.
        TurAIAgent target = resolveTargetAgent(study);
        return switch (protocol) {
            case CUSTOM_SCRIPT -> runCustomScript(study, persona, llm, target);
            case CONCEPT_TEST, DYNAMIC_SCRIPT -> runAdaptive(study, persona, llm, protocol, target);
        };
    }

    // ---- protocols ---------------------------------------------------------

    private List<TurResearchTurnDto> runCustomScript(TurResearchStudy study, TurPersona persona,
            TurLLMInstance llm, TurAIAgent target) {
        List<String> questions = parseQuestions(study.getQuestionsJson());
        // Target mode: the fixed script is replayed to the deployed agent. Legacy:
        // the persona-fused shell answers the script.
        TurAIAgent answerer = target != null ? target : buildPersonaAgent(persona);
        List<TurResearchTurnDto> turns = new ArrayList<>(questions.size());
        int index = 0;
        for (String question : questions) {
            if (StringUtils.isBlank(question)) {
                continue;
            }
            String answer = askAnswerer(answerer, llm, turns, question.trim());
            turns.add(new TurResearchTurnDto(index++, question.trim(), answer));
        }
        return turns;
    }

    private List<TurResearchTurnDto> runAdaptive(TurResearchStudy study, TurPersona persona,
            TurLLMInstance llm, TurResearchProtocol protocol, TurAIAgent target) {
        int cap = Math.min(Math.max(study.getMaxQuestions(), 1), MAX_QUESTIONS_HARD_CAP);
        boolean targeting = target != null;
        // Target mode inverts the roles: the persona plays the synthetic user asking
        // the questions, and the deployed agent answers. Legacy mode: a goal-driven
        // interviewer asks, the persona-fused shell answers.
        TurAIAgent questioner = targeting ? buildUserSimulatorAgent(persona) : buildInterviewerAgent();
        String questionerSystem = targeting
                ? userSimulatorSystemPrompt(study, persona, protocol)
                : interviewerSystemPrompt(study, protocol);
        TurAIAgent answerer = targeting ? target : buildPersonaAgent(persona);

        List<TurResearchTurnDto> turns = new ArrayList<>(cap);
        for (int index = 0; index < cap; index++) {
            String question = nextQuestion(questioner, questionerSystem, llm, turns);
            if (StringUtils.isBlank(question) || isDone(question)) {
                break;
            }
            String answer = askAnswerer(answerer, llm, turns, question);
            turns.add(new TurResearchTurnDto(index, question, answer));
            if (StringUtils.isBlank(answer)) {
                break;
            }
        }
        return turns;
    }

    /** Resolve the study's T726 target agent, or {@code null} when unset/unknown. */
    private TurAIAgent resolveTargetAgent(TurResearchStudy study) {
        String targetAgentId = study.getTargetAgentId();
        if (StringUtils.isBlank(targetAgentId)) {
            return null;
        }
        TurAIAgent target = agentRepository.findById(targetAgentId).orElse(null);
        if (target == null) {
            log.warn("[ResearchInterview] study {} targets unknown agent {}; "
                    + "falling back to the bare-LLM persona interview",
                    study.getId(), targetAgentId);
        }
        return target;
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

    /**
     * Ask the answerer the current question, carrying prior turns as context. The
     * answerer is the persona-fused shell (legacy) or the deployed target agent
     * (T726); either way {@code systemPromptOverride} stays null so the agent uses
     * its own resolved system prompt (persona fusion, or the deployed agent's real
     * prompt + tools + RAG).
     */
    private String askAnswerer(TurAIAgent answerer, TurLLMInstance llm,
            List<TurResearchTurnDto> priorTurns, String question) {
        List<ChatMessageItem> history = new ArrayList<>(priorTurns.size() * 2 + 1);
        for (TurResearchTurnDto turn : priorTurns) {
            history.add(new ChatMessageItem("user", turn.question()));
            history.add(new ChatMessageItem("assistant", turn.answer()));
        }
        history.add(new ChatMessageItem("user", question));
        return collectAnswer(answerer, llm, history, null);
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

    /**
     * T726 — the synthetic-user simulator shell that drives a conversation with the
     * deployed target agent. Tool-less like the interviewer; the persona's identity
     * is baked into the {@link #userSimulatorSystemPrompt} override (rather than
     * relying on {@code defaultPersona} fusion, which the override would replace),
     * so the questioner behaves as that user deterministically.
     */
    private TurAIAgent buildUserSimulatorAgent(TurPersona persona) {
        TurAIAgent agent = new TurAIAgent();
        agent.setTitle("Synthetic user: " + persona.getName());
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

    /**
     * T726 — the system prompt for the persona-as-synthetic-user simulator that
     * red-teams / UX-tests the deployed target agent. Bakes the persona's identity
     * (name, description, behavioural instruction, OCEAN facet) in so the questioner
     * behaves as that user, then frames the study goal as the user's objective.
     */
    private String userSimulatorSystemPrompt(TurResearchStudy study, TurPersona persona,
            TurResearchProtocol protocol) {
        StringBuilder sb = new StringBuilder();
        sb.append("You ARE the following person, talking to an AI assistant exactly as a real ")
                .append("user would. Never break character and never reveal that you are an AI.\n\n");
        sb.append("# Who you are\n");
        sb.append("Name: ").append(persona.getName()).append('\n');
        appendIfPresent(sb, "About you", persona.getDescription());
        appendIfPresent(sb, "How you behave", persona.getSystemInstruction());
        appendPersonality(sb, persona);
        sb.append("\n# Your objective\n");
        if (StringUtils.isNotBlank(study.getGoal())) {
            sb.append(study.getGoal().trim()).append('\n');
        } else {
            sb.append("Explore whether this assistant can genuinely help someone like you.\n");
        }
        appendIfPresent(sb, "What you are secretly trying to find out", study.getHypothesis());
        if (protocol == TurResearchProtocol.CONCEPT_TEST) {
            appendIfPresent(sb, "React to this concept", study.getConceptText());
        }
        sb.append("\nTalk to the assistant to accomplish your objective, ONE message at a time, ")
                .append("as this person would — with their knowledge level, tone and concerns. Be ")
                .append("realistic: ask follow-ups, push back, or go off the happy path like a real ")
                .append("user. Write in the same language as your objective. Output ONLY your next ")
                .append("message to the assistant — no narration, labels or quotes. When your ")
                .append("objective is met or you are truly stuck, reply with exactly the single ")
                .append("word DONE.");
        return sb.toString();
    }

    /** Append a compact OCEAN (Big Five) descriptor when any facet is set (T717). */
    private void appendPersonality(StringBuilder sb, TurPersona persona) {
        List<String> traits = new ArrayList<>(5);
        addTrait(traits, "openness", persona.getOpenness());
        addTrait(traits, "conscientiousness", persona.getConscientiousness());
        addTrait(traits, "extraversion", persona.getExtraversion());
        addTrait(traits, "agreeableness", persona.getAgreeableness());
        addTrait(traits, "neuroticism", persona.getNeuroticism());
        if (!traits.isEmpty()) {
            sb.append("Personality (0-100): ").append(String.join(", ", traits)).append("\n\n");
        }
    }

    private void addTrait(List<String> traits, String label, Integer value) {
        if (value != null) {
            traits.add(label + " " + value);
        }
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
