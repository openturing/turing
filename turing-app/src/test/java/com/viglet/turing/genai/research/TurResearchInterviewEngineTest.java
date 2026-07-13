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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.viglet.turing.genai.TurAgentChatExecutor;
import com.viglet.turing.genai.TurAgentChatExecutor.ChatResponse;
import com.viglet.turing.genai.TurAgentChatRequest;
import com.viglet.turing.genai.research.dto.TurResearchTurnDto;
import com.viglet.turing.genai.tool.TurNativeToolService;
import com.viglet.turing.persistence.model.llm.TurLLMInstance;
import com.viglet.turing.persistence.model.persona.TurPersona;
import com.viglet.turing.persistence.model.research.TurResearchProtocol;
import com.viglet.turing.persistence.model.research.TurResearchStudy;

import reactor.core.publisher.Flux;

/**
 * Unit tests for the interview engine (Block AW / §XLVI.2, T720). The shared
 * chat executor is mocked so each protocol's question-selection logic is
 * exercised without a network/LLM: the interviewer turn is recognised by its
 * non-null {@code systemPromptOverride}, the persona turn echoes its last user
 * message. Verifies CUSTOM_SCRIPT asks each question verbatim, DYNAMIC_SCRIPT
 * loops until the interviewer replies DONE (respecting the cap), and CONCEPT_TEST
 * seeds the interviewer system prompt with the concept.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@ExtendWith(MockitoExtension.class)
class TurResearchInterviewEngineTest {

    @Mock
    private TurAgentChatExecutor executor;
    @Mock
    private TurNativeToolService nativeToolService;

    private TurResearchInterviewEngine engine;
    private final List<TurAgentChatRequest> captured = new ArrayList<>();

    @BeforeEach
    void setUp() {
        engine = new TurResearchInterviewEngine(executor, nativeToolService);
        captured.clear();
        lenient().when(nativeToolService.getAllTools()).thenReturn(List.of());
    }

    private TurPersona persona() {
        TurPersona persona = new TurPersona();
        persona.setName("Test participant");
        return persona;
    }

    /** Interviewer turn = non-null systemPromptOverride; persona turn echoes input. */
    private void stubExecutor(List<String> interviewerReplies) {
        int[] idx = {0};
        when(executor.execute(any(TurAgentChatRequest.class))).thenAnswer(inv -> {
            TurAgentChatRequest req = inv.getArgument(0);
            captured.add(req);
            if (req.systemPromptOverride() != null) {
                String reply = idx[0] < interviewerReplies.size()
                        ? interviewerReplies.get(idx[0])
                        : "DONE";
                idx[0]++;
                return Flux.just(new ChatResponse("assistant", reply, "token"));
            }
            String lastUser = req.history().get(req.history().size() - 1).content();
            return Flux.just(new ChatResponse("assistant", "Answer to: " + lastUser, "token"));
        });
    }

    @Test
    void customScriptAsksEachQuestionVerbatim() {
        stubExecutor(List.of()); // interviewer never used
        TurResearchStudy study = new TurResearchStudy();
        study.setProtocol(TurResearchProtocol.CUSTOM_SCRIPT);
        study.setQuestionsJson("[\"What do you value most?\",\"What frustrates you?\"]");

        List<TurResearchTurnDto> turns = engine.interview(study, persona(), new TurLLMInstance());

        assertEquals(2, turns.size());
        assertEquals("What do you value most?", turns.get(0).question());
        assertEquals("Answer to: What do you value most?", turns.get(0).answer());
        assertEquals("What frustrates you?", turns.get(1).question());
        // No interviewer turn was issued.
        assertTrue(captured.stream().allMatch(r -> r.systemPromptOverride() == null));
    }

    @Test
    void dynamicScriptLoopsUntilInterviewerReturnsDone() {
        stubExecutor(List.of("First question?", "Second question?", "DONE"));
        TurResearchStudy study = new TurResearchStudy();
        study.setProtocol(TurResearchProtocol.DYNAMIC_SCRIPT);
        study.setGoal("Understand onboarding pain");
        study.setMaxQuestions(6);

        List<TurResearchTurnDto> turns = engine.interview(study, persona(), new TurLLMInstance());

        assertEquals(2, turns.size());
        assertEquals("First question?", turns.get(0).question());
        assertEquals("Second question?", turns.get(1).question());
        // The interviewer system prompt carries the research goal.
        assertTrue(captured.stream()
                .filter(r -> r.systemPromptOverride() != null)
                .anyMatch(r -> r.systemPromptOverride().contains("Understand onboarding pain")));
    }

    @Test
    void dynamicScriptRespectsMaxQuestionsCap() {
        // Interviewer never says DONE — the cap must stop the loop.
        stubExecutor(List.of("Q1?", "Q2?", "Q3?", "Q4?", "Q5?"));
        TurResearchStudy study = new TurResearchStudy();
        study.setProtocol(TurResearchProtocol.DYNAMIC_SCRIPT);
        study.setGoal("goal");
        study.setMaxQuestions(2);

        List<TurResearchTurnDto> turns = engine.interview(study, persona(), new TurLLMInstance());

        assertEquals(2, turns.size());
    }

    @Test
    void conceptTestSeedsInterviewerWithConcept() {
        stubExecutor(List.of("What is your first reaction?", "DONE"));
        TurResearchStudy study = new TurResearchStudy();
        study.setProtocol(TurResearchProtocol.CONCEPT_TEST);
        study.setGoal("Validate the new pricing page");
        study.setConceptText("A flat $9/month plan with unlimited searches");
        study.setMaxQuestions(4);

        List<TurResearchTurnDto> turns = engine.interview(study, persona(), new TurLLMInstance());

        assertEquals(1, turns.size());
        assertFalse(captured.isEmpty());
        assertTrue(captured.stream()
                .filter(r -> r.systemPromptOverride() != null)
                .anyMatch(r -> r.systemPromptOverride()
                        .contains("A flat $9/month plan with unlimited searches")));
    }
}
