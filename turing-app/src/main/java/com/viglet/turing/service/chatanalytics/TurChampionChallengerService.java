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

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.viglet.turing.persistence.model.agent.TurChatFlow;
import com.viglet.turing.persistence.repository.agent.TurChatFlowRepository;
import com.viglet.turing.service.chatanalytics.TurExperimentSignificanceService.SignificanceResult;
import com.viglet.turing.service.chatanalytics.TurExperimentSignificanceService.SuccessMetric;

import lombok.extern.slf4j.Slf4j;

/**
 * T71 / §VII.8.b — champion-challenger auto-promotion. Closes the loop on the
 * A/B stack: T68 tells the operator <i>whether</i> a variant has won, this
 * service <i>acts</i> on that verdict by pinning the winner to 100% of traffic
 * and archiving the losers — so a statistically-settled experiment converges
 * on its champion without an operator manually editing each flow's
 * {@code trafficWeight}.
 *
 * <p>Two entry points share one promotion routine:
 * <ul>
 *   <li>{@link #promote} — operator-triggered (the manual
 *       {@code POST /experiment/{key}/promote} endpoint). Always evaluates and,
 *       on a declared winner, applies the promotion regardless of the per-flow
 *       {@code autoPromote} opt-in.</li>
 *   <li>{@link #runScheduledPromotions} — the daily
 *       {@link TurChampionChallengerPromotionJob}. Only touches experiments
 *       where at least one variant has opted in via {@code autoPromote=true},
 *       honouring the project rule that behaviour changes ship as a choosable
 *       per-entity flag (default = legacy / manual).</li>
 * </ul>
 *
 * <p>"Promote" = winner {@code trafficWeight=100}; "archive" = loser
 * {@code trafficWeight=0} (excluded from fresh assignment, past sessions stay
 * analyzable) + {@code experimentEndsAt=now} (closes the loser's assignment
 * window so {@code TurChatFlowEngineService.isInExperimentWindow} returns false
 * for it). Bandit mode is cleared on every arm — once a champion is fixed there
 * is nothing left to explore. The winner keeps its existing schedule window
 * untouched.
 *
 * <p>"Winner" comes verbatim from {@link TurExperimentSignificanceService} so
 * the promotion decision uses the exact same z-test + sample-size gate the
 * dashboard banner shows: an underpowered or not-yet-significant experiment
 * yields {@code winner == null} and this service is a no-op.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
@Slf4j
@Service
public class TurChampionChallengerService {

    /** trafficWeight pinned on the promoted champion. */
    static final int CHAMPION_WEIGHT = 100;
    /** trafficWeight written to every archived (losing) arm. */
    static final int ARCHIVED_WEIGHT = 0;

    private final TurExperimentSignificanceService significanceService;
    private final TurChatFlowRepository chatFlowRepository;

    public TurChampionChallengerService(TurExperimentSignificanceService significanceService,
            TurChatFlowRepository chatFlowRepository) {
        this.significanceService = significanceService;
        this.chatFlowRepository = chatFlowRepository;
    }

    /**
     * Outcome of a promotion attempt. {@code applied} is false (with a
     * human-readable {@code reason}) whenever there is no winner to act on —
     * underpowered sample, no significant difference, unknown winner flow, or a
     * dry-run. {@code archivedVariants} lists the loser labels that were (or, in
     * dry-run, would be) archived.
     */
    public record PromotionResult(
            String experimentKey,
            String successMetric,
            boolean applied,
            boolean dryRun,
            String winner,
            String winnerFlowId,
            Double pValue,
            List<String> archivedVariants,
            String reason) {
    }

    /**
     * Evaluates significance for {@code experimentKey} and, when a winner is
     * declared, promotes it / archives the losers. Mutating — wrapped in a
     * transaction so the multi-row update commits atomically.
     *
     * @param experimentKey shared key across the experiment's variants
     * @param metric        conversion to test (null → {@code GOAL_ACHIEVED})
     * @param alpha         significance threshold (null → service default 0.05)
     * @param dryRun        when true, computes the plan but persists nothing
     */
    @Transactional
    public PromotionResult promote(String experimentKey, SuccessMetric metric, Double alpha,
            boolean dryRun) {
        SuccessMetric m = metric == null ? SuccessMetric.GOAL_ACHIEVED : metric;
        SignificanceResult sig = significanceService.evaluate(experimentKey, m, null, null, alpha);

        if (sig.winner() == null) {
            // Underpowered, no significant difference, or < 2 variants — the
            // significance service already phrased the reason for the banner.
            return notApplied(experimentKey, m, sig.pValue(), sig.recommendation());
        }

        List<TurChatFlow> variants = chatFlowRepository.findByExperimentKey(experimentKey);
        if (variants.isEmpty()) {
            return notApplied(experimentKey, m, sig.pValue(),
                    "Winner '" + sig.winner() + "' declared but no flows found for experimentKey '"
                            + experimentKey + "'.");
        }

        List<TurChatFlow> champions = new ArrayList<>();
        List<TurChatFlow> losers = new ArrayList<>();
        for (TurChatFlow flow : variants) {
            if (sig.winner().equals(flow.getVariantLabel())) {
                champions.add(flow);
            } else {
                losers.add(flow);
            }
        }
        if (champions.isEmpty()) {
            return notApplied(experimentKey, m, sig.pValue(),
                    "Winner '" + sig.winner() + "' has no matching flow under experimentKey '"
                            + experimentKey + "' (variantLabel mismatch).");
        }

        Instant now = Instant.now();
        applyChampions(champions, dryRun);
        List<String> archived = applyLosers(losers, now, dryRun);

        String winnerFlowId = champions.get(0).getId();
        String reason = String.format(
                "%sPromoted winner '%s' (flow %s) to trafficWeight=%d; archived %d loser(s): %s. (p=%s)",
                dryRun ? "[dry-run] would have " : "",
                sig.winner(), winnerFlowId, CHAMPION_WEIGHT, archived.size(), archived,
                sig.pValue() == null ? "n/a" : String.format("%.4f", sig.pValue()));
        if (dryRun) {
            log.info("[ChampionChallenger] {}", reason);
        } else {
            log.info("[ChampionChallenger] experimentKey='{}': {}", experimentKey, reason);
        }
        return new PromotionResult(experimentKey, m.name(), !dryRun, dryRun,
                sig.winner(), winnerFlowId, sig.pValue(), archived, reason);
    }

    /** Sets the winning variant(s) to champion weight, persisting unless {@code dryRun}. */
    private void applyChampions(List<TurChatFlow> champions, boolean dryRun) {
        for (TurChatFlow champion : champions) {
            champion.setTrafficWeight(CHAMPION_WEIGHT);
            champion.setBanditEnabled(Boolean.FALSE);
            if (!dryRun) {
                chatFlowRepository.save(champion);
            }
        }
    }

    /**
     * Archives the losing variant(s) (zero weight, ended now), persisting unless
     * {@code dryRun}. Returns the archived variant labels.
     */
    private List<String> applyLosers(List<TurChatFlow> losers, Instant now, boolean dryRun) {
        List<String> archived = new ArrayList<>();
        for (TurChatFlow loser : losers) {
            loser.setTrafficWeight(ARCHIVED_WEIGHT);
            loser.setBanditEnabled(Boolean.FALSE);
            loser.setExperimentEndsAt(now);
            archived.add(loser.getVariantLabel());
            if (!dryRun) {
                chatFlowRepository.save(loser);
            }
        }
        return archived;
    }

    /**
     * Daily scan: promotes every opted-in experiment that has reached
     * significance. An experiment is eligible when at least one of its variants
     * has {@code autoPromote=true} (same "any variant flips the whole group"
     * semantics as bandit mode). Returns the number of experiments where a
     * promotion was actually applied.
     */
    @Transactional
    public int runScheduledPromotions() {
        Set<String> eligibleKeys = new LinkedHashSet<>();
        for (TurChatFlow flow : chatFlowRepository.findAll()) {
            String key = flow.getExperimentKey();
            if (Boolean.TRUE.equals(flow.getAutoPromote()) && key != null && !key.isBlank()) {
                eligibleKeys.add(key);
            }
        }
        int promoted = 0;
        for (String key : eligibleKeys) {
            try {
                PromotionResult result = promote(key, SuccessMetric.GOAL_ACHIEVED, null, false);
                if (result.applied()) {
                    promoted++;
                }
            } catch (RuntimeException e) {
                // One experiment's failure (analytics hiccup, etc.) must not
                // abort the rest of the daily sweep.
                log.warn("[ChampionChallenger] auto-promotion failed for experimentKey='{}': {}",
                        key, e.getMessage());
            }
        }
        return promoted;
    }

    private static PromotionResult notApplied(String experimentKey, SuccessMetric metric,
            Double pValue, String reason) {
        return new PromotionResult(experimentKey, metric.name(), false, false,
                null, null, pValue, List.of(), reason);
    }
}
