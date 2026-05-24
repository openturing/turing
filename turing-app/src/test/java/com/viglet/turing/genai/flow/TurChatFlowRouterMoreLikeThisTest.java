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
