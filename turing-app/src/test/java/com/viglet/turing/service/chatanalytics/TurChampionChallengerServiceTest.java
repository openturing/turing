/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.service.chatanalytics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.viglet.turing.persistence.model.agent.TurChatFlow;
import com.viglet.turing.persistence.repository.agent.TurChatFlowRepository;
import com.viglet.turing.service.chatanalytics.TurChampionChallengerService.PromotionResult;
import com.viglet.turing.service.chatanalytics.TurExperimentSignificanceService.SignificanceResult;
import com.viglet.turing.service.chatanalytics.TurExperimentSignificanceService.SuccessMetric;

/**
 * Pins the T71 champion-challenger promotion: a declared winner is pinned to
 * 100% traffic and the losers archived (weight 0 + closed window); no winner /
 * underpowered / unknown winner is a no-op; dry-run computes the plan without
 * persisting; the scheduled sweep only touches opted-in experiments.
 */
@ExtendWith(MockitoExtension.class)
class TurChampionChallengerServiceTest {

    @Mock
    private TurExperimentSignificanceService significanceService;
    @Mock
    private TurChatFlowRepository chatFlowRepository;

    private TurChampionChallengerService service() {
        return new TurChampionChallengerService(significanceService, chatFlowRepository);
    }

    private TurChatFlow variant(String label, Integer weight, Boolean autoPromote, String experimentKey) {
        TurChatFlow f = new TurChatFlow();
        f.setId("flow-" + label);
        f.setVariantLabel(label);
        f.setTrafficWeight(weight);
        f.setAutoPromote(autoPromote);
        f.setExperimentKey(experimentKey);
        return f;
    }

    private SignificanceResult sigWithWinner(String key, String winner, Double pValue) {
        return new SignificanceResult(key, SuccessMetric.GOAL_ACHIEVED.name(), List.of(),
                winner, pValue, 0.05, false,
                "Significant difference detected. Winner: '" + winner + "'.");
    }

    private SignificanceResult sigNoWinner(String key, String reason) {
        return new SignificanceResult(key, SuccessMetric.GOAL_ACHIEVED.name(), List.of(),
                null, null, 0.05, true, reason);
    }

    @Test
    void promotesWinnerAndArchivesLoser() {
        TurChatFlow control = variant("control", 50, null, "exp");
        TurChatFlow treatment = variant("treatment", 50, null, "exp");
        when(significanceService.evaluate(eq("exp"), any(), any(), any(), any()))
                .thenReturn(sigWithWinner("exp", "treatment", 0.0123));
        when(chatFlowRepository.findByExperimentKey("exp")).thenReturn(List.of(control, treatment));

        PromotionResult result = service().promote("exp", SuccessMetric.GOAL_ACHIEVED, null, false);

        assertThat(result.applied()).isTrue();
        assertThat(result.dryRun()).isFalse();
        assertThat(result.winner()).isEqualTo("treatment");
        assertThat(result.winnerFlowId()).isEqualTo("flow-treatment");
        assertThat(result.archivedVariants()).containsExactly("control");

        // Champion pinned, bandit cleared, window untouched.
        assertThat(treatment.getTrafficWeight()).isEqualTo(100);
        assertThat(treatment.getBanditEnabled()).isFalse();
        assertThat(treatment.getExperimentEndsAt()).isNull();
        // Loser archived: weight 0, bandit cleared, window closed.
        assertThat(control.getTrafficWeight()).isZero();
        assertThat(control.getBanditEnabled()).isFalse();
        assertThat(control.getExperimentEndsAt()).isNotNull();

        verify(chatFlowRepository).save(treatment);
        verify(chatFlowRepository).save(control);
    }

    @Test
    void noOpWhenNoWinnerDeclared() {
        when(significanceService.evaluate(eq("exp"), any(), any(), any(), any()))
                .thenReturn(sigNoWinner("exp", "No significant difference yet. Keep collecting data."));

        PromotionResult result = service().promote("exp", SuccessMetric.GOAL_ACHIEVED, null, false);

        assertThat(result.applied()).isFalse();
        assertThat(result.winner()).isNull();
        assertThat(result.archivedVariants()).isEmpty();
        assertThat(result.reason()).contains("No significant difference");
        verify(chatFlowRepository, never()).findByExperimentKey(any());
        verify(chatFlowRepository, never()).save(any());
    }

    @Test
    void noOpWhenWinnerFlowNotFound() {
        TurChatFlow control = variant("control", 50, null, "exp");
        TurChatFlow treatment = variant("treatment", 50, null, "exp");
        when(significanceService.evaluate(eq("exp"), any(), any(), any(), any()))
                .thenReturn(sigWithWinner("exp", "ghost-label", 0.01));
        when(chatFlowRepository.findByExperimentKey("exp")).thenReturn(List.of(control, treatment));

        PromotionResult result = service().promote("exp", SuccessMetric.GOAL_ACHIEVED, null, false);

        assertThat(result.applied()).isFalse();
        assertThat(result.reason()).contains("mismatch");
        verify(chatFlowRepository, never()).save(any());
        // Nothing mutated.
        assertThat(treatment.getTrafficWeight()).isEqualTo(50);
        assertThat(control.getTrafficWeight()).isEqualTo(50);
    }

    @Test
    void noOpWhenNoFlowsForKey() {
        when(significanceService.evaluate(eq("exp"), any(), any(), any(), any()))
                .thenReturn(sigWithWinner("exp", "treatment", 0.01));
        when(chatFlowRepository.findByExperimentKey("exp")).thenReturn(List.of());

        PromotionResult result = service().promote("exp", SuccessMetric.GOAL_ACHIEVED, null, false);

        assertThat(result.applied()).isFalse();
        assertThat(result.reason()).contains("no flows found");
        verify(chatFlowRepository, never()).save(any());
    }

    @Test
    void dryRunComputesPlanWithoutPersisting() {
        TurChatFlow control = variant("control", 50, null, "exp");
        TurChatFlow treatment = variant("treatment", 50, null, "exp");
        when(significanceService.evaluate(eq("exp"), any(), any(), any(), any()))
                .thenReturn(sigWithWinner("exp", "treatment", 0.02));
        when(chatFlowRepository.findByExperimentKey("exp")).thenReturn(List.of(control, treatment));

        PromotionResult result = service().promote("exp", SuccessMetric.GOAL_ACHIEVED, null, true);

        assertThat(result.applied()).isFalse();
        assertThat(result.dryRun()).isTrue();
        assertThat(result.winner()).isEqualTo("treatment");
        assertThat(result.archivedVariants()).containsExactly("control");
        assertThat(result.reason()).contains("dry-run");
        verify(chatFlowRepository, never()).save(any());
    }

    @Test
    void scheduledSweepOnlyPromotesOptedInExperiments() {
        // exp1 opted in (treatment.autoPromote=true); exp2 not opted in.
        TurChatFlow c1 = variant("control", 50, false, "exp1");
        TurChatFlow t1 = variant("treatment", 50, true, "exp1");
        TurChatFlow c2 = variant("control", 50, false, "exp2");
        TurChatFlow t2 = variant("treatment", 50, null, "exp2");
        TurChatFlow plain = variant("solo", null, null, null); // no experiment, ignored
        when(chatFlowRepository.findAll()).thenReturn(List.of(c1, t1, c2, t2, plain));
        when(significanceService.evaluate(eq("exp1"), any(), any(), any(), any()))
                .thenReturn(sigWithWinner("exp1", "treatment", 0.005));
        when(chatFlowRepository.findByExperimentKey("exp1")).thenReturn(List.of(c1, t1));

        int promoted = service().runScheduledPromotions();

        assertThat(promoted).isEqualTo(1);
        verify(chatFlowRepository).findByExperimentKey("exp1");
        verify(chatFlowRepository, never()).findByExperimentKey("exp2");
        assertThat(t1.getTrafficWeight()).isEqualTo(100);
        assertThat(c1.getTrafficWeight()).isZero();
    }
}
