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

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.lucene.analysis.Analyzer;
import org.springframework.stereotype.Service;

import com.viglet.turing.persistence.dto.agent.TurChatFlowTriggerConflictDto;
import com.viglet.turing.persistence.model.agent.TurChatFlow;
import com.viglet.turing.persistence.repository.agent.TurChatFlowRepository;

/**
 * T91 / §VII.11.a — surfaces pairs of chat flows whose
 * {@code triggerDescription} stems overlap enough that the procedural router
 * cannot reliably pick one without falling back to the LLM.
 *
 * <p>The router (see {@link TurChatFlowEngineService#tryProceduralRoute(java.util.List, String)})
 * scores user messages against each flow's trigger description with Lucene
 * MoreLikeThis/BM25; when two flows score within
 * {@code PROCEDURAL_DOMINANCE_RATIO} (1.5×) of each other on the same query,
 * the router defers to the LLM. That's safe but silent — admins have no way
 * to know two flows compete for the same user intent until a customer
 * complains. This service runs offline (admin-triggered, once per page load)
 * and emits the conflicting pairs so the chat-flow editor can warn upfront.
 *
 * <p>Algorithm — Jaccard similarity on stemmed token sets:
 * <ol>
 *   <li>Pull every enabled flow on the agent with a non-blank
 *       {@code triggerDescription}.</li>
 *   <li>Group flows by the analyzer the router would pick for each
 *       ({@link TurChatFlowEngineService#analyzerFor}) — PT and EN buckets
 *       never compare against each other because the router never scores
 *       them in the same MLT pass.</li>
 *   <li>For each unordered pair {@code (A, B)} in a bucket, tokenize both
 *       descriptions through the bucket's analyzer (same pipeline as the
 *       router → identical stems / stopwords) and compute
 *       {@code |A∩B| / |A∪B|}.</li>
 *   <li>Emit a {@link TurChatFlowTriggerConflictDto} when the pair clears
 *       {@link #WARNING_JACCARD} (with at least {@link #WARNING_INTERSECTION}
 *       shared stems to suppress short-description noise). Severity
 *       escalates to {@code HIGH} above {@link #HIGH_JACCARD}.</li>
 * </ol>
 *
 * <p><b>Why Jaccard and not MoreLikeThis pairwise?</b> MLT scores depend on
 * the doc-frequency distribution of the corpus, which changes whenever the
 * admin saves another flow. Jaccard on the analyzer's stem pipeline is
 * deterministic per-pair (depends only on the two descriptions), so the
 * warning stays stable until the admin actually edits one side — exactly
 * the property an "edit-time hint" needs. The router itself still uses MLT
 * at runtime where the corpus is fixed for the duration of a turn.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
@Service
public class TurTriggerConflictService {

    /**
     * Minimum Jaccard similarity to surface a conflict at all. Below this the
     * pair shares at most incidental stopwords-survivors — false positives
     * dominate. Empirically tuned against the Education customer's tri-flow
     * (B2C / B2B / micro-lesson): the legitimate overlap there scores
     * ~0.10-0.15, the genuine ambiguous pair from a customer demo scored
     * 0.34. 0.20 leaves the legitimate split untouched while catching real
     * collisions.
     */
    static final double WARNING_JACCARD = 0.20;

    /**
     * Jaccard threshold for the louder {@code HIGH} severity. Above this the
     * procedural router cannot reliably separate the pair on most user
     * messages because the dominance check ({@code 1.5×} the runner-up)
     * collapses when both flows hit nearly the same stem set.
     */
    static final double HIGH_JACCARD = 0.40;

    /**
     * Suppress conflicts where the intersection is small enough that a
     * single shared stem can swing Jaccard above {@link #WARNING_JACCARD}.
     * A 4-stem description sharing 1 stem with a 4-stem peer scores 0.14
     * naturally, but pathological 2-stem descriptions can score 0.33 on a
     * single accidental match — three shared stems is the smallest signal
     * the admin should plausibly act on.
     */
    static final int WARNING_INTERSECTION = 3;

    /**
     * Cap on overlapping stems sent to the UI. The admin needs the actionable
     * vocabulary, not a full set dump — eight stems comfortably fit in a
     * single-line hint without truncating the meaningful ones.
     */
    private static final int MAX_OVERLAPPING_TOKENS = 8;

    private final TurChatFlowRepository chatFlowRepository;

    public TurTriggerConflictService(TurChatFlowRepository chatFlowRepository) {
        this.chatFlowRepository = chatFlowRepository;
    }

    /**
     * Returns every conflicting pair of trigger descriptions for the agent,
     * ordered by descending similarity (loudest first). Empty list when the
     * agent has fewer than two enabled flows with a trigger description or
     * when no pair clears {@link #WARNING_JACCARD}.
     */
    public List<TurChatFlowTriggerConflictDto> detect(String agentId) {
        if (agentId == null || agentId.isBlank()) {
            return List.of();
        }
        List<TurChatFlow> flows = chatFlowRepository.findByTurAIAgent_IdOrderByNameAsc(agentId);
        if (flows == null || flows.size() < 2) {
            return List.of();
        }
        // Group by analyzer so PT/EN pairs do not get compared — the
        // procedural router never scores them together either.
        Map<Analyzer, List<EligibleFlow>> byAnalyzer = new HashMap<>(2);
        for (TurChatFlow flow : flows) {
            if (flow.getEnabled() != 1) {
                continue;
            }
            String description = flow.getTriggerDescription();
            if (description == null || description.isBlank()) {
                continue;
            }
            Analyzer analyzer = TurChatFlowEngineService.analyzerFor(
                    flow.getTriggerLanguage(), description);
            Set<String> tokens = TurChatFlowEngineService.tokenize(description, analyzer);
            if (tokens.isEmpty()) {
                continue;
            }
            byAnalyzer.computeIfAbsent(analyzer, ignored -> new ArrayList<>())
                    .add(new EligibleFlow(flow, tokens, analyzer));
        }

        List<TurChatFlowTriggerConflictDto> conflicts = new ArrayList<>();
        for (List<EligibleFlow> bucket : byAnalyzer.values()) {
            if (bucket.size() < 2) {
                continue;
            }
            for (int i = 0; i < bucket.size(); i++) {
                for (int j = i + 1; j < bucket.size(); j++) {
                    TurChatFlowTriggerConflictDto conflict = scorePair(bucket.get(i), bucket.get(j));
                    if (conflict != null) {
                        conflicts.add(conflict);
                    }
                }
            }
        }
        conflicts.sort(Comparator
                .comparingDouble(TurChatFlowTriggerConflictDto::similarity).reversed()
                .thenComparing(TurChatFlowTriggerConflictDto::flowAId)
                .thenComparing(TurChatFlowTriggerConflictDto::flowBId));
        return conflicts;
    }

    private static TurChatFlowTriggerConflictDto scorePair(EligibleFlow a, EligibleFlow b) {
        Set<String> intersection = new HashSet<>(a.tokens());
        intersection.retainAll(b.tokens());
        if (intersection.size() < WARNING_INTERSECTION) {
            return null;
        }
        Set<String> union = new HashSet<>(a.tokens());
        union.addAll(b.tokens());
        if (union.isEmpty()) {
            return null;
        }
        double jaccard = (double) intersection.size() / (double) union.size();
        if (jaccard < WARNING_JACCARD) {
            return null;
        }
        String severity = jaccard >= HIGH_JACCARD ? "HIGH" : "WARNING";
        List<String> overlapping = topOverlapping(intersection);
        // Order pair deterministically by id so the UI sees one entry per
        // unordered pair regardless of admin sort order on the flow list.
        EligibleFlow first;
        EligibleFlow second;
        if (a.flow().getId() == null
                || (b.flow().getId() != null
                        && a.flow().getId().compareTo(b.flow().getId()) <= 0)) {
            first = a;
            second = b;
        } else {
            first = b;
            second = a;
        }
        String suggestion = buildSuggestion(first.analyzer(), overlapping);
        return new TurChatFlowTriggerConflictDto(
                first.flow().getId(),
                safeName(first.flow()),
                second.flow().getId(),
                safeName(second.flow()),
                severity,
                jaccard,
                intersection.size(),
                overlapping,
                suggestion);
    }

    private static List<String> topOverlapping(Set<String> intersection) {
        return intersection.stream()
                .sorted(Comparator.comparingInt(String::length).reversed()
                        .thenComparing(Comparator.naturalOrder()))
                .limit(MAX_OVERLAPPING_TOKENS)
                .toList();
    }

    private static String buildSuggestion(Analyzer analyzer, List<String> overlapping) {
        // Match the analyzer's language so the hint reads naturally next to
        // the description the admin just authored. The PT analyzer is the
        // platform default (legacy + AUTO fallback) so it wins ties.
        boolean english = analyzer != null
                && analyzer.getClass().getSimpleName().toLowerCase().contains("english");
        String joined = String.join(", ", overlapping);
        if (english) {
            return "Both flows share the terms: " + joined
                    + ". Add distinguishing vocabulary, or a 'DO NOT ACTIVATE when…' clause"
                    + " to the more specific flow, so the procedural router can separate them.";
        }
        return "Os dois fluxos compartilham os termos: " + joined
                + ". Adicione palavras distintivas, ou uma cláusula 'NÃO ATIVAR para…'"
                + " no fluxo mais específico, para que o roteador procedural consiga separá-los.";
    }

    private static String safeName(TurChatFlow flow) {
        return flow.getName() == null || flow.getName().isBlank()
                ? flow.getId()
                : flow.getName();
    }

    /** Internal carrier: keeps each flow paired with its tokens + analyzer. */
    private record EligibleFlow(TurChatFlow flow, Set<String> tokens, Analyzer analyzer) {
    }
}
