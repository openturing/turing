/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.authoring.persona;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.viglet.turing.genai.authoring.persona.TurAudienceCohortService.CohortResult;
import com.viglet.turing.persistence.dto.persona.TurPersonaDto;
import com.viglet.turing.persistence.model.persona.TurPersonaKind;
import com.viglet.turing.persistence.model.persona.TurPersonaTone;
import com.viglet.turing.system.TurLlmSummaryService;
import com.viglet.turing.system.TurLlmSummaryService.SummaryResult;

/**
 * Unit coverage for {@link TurAudienceCohortService} (Block AW / §XLVI.5, T731) —
 * strict-JSON-array parse → never-saved drafts with clamped OCEAN facets, and the
 * fail-open guards (blank brief, no LLM, unparseable output).
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@ExtendWith(MockitoExtension.class)
class TurAudienceCohortServiceTest {

    @Mock
    private TurLlmSummaryService llmSummaryService;

    @InjectMocks
    private TurAudienceCohortService service;

    private static final String TWO_PERSONAS = """
            [
              {"name":"careful-planner","description":"Methodical buyer","systemInstruction":"You are careful.",
               "tone":"FORMAL","verbosity":4,"languageStyle":"NEUTRAL",
               "readingLevel":"GRADUATE","domainExpertise":"ADVANCED","vocabularyCeiling":"rich",
               "primaryLanguage":"en","openness":30,"conscientiousness":90,"extraversion":20,
               "agreeableness":60,"neuroticism":150},
              {"name":"impulsive-explorer","description":"Spontaneous shopper","systemInstruction":"You are bold.",
               "tone":"CASUAL","verbosity":2,"languageStyle":"DIRECT",
               "readingLevel":"SECONDARY","domainExpertise":"BEGINNER","vocabularyCeiling":"plain",
               "primaryLanguage":"en","openness":95,"conscientiousness":25,"extraversion":88,
               "agreeableness":50,"neuroticism":40}
            ]
            """;

    @Test
    void synthesizesDiverseDraftsWithClampedFacets() {
        when(llmSummaryService.generate(anyString(), anyString(), anyString(), anyBoolean()))
                .thenReturn(new SummaryResult(true, null, TWO_PERSONAS, true));

        CohortResult result = service.synthesize("Online grocery shoppers in Brazil", 2, false);

        assertThat(result.success()).isTrue();
        assertThat(result.personas()).hasSize(2);
        TurPersonaDto first = result.personas().get(0);
        assertThat(first.getId()).isNull();
        assertThat(first.getName()).isEqualTo("careful-planner");
        assertThat(first.getTone()).isEqualTo(TurPersonaTone.FORMAL);
        assertThat(first.getPersonaKind()).isEqualTo(TurPersonaKind.BOTH);
        assertThat(first.getConscientiousness()).isEqualTo(90);
        // 150 is out of range and must clamp to 100.
        assertThat(first.getNeuroticism()).isEqualTo(100);
        assertThat(first.getAudience()).isNotNull();
        assertThat(result.personas().get(1).getOpenness()).isEqualTo(95);
    }

    @Test
    void blankBriefFailsWithoutCallingLlm() {
        CohortResult result = service.synthesize("   ", 5, false);

        assertThat(result.success()).isFalse();
        assertThat(result.personas()).isEmpty();
    }

    @Test
    void noLlmFailsOpen() {
        when(llmSummaryService.generate(anyString(), anyString(), anyString(), anyBoolean()))
                .thenReturn(new SummaryResult(false, "no default LLM", null, false));

        CohortResult result = service.synthesize("Enterprise IT buyers", 4, false);

        assertThat(result.success()).isFalse();
        assertThat(result.error()).contains("no default LLM");
    }

    @Test
    void unparseableOutputFails() {
        when(llmSummaryService.generate(anyString(), anyString(), anyString(), anyBoolean()))
                .thenReturn(new SummaryResult(true, null, "sorry, I cannot do that", true));

        CohortResult result = service.synthesize("Anyone", 3, false);

        assertThat(result.success()).isFalse();
        assertThat(result.personas()).isEmpty();
    }
}
