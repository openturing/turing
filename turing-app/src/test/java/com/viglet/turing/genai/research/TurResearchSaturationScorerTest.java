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

import java.util.List;

import org.junit.jupiter.api.Test;

import com.viglet.turing.genai.research.TurResearchSaturationScorer.Participant;
import com.viglet.turing.genai.research.dto.TurResearchSaturationResultDto;
import com.viglet.turing.genai.research.dto.TurResearchSaturationStepDto;

/**
 * Pure, LLM-free unit test for the T723 saturation scorer (Block AW / §XLVI.3).
 * Runs in CI with no Spring context and no model — the whole point of the
 * deterministic scorer.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
class TurResearchSaturationScorerTest {

    private final TurResearchSaturationScorer scorer = new TurResearchSaturationScorer();

    @Test
    void unavailableBelowTwoParticipantsWithContent() {
        TurResearchSaturationResultDto empty = scorer.score(List.of());
        assertFalse(empty.available());
        assertEquals(0, empty.personaCount());

        TurResearchSaturationResultDto single = scorer.score(List.of(
                new Participant("p1", "Ana", "checkout payment slow confusing")));
        assertFalse(single.available());
        assertEquals(1, single.personaCount());
    }

    @Test
    void detectsSaturationWhenLaterPersonasRepeatThemes() {
        // First two raise distinct themes; the last two only echo them → dry tail.
        List<Participant> cohort = List.of(
                new Participant("p1", "Ana", "checkout payment slow confusing"),
                new Participant("p2", "Bob", "search results irrelevant ranking"),
                new Participant("p3", "Cid", "checkout payment slow"),
                new Participant("p4", "Dora", "search ranking results"));

        TurResearchSaturationResultDto result = scorer.score(cohort);

        assertTrue(result.available());
        assertTrue(result.saturated());
        assertEquals(2, result.adequateAtN(), "sample became adequate after the first two personas");
        assertEquals(8, result.totalUniqueThemes());
        assertEquals(4, result.steps().size());

        // Cumulative themes never decrease; roster identity is carried through.
        List<TurResearchSaturationStepDto> steps = result.steps();
        assertEquals("p1", steps.get(0).personaId());
        assertEquals(4, steps.get(0).newThemes());
        assertEquals(0, steps.get(2).newThemes(), "Cid only echoed Ana's themes");
        assertEquals(0.0, steps.get(3).noveltyRatio(), "Dora added nothing new");
        int prevCumulative = 0;
        for (TurResearchSaturationStepDto step : steps) {
            assertTrue(step.cumulativeThemes() >= prevCumulative);
            prevCumulative = step.cumulativeThemes();
        }
    }

    @Test
    void neverSaturatesWhenEveryPersonaKeepsAddingThemes() {
        List<Participant> cohort = List.of(
                new Participant("p1", "Ana", "checkout payment slow confusing"),
                new Participant("p2", "Bob", "search results irrelevant ranking"),
                new Participant("p3", "Cid", "delivery tracking notifications refund"),
                new Participant("p4", "Dora", "onboarding tutorial dashboard analytics"));

        TurResearchSaturationResultDto result = scorer.score(cohort);

        assertTrue(result.available());
        assertFalse(result.saturated(), "each persona still teaches us something new");
        assertEquals(-1, result.adequateAtN());
        assertEquals(16, result.totalUniqueThemes());
    }
}
