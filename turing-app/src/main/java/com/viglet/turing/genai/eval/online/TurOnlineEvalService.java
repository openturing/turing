/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.eval.online;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import com.viglet.turing.genai.citation.TurCitationDriftService;
import com.viglet.turing.genai.eval.TurAgentEvalRunnerService;
import com.viglet.turing.genai.eval.grader.TurEvalGrader;
import com.viglet.turing.genai.eval.grader.TurEvalGraderConfigView;
import com.viglet.turing.genai.eval.grader.TurEvalGraderRegistry;
import com.viglet.turing.genai.eval.grader.TurEvalGraderResult;
import com.viglet.turing.genai.eval.grader.TurEvalGradingContext;
import com.viglet.turing.genai.eval.grader.TurEvalResolvedGrader;
import com.viglet.turing.genai.eval.grader.TurEvalStackAggregator;
import com.viglet.turing.genai.selftuning.TurSelfTuningMiner;
import com.viglet.turing.persistence.dto.agent.TurChatSessionMessageDto;
import com.viglet.turing.persistence.dto.agent.TurChatSessionMessagesDto;
import com.viglet.turing.persistence.dto.agent.TurOnlineEvalSnapshotDto;
import com.viglet.turing.persistence.model.agent.TurAIAgent;
import com.viglet.turing.persistence.model.agent.TurAgentEvalCase;
import com.viglet.turing.persistence.model.agent.TurAgentEvalExpectedOutcome;
import com.viglet.turing.persistence.model.agent.TurAgentEvalSet;
import com.viglet.turing.persistence.model.agent.TurChatCitationRecord;
import com.viglet.turing.persistence.model.agent.TurChatFlowSubmission;
import com.viglet.turing.persistence.model.agent.TurOnlineEvalSnapshot;
import com.viglet.turing.persistence.repository.agent.TurAIAgentRepository;
import com.viglet.turing.persistence.repository.agent.TurChatFlowSubmissionRepository;
import com.viglet.turing.persistence.repository.agent.TurOnlineEvalSnapshotRepository;
import com.viglet.turing.properties.TurConfigProperties;
import com.viglet.turing.properties.TurGenAiProperty.TurGenAiOnlineEvalProperty;
import com.viglet.turing.service.chatanalytics.TurChatAnalyticsStore;
import com.viglet.turing.service.chatanalytics.TurChatSessionFilter;
import com.viglet.turing.service.chatmemory.TurChatMemoryService;

import lombok.extern.slf4j.Slf4j;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/**
 * T603 / §XXXIII.18 — continuous / online eval. Samples an agent's recent
 * <b>live production traffic</b>, grades it in the background with the same
 * pluggable CODE + MODEL grader stack the pre-publish runner uses (but with
 * <b>no replay</b> — the answer already happened), and records a rolling quality
 * {@link TurOnlineEvalSnapshot} that flags <b>drift</b> versus a healthy
 * baseline.
 *
 * <p>It composes three existing post-hoc signals into one snapshot:
 * <ul>
 *   <li><b>T87 sentiment</b> — the fraction of sampled sessions labelled
 *       NEGATIVE / FRUSTRATED;</li>
 *   <li><b>T155 citation drift</b> — the fraction of sampled sessions carrying
 *       at least one stale citation (only when drift detection is on);</li>
 *   <li><b>T447 miner</b> — the fraction of sampled sessions the failing-session
 *       heuristic classes as a failure, reusing {@link
 *       TurSelfTuningMiner#findFailingSessions} verbatim.</li>
 * </ul>
 *
 * <p>Everything is opt-in and fail-open: a disabled analytics store, no recent
 * sessions, a missing LLM, or any error yields a non-persisted "unavailable"
 * snapshot ({@link TurOnlineEvalSnapshotDto#unavailable}) rather than an
 * exception on the schedule. Grader scoring only runs when the agent binds an
 * {@code onlineEvalGraderStackId}; otherwise the snapshot carries only the three
 * signal rates. The first persisted snapshot per agent is the drift baseline.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Slf4j
@Service
public class TurOnlineEvalService {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final Set<String> NEGATIVE_SENTIMENTS = Set.of("NEGATIVE", "FRUSTRATED");

    private final TurAIAgentRepository agentRepository;
    private final TurOnlineEvalSnapshotRepository snapshotRepository;
    private final TurChatAnalyticsStore analyticsStore;
    private final TurChatMemoryService chatMemoryService;
    private final TurChatFlowSubmissionRepository submissionRepository;
    private final TurSelfTuningMiner failureMiner;
    private final TurCitationDriftService citationDriftService;
    private final TurEvalGraderRegistry graderRegistry;
    private final TurAgentEvalRunnerService runnerService;
    private final TurConfigProperties configProperties;

    public TurOnlineEvalService(TurAIAgentRepository agentRepository,
            TurOnlineEvalSnapshotRepository snapshotRepository,
            TurChatAnalyticsStore analyticsStore,
            TurChatMemoryService chatMemoryService,
            TurChatFlowSubmissionRepository submissionRepository,
            TurSelfTuningMiner failureMiner,
            TurCitationDriftService citationDriftService,
            TurEvalGraderRegistry graderRegistry,
            TurAgentEvalRunnerService runnerService,
            TurConfigProperties configProperties) {
        this.agentRepository = agentRepository;
        this.snapshotRepository = snapshotRepository;
        this.analyticsStore = analyticsStore;
        this.chatMemoryService = chatMemoryService;
        this.submissionRepository = submissionRepository;
        this.failureMiner = failureMiner;
        this.citationDriftService = citationDriftService;
        this.graderRegistry = graderRegistry;
        this.runnerService = runnerService;
        this.configProperties = configProperties;
    }

    private TurGenAiOnlineEvalProperty props() {
        return configProperties.getGenai().getOnlineEval();
    }

    // ─────────────────────────── Public API ───────────────────────────

    /**
     * Samples the agent's recent live sessions, grades them, detects drift, and
     * persists a snapshot. Fail-open: returns a non-persisted "unavailable"
     * snapshot when there's nothing to grade or anything goes wrong. Called by
     * both the scheduled sweep and the on-demand "run now" API.
     */
    public TurOnlineEvalSnapshotDto sampleAndGrade(String agentId) {
        Instant now = Instant.now();
        TurAIAgent agent = agentRepository.findById(agentId).orElse(null);
        if (agent == null) {
            return TurOnlineEvalSnapshotDto.unavailable(agentId, now, "Agent not found");
        }
        if (!analyticsStore.isEnabled()) {
            return TurOnlineEvalSnapshotDto.unavailable(agentId, now,
                    "Chat-analytics store disabled — no live traffic to sample");
        }
        try {
            return doSampleAndGrade(agent, now);
        } catch (RuntimeException e) {
            log.warn("[OnlineEval] sweep failed for agent={}: {} — recording nothing",
                    agentId, e.getMessage());
            return TurOnlineEvalSnapshotDto.unavailable(agentId, now, "Error: " + e.getMessage());
        }
    }

    /**
     * Newest snapshots first, capped at {@code limit} — powers the Studio
     * timeline / run list.
     */
    public List<TurOnlineEvalSnapshotDto> history(String agentId, int limit) {
        return snapshotRepository
                .findByAgentIdOrderByCreatedAtDesc(agentId, PageRequest.of(0, Math.max(1, limit)))
                .stream().map(TurOnlineEvalSnapshotDto::of).toList();
    }

    /** The single most recent snapshot, or empty when none has been recorded. */
    public Optional<TurOnlineEvalSnapshotDto> latest(String agentId) {
        return snapshotRepository.findFirstByAgentIdOrderByCreatedAtDesc(agentId)
                .map(TurOnlineEvalSnapshotDto::of);
    }

    /**
     * Promote a snapshot to the agent's healthy baseline (demoting any prior
     * baseline) — the "re-baseline after a fix" control. Returns the promoted
     * snapshot view, or empty when the id doesn't belong to the agent.
     */
    public Optional<TurOnlineEvalSnapshotDto> promoteBaseline(String agentId, String snapshotId) {
        TurOnlineEvalSnapshot target = snapshotRepository.findById(snapshotId).orElse(null);
        if (target == null || !agentId.equals(target.getAgentId())) {
            return Optional.empty();
        }
        snapshotRepository.findByAgentIdAndBaselineTrue(agentId).forEach(prev -> {
            if (!prev.getId().equals(snapshotId)) {
                prev.setBaseline(false);
                snapshotRepository.save(prev);
            }
        });
        target.setBaseline(true);
        // Re-baselining declares the window healthy again: clear its drift flag.
        target.setDriftDetected(false);
        target.setDriftReason(null);
        return Optional.of(TurOnlineEvalSnapshotDto.of(snapshotRepository.save(target)));
    }

    // ─────────────────────────── Sweep ───────────────────────────

    private TurOnlineEvalSnapshotDto doSampleAndGrade(TurAIAgent agent, Instant now) {
        TurGenAiOnlineEvalProperty cfg = props();
        Instant from = now.minus(Duration.ofDays(Math.max(1, cfg.getWindowDays())));
        List<Map<String, Object>> sessions = analyticsStore.findRecentSessions(from, now,
                new TurChatSessionFilter(agent.getId(), null, null, null, null, null),
                Math.max(1, cfg.getSampleSize()));
        if (sessions.isEmpty()) {
            return TurOnlineEvalSnapshotDto.unavailable(agent.getId(), now,
                    "No recent sessions in the sampling window");
        }
        int sampled = sessions.size();

        // (a) T87 sentiment rate — session-level label.
        long negative = sessions.stream()
                .filter(s -> NEGATIVE_SENTIMENTS.contains(str(s.get("sentiment")).toUpperCase()))
                .count();
        double negativeSentimentRate = ratio(negative, sampled);

        // (b) T447 failing-session rate — reuse the miner's exact heuristic.
        Set<String> failingIds = failureMiner.findFailingSessions(agent.getId()).stream()
                .map(s -> str(s.get("conversationId")))
                .filter(id -> !id.isEmpty())
                .collect(Collectors.toSet());
        long failing = sessions.stream()
                .filter(s -> failingIds.contains(str(s.get("conversationId"))))
                .count();
        double failingSessionRate = ratio(failing, sampled);

        // (c) T155 stale-citation rate — only when drift detection is enabled.
        double staleCitationRate = citationStaleRate(sessions, sampled);

        // (d) grade the live conversations with the bound grader stack, if any.
        GradeResult graded = gradeSessions(agent, sessions);

        TurOnlineEvalSnapshot snapshot = new TurOnlineEvalSnapshot();
        snapshot.setAgentId(agent.getId());
        snapshot.setCreatedAt(now);
        snapshot.setWindowStart(from);
        snapshot.setWindowEnd(now);
        snapshot.setSampledSessions(sampled);
        snapshot.setGradedSessions(graded.gradedSessions());
        snapshot.setGraderStackId(graded.stackId());
        snapshot.setMeanScore(graded.meanScore());
        snapshot.setPassRate(graded.passRate());
        snapshot.setNegativeSentimentRate(negativeSentimentRate);
        snapshot.setStaleCitationRate(staleCitationRate);
        snapshot.setFailingSessionRate(failingSessionRate);

        // Drift vs the healthy baseline (bootstrap the first snapshot as baseline).
        Optional<TurOnlineEvalSnapshot> baseline =
                snapshotRepository.findFirstByAgentIdAndBaselineTrueOrderByCreatedAtDesc(agent.getId());
        if (baseline.isEmpty()) {
            snapshot.setBaseline(true);
        } else if (sampled >= cfg.getMinSamplesForDrift()) {
            String reason = detectDrift(baseline.get(), snapshot, cfg);
            if (reason != null) {
                snapshot.setDriftDetected(true);
                snapshot.setDriftReason(reason);
            }
        }

        TurOnlineEvalSnapshot saved = snapshotRepository.save(snapshot);
        if (saved.isDriftDetected()) {
            log.warn("[OnlineEval] DRIFT for agent={} ({} sampled): {}",
                    agent.getId(), sampled, saved.getDriftReason());
        } else {
            log.info("[OnlineEval] agent={} sampled={} graded={} meanScore={} negSent={} failing={}",
                    agent.getId(), sampled, graded.gradedSessions(),
                    fmt(graded.meanScore()), fmt(negativeSentimentRate), fmt(failingSessionRate));
        }
        return TurOnlineEvalSnapshotDto.of(saved);
    }

    private double citationStaleRate(List<Map<String, Object>> sessions, int sampled) {
        if (!citationDriftService.isEnabled()) {
            return -1d;
        }
        long stale = 0;
        for (Map<String, Object> s : sessions) {
            String conversationId = str(s.get("conversationId"));
            if (conversationId.isEmpty()) {
                continue;
            }
            boolean anyStale = citationDriftService.findByConversation(conversationId).stream()
                    .anyMatch(TurChatCitationRecord::isCitationStale);
            if (anyStale) {
                stale++;
            }
        }
        return ratio(stale, sampled);
    }

    // ─────────────────────────── Live grading (no replay) ───────────────────────────

    /** Aggregate grading outcome across the sampled window. */
    private record GradeResult(String stackId, int gradedSessions, double meanScore, double passRate) {
        static GradeResult none() {
            return new GradeResult(null, 0, -1d, -1d);
        }
    }

    private GradeResult gradeSessions(TurAIAgent agent, List<Map<String, Object>> sessions) {
        String stackId = agent.getOnlineEvalGraderStackId();
        if (stackId == null || stackId.isBlank()) {
            return GradeResult.none();
        }
        ChatModel judgeModel = runnerService.resolveJudgeModel(agent);
        // A transient set carrying only the grader-stack binding — resolveStack
        // reads getGraderStackId() and loads the stack's configs (T600), exactly
        // as the T602 ad-hoc dataset run does.
        TurAgentEvalSet set = new TurAgentEvalSet();
        set.setName("online:" + agent.getId());
        set.setEnabled(1);
        set.setGraderStackId(stackId);
        set.setTurAIAgent(agent);

        double scoreSum = 0d;
        int graded = 0;
        int passed = 0;
        for (Map<String, Object> session : sessions) {
            String conversationId = str(session.get("conversationId"));
            if (conversationId.isEmpty()) {
                continue;
            }
            Optional<TurEvalStackAggregator.Aggregate> aggregate =
                    gradeOne(agent, set, conversationId, str(session.get("outcome")), judgeModel);
            if (aggregate.isEmpty()) {
                continue; // no applicable grader for this live conversation
            }
            graded++;
            scoreSum += aggregate.get().score();
            if (aggregate.get().passed()) {
                passed++;
            }
        }
        if (graded == 0) {
            return new GradeResult(stackId, 0, -1d, -1d);
        }
        return new GradeResult(stackId, graded, scoreSum / graded, ratio(passed, graded));
    }

    /**
     * Grades one live conversation by building a {@link TurEvalGradingContext}
     * from its already-produced turns / slots / outcome (no replay) and running
     * the resolved grader stack. Returns empty when no grader applied (e.g. an
     * expectation-based grader on live traffic that carries no golden
     * expectation) so the session is excluded from the graded-rate denominator.
     */
    private Optional<TurEvalStackAggregator.Aggregate> gradeOne(TurAIAgent agent, TurAgentEvalSet set,
            String conversationId, String outcome, ChatModel judgeModel) {
        List<String> userTurns = new ArrayList<>();
        List<String> assistantReplies = new ArrayList<>();
        TurChatSessionMessagesDto messages =
                chatMemoryService.listMessages(conversationId, props().getMaxTurnsPerSession());
        if (messages != null && messages.messages() != null) {
            for (TurChatSessionMessageDto m : messages.messages()) {
                if ("assistant".equalsIgnoreCase(m.role())) {
                    assistantReplies.add(m.content() == null ? "" : m.content());
                } else if ("user".equalsIgnoreCase(m.role())) {
                    userTurns.add(m.content() == null ? "" : m.content());
                }
            }
        }
        if (assistantReplies.isEmpty()) {
            return Optional.empty(); // nothing the content graders can score
        }

        Map<String, String> capturedSlots = new LinkedHashMap<>();
        String finalNodeId = null;
        List<TurChatFlowSubmission> subs =
                submissionRepository.findByConversationIdOrderByCompletedAtDesc(conversationId);
        if (!subs.isEmpty()) {
            TurChatFlowSubmission sub = subs.get(0);
            finalNodeId = sub.getEndNodeId();
            capturedSlots.putAll(parseSlots(sub.getVariablesJson()));
        }

        // A transient case with no golden expectation: expectation-based graders
        // (slot / outcome / node) will not applyTo, which is the intended
        // behaviour for un-annotated live traffic.
        TurAgentEvalCase liveCase = new TurAgentEvalCase();
        liveCase.setId(conversationId);
        liveCase.setName(conversationId);
        liveCase.setExpectedOutcome(TurAgentEvalExpectedOutcome.ANY);

        String actualOutcome = outcome == null || outcome.isBlank()
                ? TurAgentEvalExpectedOutcome.ANY.name() : outcome;
        TurEvalGradingContext ctx = new TurEvalGradingContext(liveCase, conversationId, userTurns,
                assistantReplies, capturedSlots, finalNodeId, actualOutcome, judgeModel, null);

        List<TurEvalStackAggregator.Contribution> contributions = new ArrayList<>();
        for (TurEvalResolvedGrader resolved : graderRegistry.resolveStack(set, liveCase)) {
            TurEvalGrader grader = resolved.grader();
            TurEvalGraderConfigView config = resolved.config();
            if (!grader.appliesTo(ctx, config)) {
                continue;
            }
            TurEvalGraderResult result = grader.grade(ctx, config);
            if (result.deferred()) {
                // Online eval never parks human-review tasks — skip the deferral.
                continue;
            }
            contributions.add(new TurEvalStackAggregator.Contribution(result.score(), result.passed(),
                    config.weight(), config.threshold(), config.blocking() == 1));
        }
        if (contributions.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(TurEvalStackAggregator.aggregate(contributions));
    }

    // ─────────────────────────── Drift policy ───────────────────────────

    /**
     * Compares this window against the healthy baseline and returns a
     * semicolon-joined reason list, or {@code null} when no drift trigger fired.
     * Relative triggers (score / pass-rate drop) only fire when both windows
     * actually graded; the sentiment / citation / failing triggers are absolute.
     */
    private String detectDrift(TurOnlineEvalSnapshot baseline, TurOnlineEvalSnapshot current,
            TurGenAiOnlineEvalProperty cfg) {
        List<String> reasons = new ArrayList<>();
        if (baseline.getMeanScore() >= 0 && current.getMeanScore() >= 0
                && current.getMeanScore() <= baseline.getMeanScore() - cfg.getScoreDropThreshold()) {
            reasons.add(String.format("mean score %s down from baseline %s",
                    fmt(current.getMeanScore()), fmt(baseline.getMeanScore())));
        }
        if (baseline.getPassRate() >= 0 && current.getPassRate() >= 0
                && current.getPassRate() <= baseline.getPassRate() - cfg.getPassRateDropThreshold()) {
            reasons.add(String.format("pass rate %s down from baseline %s",
                    fmt(current.getPassRate()), fmt(baseline.getPassRate())));
        }
        if (current.getNegativeSentimentRate() >= cfg.getNegativeSentimentThreshold()) {
            reasons.add(String.format("negative sentiment %s ≥ %s",
                    fmt(current.getNegativeSentimentRate()), fmt(cfg.getNegativeSentimentThreshold())));
        }
        if (current.getStaleCitationRate() >= 0
                && current.getStaleCitationRate() >= cfg.getStaleCitationThreshold()) {
            reasons.add(String.format("stale citations %s ≥ %s",
                    fmt(current.getStaleCitationRate()), fmt(cfg.getStaleCitationThreshold())));
        }
        if (current.getFailingSessionRate() >= cfg.getFailingSessionThreshold()) {
            reasons.add(String.format("failing sessions %s ≥ %s",
                    fmt(current.getFailingSessionRate()), fmt(cfg.getFailingSessionThreshold())));
        }
        return reasons.isEmpty() ? null : String.join("; ", reasons);
    }

    // ─────────────────────────── Helpers ───────────────────────────

    private static Map<String, String> parseSlots(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            Map<String, Object> raw = OBJECT_MAPPER.readValue(json, new TypeReference<>() {});
            if (raw == null) {
                return Map.of();
            }
            Map<String, String> slots = new LinkedHashMap<>();
            raw.forEach((k, v) -> slots.put(k, v == null ? null : v.toString()));
            return slots;
        } catch (RuntimeException e) {
            return Map.of();
        }
    }

    private static double ratio(long numerator, int denominator) {
        return denominator <= 0 ? 0d : (double) numerator / denominator;
    }

    private static String str(Object o) {
        return o == null ? "" : o.toString();
    }

    private static String fmt(double v) {
        return String.format(java.util.Locale.ROOT, "%.2f", v);
    }
}
