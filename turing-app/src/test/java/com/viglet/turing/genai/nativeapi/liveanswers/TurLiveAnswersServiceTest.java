/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.nativeapi.liveanswers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.util.ReflectionTestUtils;

import com.viglet.turing.genai.citation.TurCitationDocument;
import com.viglet.turing.genai.tool.TurRagSearchToolService;
import com.viglet.turing.persistence.model.agent.TurAIAgent;

/**
 * T187 / §X.15.a — unit tests for {@link TurLiveAnswersService}: the opt-in
 * gate, the fusion-prompt + [KB-n] grounding augmentation, the double-grounding
 * skip, and the graceful no-hit / blank-query degradation.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@ExtendWith(MockitoExtension.class)
class TurLiveAnswersServiceTest {

    @Mock
    private TurRagSearchToolService ragSearchToolService;

    @InjectMocks
    private TurLiveAnswersService service;

    @BeforeEach
    void loadRealFusionPrompt() {
        // The @Value resource is not injected outside Spring; wire the real
        // classpath prompt and run the @PostConstruct loader by hand so the
        // augment() assertions see the shipped fusion guidance.
        ReflectionTestUtils.setField(service, "fusionPromptResource",
                new ClassPathResource("prompts/live-answers.md"));
        ReflectionTestUtils.invokeMethod(service, "loadFusionPrompt");
    }

    private static TurAIAgent agent(boolean liveAnswers) {
        TurAIAgent agent = new TurAIAgent();
        agent.setTitle("Agent");
        agent.setLiveAnswersEnabled(liveAnswers);
        return agent;
    }

    @Test
    void isEnabledReflectsTheFlag() {
        assertThat(service.isEnabled(agent(true))).isTrue();
        assertThat(service.isEnabled(agent(false))).isFalse();
        assertThat(service.isEnabled(null)).isFalse();
    }

    @Test
    void augmentAppendsFusionPromptAndGroundingBlock() {
        when(ragSearchToolService.retrievePassagesForCitations(eq("how much is the plan?"), anyInt()))
                .thenReturn(List.of(
                        new TurCitationDocument("doc-1", "Pricing Page", "https://x/p", "The Pro plan is $20/mo."),
                        new TurCitationDocument("doc-2", "FAQ", null, "Billed monthly.")));

        String augmented = service.augment("BASE PROMPT", "how much is the plan?", false);

        assertThat(augmented)
                .startsWith("BASE PROMPT")
                .contains("LIVE ANSWERS")                 // fusion guidance loaded
                // grounding header — distinctive phrase only the block carries
                .contains("were retrieved from the indexed knowledge base")
                .contains("[KB-1] Pricing Page (https://x/p)")
                .contains("The Pro plan is $20/mo.")
                .contains("[KB-2] FAQ")
                .contains("Billed monthly.")
                // No url for the second passage → no parenthesised link.
                .doesNotContain("[KB-2] FAQ (");
    }

    @Test
    void augmentSkipsGroundingWhenAlreadyGrounded() {
        String augmented = service.augment("BASE PROMPT", "any question", true);

        assertThat(augmented)
                .startsWith("BASE PROMPT")
                .contains("LIVE ANSWERS")                       // fusion guidance still appended
                // but no grounding block (its distinctive header is absent)
                .doesNotContain("were retrieved from the indexed knowledge base");
        // The Anthropic citations path already attaches the passages, so we must
        // not run a second retrieval here.
        verifyNoInteractions(ragSearchToolService);
    }

    @Test
    void groundingBlockEmptyForBlankQuery() {
        assertThat(service.buildGroundingBlock("  ")).isEmpty();
        // Blank query → no retrieval at all.
        verifyNoInteractions(ragSearchToolService);
    }

    @Test
    void groundingBlockEmptyWhenNoHits() {
        when(ragSearchToolService.retrievePassagesForCitations(eq("q"), anyInt()))
                .thenReturn(List.of());
        assertThat(service.buildGroundingBlock("q")).isEmpty();
    }

    @Test
    void groundingBlockSkipsBlankTextPassages() {
        when(ragSearchToolService.retrievePassagesForCitations(eq("q"), anyInt()))
                .thenReturn(List.of(
                        new TurCitationDocument("doc-1", "Empty", null, "   "),
                        new TurCitationDocument("doc-2", "Real", null, "actual text")));

        String block = service.buildGroundingBlock("q");

        // The blank passage is dropped; numbering still starts at [KB-1] for the
        // first NON-empty passage.
        assertThat(block)
                .contains("[KB-1] Real")
                .contains("actual text")
                .doesNotContain("Empty");
    }
}
