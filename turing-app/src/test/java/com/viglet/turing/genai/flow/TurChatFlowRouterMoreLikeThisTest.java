/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.flow;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.viglet.turing.persistence.model.agent.TurChatFlow;
import com.viglet.turing.persistence.model.agent.TurChatFlowTriggerLanguage;

/**
 * T26 / §II.2.2 — pins the procedural router's MoreLikeThis scoring.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
class TurChatFlowRouterMoreLikeThisTest {

    @Test
    void mltRoutesOnSingleRareTriggerTerm() {
        var refund = flow("refund", "refund reimbursement chargeback", TurChatFlowTriggerLanguage.EN);
        var shipping = flow("shipping", "shipping delivery tracking", TurChatFlowTriggerLanguage.EN);

        var picked = TurChatFlowEngineService.tryProceduralRoute(candidates(refund, shipping),
                "refund");

        assertThat(picked).contains("refund");
    }

    @Test
    void mltFallsBackWhenTopTwoScoresAreAmbiguous() {
        var first = flow("first", "career plan benefits", TurChatFlowTriggerLanguage.EN);
        var second = flow("second", "career plan benefits", TurChatFlowTriggerLanguage.EN);

        var picked = TurChatFlowEngineService.tryProceduralRoute(candidates(first, second),
                "career plan benefits");

        assertThat(picked).isEmpty();
    }

    @Test
    void mltKeepsT25LanguageSpecificStemming() {
        var english = flow("english-running", "returns and running shoes",
                TurChatFlowTriggerLanguage.EN);
        var portuguese = flow("portuguese-planos", "planos de carreira",
                TurChatFlowTriggerLanguage.PT);

        var picked = TurChatFlowEngineService.tryProceduralRoute(candidates(english, portuguese),
                "run return");

        assertThat(picked).contains("english-running");
    }

    /**
     * Procedural routing is a disambiguation fast-path — with a single
     * candidate there is nothing to disambiguate. The router must defer
     * to the LLM, which can honor negative-list semantics inside the
     * description (e.g. {@code "NÃO ATIVAR para saudações: 'oi', 'olá'..."}).
     * Locks the §3a contract added in 2026.3.1 after a real demo trigger
     * misfired on bare "oi" because the dominance check could not protect
     * a sole candidate.
     */
    @Test
    void mltSkipsSingleCandidateAndDefersToLlmRouter() {
        var lone = flow("solo", "ajude o usuário com planejamento de carreira",
                TurChatFlowTriggerLanguage.PT);

        var picked = TurChatFlowEngineService.tryProceduralRoute(candidates(lone),
                "planejamento de carreira");

        assertThat(picked)
                .as("Single candidate: procedural router must defer to LLM regardless of overlap")
                .isEmpty();
    }

    /**
     * Defense-in-depth against bare short tokens (e.g. {@code "oi"},
     * {@code "ok"}) landing on quoted negative examples inside a long
     * trigger description. BM25 scoring in tiny analyzer buckets can't
     * separate these from legitimate single-rare-term matches (both land
     * in the 0.25-0.32 band), so the router uses a query-side
     * discriminator: at least one token must clear
     * {@code PROCEDURAL_MIN_TOKEN_LENGTH} (3 chars) for procedural
     * scoring to even run.
     */
    @Test
    void mltDefersWhenUserMessageHasOnlyShortTokens() {
        var planA = flow("plan-a",
                "ativar para planejamento de carreira. NÃO ATIVAR para "
                        + "saudações como 'oi', 'olá', 'bom dia'",
                TurChatFlowTriggerLanguage.PT);
        var planB = flow("plan-b", "ativar para in-company training de equipes",
                TurChatFlowTriggerLanguage.PT);

        assertThat(TurChatFlowEngineService.tryProceduralRoute(candidates(planA, planB), "oi"))
                .as("Bare 2-char greeting must defer to LLM router")
                .isEmpty();
        assertThat(TurChatFlowEngineService.tryProceduralRoute(candidates(planA, planB), "ok ok"))
                .as("Repeated 2-char tokens still lack a substantive token — defer to LLM")
                .isEmpty();
        assertThat(TurChatFlowEngineService.tryProceduralRoute(candidates(planA, planB), "oi!"))
                .as("Trailing punctuation does not turn a 2-char token into a substantive one")
                .isEmpty();
    }

    /**
     * Companion to {@link #mltDefersWhenUserMessageHasOnlyShortTokens}:
     * mixed messages with at least one ≥ 3-char content token still hit
     * the procedural fast-path so the guard does not over-filter.
     */
    @Test
    void mltKeepsProceduralFastPathForMessagesWithSubstantiveTokens() {
        var refund = flow("refund", "refund reimbursement chargeback",
                TurChatFlowTriggerLanguage.EN);
        var shipping = flow("shipping", "shipping delivery tracking",
                TurChatFlowTriggerLanguage.EN);

        // "ok refund" has a 2-char + 6-char token; the substantive token
        // satisfies the guard and routing proceeds.
        var picked = TurChatFlowEngineService.tryProceduralRoute(
                candidates(refund, shipping), "ok refund");

        assertThat(picked).contains("refund");
    }

    private static TurChatFlow flow(String id, String triggerDescription,
            TurChatFlowTriggerLanguage language) {
        TurChatFlow flow = new TurChatFlow();
        flow.setId(id);
        flow.setTriggerDescription(triggerDescription);
        flow.setTriggerLanguage(language);
        return flow;
    }

    private static List<TurChatFlowEngineService.RouterCandidate> candidates(TurChatFlow... flows) {
        return java.util.Arrays.stream(flows)
                .map(flow -> new TurChatFlowEngineService.RouterCandidate(flow, null))
                .toList();
    }
}
