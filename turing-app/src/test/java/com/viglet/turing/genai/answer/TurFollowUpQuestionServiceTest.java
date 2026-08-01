/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.answer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.viglet.turing.system.TurLlmSummaryService;
import com.viglet.turing.system.TurLlmSummaryService.SummaryResult;

/**
 * T834 / §LXII.3 — coverage for {@link TurFollowUpQuestionService}: the
 * one-question-per-line parsing and the fail-open contract.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@ExtendWith(MockitoExtension.class)
class TurFollowUpQuestionServiceTest {

    @Mock
    private TurLlmSummaryService llmSummaryService;

    @InjectMocks
    private TurFollowUpQuestionService service;

    @Test
    void parseQuestionsStripsMarkersTrimsDedupesAndDropsTheEcho() {
        String content = """
                1. What is X?
                - How does Y work?
                What is X?

                  When to use Z?\s
                """;

        List<String> questions = TurFollowUpQuestionService.parseQuestions(content, "How does Y work?");

        // "What is X?" de-duplicated; the echo of the original query dropped.
        assertThat(questions).containsExactly("What is X?", "When to use Z?");
    }

    @Test
    void parseQuestionsCapsAtFive() {
        String content = "q1?\nq2?\nq3?\nq4?\nq5?\nq6?\nq7?";

        assertThat(TurFollowUpQuestionService.parseQuestions(content, "orig")).hasSize(5);
    }

    @Test
    void generateReturnsEmptyWhenNoLlmAvailable() {
        when(llmSummaryService.isAvailable()).thenReturn(false);

        assertThat(service.generate("site1", "q", "an answer", List.of())).isEmpty();
    }

    @Test
    void generateReturnsEmptyForBlankAnswerWithoutCallingTheLlm() {
        // Short-circuits before touching the LLM — no stubbing needed.
        assertThat(service.generate("site1", "q", "   ", List.of())).isEmpty();
    }

    @Test
    void generateParsesTheModelReply() {
        when(llmSummaryService.isAvailable()).thenReturn(true);
        when(llmSummaryService.generate(any(), any(), any(), anyBoolean()))
                .thenReturn(new SummaryResult(true, null, "First?\nSecond?", false));

        assertThat(service.generate("site1", "q", "an answer", List.of()))
                .containsExactly("First?", "Second?");
    }

    @Test
    void generateFailsOpenWhenTheLlmCallIsUnsuccessful() {
        when(llmSummaryService.isAvailable()).thenReturn(true);
        when(llmSummaryService.generate(any(), any(), any(), anyBoolean()))
                .thenReturn(new SummaryResult(false, "boom", null, false));

        assertThat(service.generate("site1", "q", "an answer", List.of())).isEmpty();
    }
}
