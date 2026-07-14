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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.viglet.turing.genai.research.TurResearchAssistantService.ProposalResult;
import com.viglet.turing.system.TurLlmSummaryService;
import com.viglet.turing.system.TurLlmSummaryService.SummaryResult;

/**
 * Unit coverage for {@link TurResearchAssistantService} (Block AW / §XLVI.5, T732)
 * — strict-JSON proposal parse (protocol coercion + clamps) and the fail-open
 * guards (blank brief, no LLM, unparseable, unknown protocol).
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@ExtendWith(MockitoExtension.class)
class TurResearchAssistantServiceTest {

    @Mock
    private TurLlmSummaryService llmSummaryService;

    @InjectMocks
    private TurResearchAssistantService service;

    @Test
    void proposesAConceptTestStudy() {
        String json = """
                {"name":"Pricing page reaction","goal":"See if the new pricing lands",
                 "hypothesis":"Buyers find it clearer","protocol":"CONCEPT_TEST",
                 "conceptText":"A 3-tier pricing page","questions":[],"maxQuestions":40,
                 "suggestedCohortBrief":"SMB buyers evaluating tools","suggestedParticipantCount":20,
                 "assistantMessage":"I drafted a concept test."}
                """;
        when(llmSummaryService.generate(anyString(), anyString(), anyString(), anyBoolean()))
                .thenReturn(new SummaryResult(true, null, json, true));

        ProposalResult result = service.propose("test my new pricing page", false);

        assertThat(result.success()).isTrue();
        assertThat(result.proposal().protocol()).isEqualTo("CONCEPT_TEST");
        assertThat(result.proposal().conceptText()).isEqualTo("A 3-tier pricing page");
        // maxQuestions clamped to 30, participant count clamped to 12.
        assertThat(result.proposal().maxQuestions()).isEqualTo(30);
        assertThat(result.proposal().suggestedParticipantCount()).isEqualTo(12);
    }

    @Test
    void unknownProtocolDefaultsToDynamic() {
        String json = """
                {"name":"Discovery","goal":"Learn onboarding pain","protocol":"WHATEVER",
                 "questions":[],"maxQuestions":6,"suggestedParticipantCount":5,
                 "suggestedCohortBrief":"New users","assistantMessage":"ok"}
                """;
        when(llmSummaryService.generate(anyString(), anyString(), anyString(), anyBoolean()))
                .thenReturn(new SummaryResult(true, null, json, true));

        ProposalResult result = service.propose("learn onboarding pain", false);

        assertThat(result.success()).isTrue();
        assertThat(result.proposal().protocol()).isEqualTo("DYNAMIC_SCRIPT");
    }

    @Test
    void blankBriefFailsWithoutCallingLlm() {
        ProposalResult result = service.propose("  ", false);

        assertThat(result.success()).isFalse();
        assertThat(result.proposal()).isNull();
    }

    @Test
    void noLlmFailsOpen() {
        when(llmSummaryService.generate(anyString(), anyString(), anyString(), anyBoolean()))
                .thenReturn(new SummaryResult(false, "no default LLM", null, false));

        ProposalResult result = service.propose("anything", false);

        assertThat(result.success()).isFalse();
        assertThat(result.error()).contains("no default LLM");
    }

    @Test
    void unparseableOutputFails() {
        when(llmSummaryService.generate(anyString(), anyString(), anyString(), anyBoolean()))
                .thenReturn(new SummaryResult(true, null, "I cannot help with that", true));

        ProposalResult result = service.propose("anything", false);

        assertThat(result.success()).isFalse();
        assertThat(result.proposal()).isNull();
    }
}
