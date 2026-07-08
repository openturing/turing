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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.viglet.turing.genai.citation.TurCitationDriftService;
import com.viglet.turing.genai.eval.TurAgentEvalRunnerService;
import com.viglet.turing.genai.eval.grader.TurEvalGrader;
import com.viglet.turing.genai.eval.grader.TurEvalGraderConfigView;
import com.viglet.turing.genai.eval.grader.TurEvalGraderKind;
import com.viglet.turing.genai.eval.grader.TurEvalGraderRegistry;
import com.viglet.turing.genai.eval.grader.TurEvalGraderResult;
import com.viglet.turing.genai.eval.grader.TurEvalGradingContext;
import com.viglet.turing.genai.eval.grader.TurEvalResolvedGrader;
import com.viglet.turing.genai.selftuning.TurSelfTuningMiner;
import com.viglet.turing.persistence.dto.agent.TurChatSessionMessageDto;
import com.viglet.turing.persistence.dto.agent.TurChatSessionMessagesDto;
import com.viglet.turing.persistence.dto.agent.TurOnlineEvalSnapshotDto;
import com.viglet.turing.persistence.model.agent.TurAIAgent;
import com.viglet.turing.persistence.model.agent.TurOnlineEvalSnapshot;
import com.viglet.turing.persistence.repository.agent.TurAIAgentRepository;
import com.viglet.turing.persistence.repository.agent.TurChatFlowSubmissionRepository;
import com.viglet.turing.persistence.repository.agent.TurOnlineEvalSnapshotRepository;
import com.viglet.turing.properties.TurConfigProperties;
import com.viglet.turing.properties.TurGenAiProperty;
import com.viglet.turing.service.chatanalytics.TurChatAnalyticsStore;

/**
 * T603 / §XXXIII.18 — continuous / online eval. Verifies the opt-in / fail-open
 * posture, the three composed signal rates (T87 sentiment, T155 citation, T447
 * failing sessions), the no-replay live grading path, drift detection versus a
 * baseline, and re-baselining.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@ExtendWith(MockitoExtension.class)
class TurOnlineEvalServiceTest {

    private static final String AGENT_ID = "agent-1";

    @Mock private TurAIAgentRepository agentRepository;
    @Mock private TurOnlineEvalSnapshotRepository snapshotRepository;
    @Mock private TurChatAnalyticsStore analyticsStore;
    @Mock private com.viglet.turing.service.chatmemory.TurChatMemoryService chatMemoryService;
    @Mock private TurChatFlowSubmissionRepository submissionRepository;
    @Mock private TurSelfTuningMiner failureMiner;
    @Mock private TurCitationDriftService citationDriftService;
    @Mock private TurEvalGraderRegistry graderRegistry;
    @Mock private TurAgentEvalRunnerService runnerService;
    @Mock private TurConfigProperties configProperties;

    private final TurGenAiProperty genAiProperty = new TurGenAiProperty();

    private TurOnlineEvalService service;

    @BeforeEach
    void setUp() {
        lenient().when(configProperties.getGenai()).thenReturn(genAiProperty);
        lenient().when(snapshotRepository.save(any())).thenAnswer(inv -> {
            TurOnlineEvalSnapshot s = inv.getArgument(0);
            if (s.getId() == null) {
                s.setId("snap-" + System.identityHashCode(s));
            }
            return s;
        });
        service = new TurOnlineEvalService(agentRepository, snapshotRepository, analyticsStore,
                chatMemoryService, submissionRepository, failureMiner, citationDriftService,
                graderRegistry, runnerService, configProperties);
    }

    // ─────────────────────────── Fail-open / opt-in ───────────────────────────

    @Test
    void analyticsStoreDisabled_returnsUnavailable_persistsNothing() {
        when(agentRepository.findById(AGENT_ID)).thenReturn(Optional.of(agent(null)));
        when(analyticsStore.isEnabled()).thenReturn(false);

        TurOnlineEvalSnapshotDto dto = service.sampleAndGrade(AGENT_ID);

        assertThat(dto.id()).isNull();
        assertThat(dto.note()).contains("disabled");
        verify(snapshotRepository, never()).save(any());
    }

    @Test
    void noRecentSessions_returnsUnavailable() {
        when(agentRepository.findById(AGENT_ID)).thenReturn(Optional.of(agent(null)));
        when(analyticsStore.isEnabled()).thenReturn(true);
        when(analyticsStore.findRecentSessions(any(), any(), any(), anyInt())).thenReturn(List.of());

        TurOnlineEvalSnapshotDto dto = service.sampleAndGrade(AGENT_ID);

        assertThat(dto.note()).contains("No recent sessions");
        verify(snapshotRepository, never()).save(any());
    }

    @Test
    void agentNotFound_returnsUnavailable() {
        when(agentRepository.findById(AGENT_ID)).thenReturn(Optional.empty());

        assertThat(service.sampleAndGrade(AGENT_ID).note()).contains("not found");
    }

    // ─────────────────────────── Signal rates + baseline bootstrap ───────────────────────────

    @Test
    void firstSnapshot_becomesBaseline_computesSentimentAndFailingRates() {
        when(agentRepository.findById(AGENT_ID)).thenReturn(Optional.of(agent(null)));
        when(analyticsStore.isEnabled()).thenReturn(true);
        when(analyticsStore.findRecentSessions(any(), any(), any(), anyInt())).thenReturn(List.of(
                session("c1", "POSITIVE", "CAPTURED"),
                session("c2", "NEGATIVE", "ABANDONED"),
                session("c3", "FRUSTRATED", "HANDOFF"),
                session("c4", "NEUTRAL", "CAPTURED")));
        // T447 miner classes c2 as failing (one of four).
        when(failureMiner.findFailingSessions(AGENT_ID))
                .thenReturn(List.of(Map.of("conversationId", "c2")));
        when(citationDriftService.isEnabled()).thenReturn(false);
        when(snapshotRepository.findFirstByAgentIdAndBaselineTrueOrderByCreatedAtDesc(AGENT_ID))
                .thenReturn(Optional.empty());

        TurOnlineEvalSnapshotDto dto = service.sampleAndGrade(AGENT_ID);

        assertThat(dto.sampledSessions()).isEqualTo(4);
        assertThat(dto.negativeSentimentRate()).isEqualTo(0.5); // c2 + c3
        assertThat(dto.failingSessionRate()).isEqualTo(0.25);   // c2
        assertThat(dto.staleCitationRate()).isEqualTo(-1d);     // drift detection off
        assertThat(dto.meanScore()).isEqualTo(-1d);             // no grader stack bound
        assertThat(dto.baseline()).isTrue();
        assertThat(dto.driftDetected()).isFalse();
    }

    // ─────────────────────────── Live grading (no replay) ───────────────────────────

    @Test
    void gradesLiveTraffic_whenStackBound() {
        when(agentRepository.findById(AGENT_ID)).thenReturn(Optional.of(agent("stack-1")));
        when(analyticsStore.isEnabled()).thenReturn(true);
        when(analyticsStore.findRecentSessions(any(), any(), any(), anyInt())).thenReturn(List.of(
                session("c1", "POSITIVE", "CAPTURED"),
                session("c2", "POSITIVE", "CAPTURED")));
        when(failureMiner.findFailingSessions(AGENT_ID)).thenReturn(List.of());
        lenient().when(citationDriftService.isEnabled()).thenReturn(false);
        when(runnerService.resolveJudgeModel(any())).thenReturn(null);
        when(chatMemoryService.listMessages(any(), anyInt())).thenAnswer(inv ->
                messages(inv.getArgument(0)));
        when(submissionRepository.findByConversationIdOrderByCompletedAtDesc(any()))
                .thenReturn(List.of());
        when(graderRegistry.resolveStack(any(), any()))
                .thenReturn(List.of(resolved(passingGrader(1.0, true))));
        when(snapshotRepository.findFirstByAgentIdAndBaselineTrueOrderByCreatedAtDesc(AGENT_ID))
                .thenReturn(Optional.empty());

        TurOnlineEvalSnapshotDto dto = service.sampleAndGrade(AGENT_ID);

        assertThat(dto.graderStackId()).isEqualTo("stack-1");
        assertThat(dto.gradedSessions()).isEqualTo(2);
        assertThat(dto.meanScore()).isEqualTo(1.0);
        assertThat(dto.passRate()).isEqualTo(1.0);
    }

    @Test
    void skipsSessionsWithNoAssistantReply_fromGradedDenominator() {
        when(agentRepository.findById(AGENT_ID)).thenReturn(Optional.of(agent("stack-1")));
        when(analyticsStore.isEnabled()).thenReturn(true);
        when(analyticsStore.findRecentSessions(any(), any(), any(), anyInt())).thenReturn(List.of(
                session("c1", "POSITIVE", "CAPTURED"),
                session("empty", "POSITIVE", "CAPTURED")));
        when(failureMiner.findFailingSessions(AGENT_ID)).thenReturn(List.of());
        lenient().when(runnerService.resolveJudgeModel(any())).thenReturn(null);
        when(chatMemoryService.listMessages(any(), anyInt())).thenAnswer(inv -> {
            String id = inv.getArgument(0);
            return "empty".equals(id)
                    ? new TurChatSessionMessagesDto(id, "NONE", false, List.of(), null)
                    : messages(id);
        });
        lenient().when(submissionRepository.findByConversationIdOrderByCompletedAtDesc(any()))
                .thenReturn(List.of());
        when(graderRegistry.resolveStack(any(), any()))
                .thenReturn(List.of(resolved(passingGrader(0.8, true))));
        when(snapshotRepository.findFirstByAgentIdAndBaselineTrueOrderByCreatedAtDesc(AGENT_ID))
                .thenReturn(Optional.empty());

        TurOnlineEvalSnapshotDto dto = service.sampleAndGrade(AGENT_ID);

        assertThat(dto.sampledSessions()).isEqualTo(2);
        assertThat(dto.gradedSessions()).isEqualTo(1); // "empty" had no assistant reply
        assertThat(dto.meanScore()).isEqualTo(0.8);
    }

    // ─────────────────────────── Drift detection ───────────────────────────

    @Test
    void flagsDrift_whenFailingRateExceedsThreshold_vsBaseline() {
        when(agentRepository.findById(AGENT_ID)).thenReturn(Optional.of(agent(null)));
        when(analyticsStore.isEnabled()).thenReturn(true);
        List<Map<String, Object>> sessions = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            sessions.add(session("c" + i, "NEGATIVE", "ABANDONED"));
        }
        when(analyticsStore.findRecentSessions(any(), any(), any(), anyInt())).thenReturn(sessions);
        // Every sampled session is failing → rate 1.0 ≥ 0.4.
        when(failureMiner.findFailingSessions(AGENT_ID)).thenReturn(sessions);
        lenient().when(citationDriftService.isEnabled()).thenReturn(false);

        TurOnlineEvalSnapshot baseline = new TurOnlineEvalSnapshot();
        baseline.setMeanScore(-1);
        baseline.setPassRate(-1);
        baseline.setFailingSessionRate(0.0);
        when(snapshotRepository.findFirstByAgentIdAndBaselineTrueOrderByCreatedAtDesc(AGENT_ID))
                .thenReturn(Optional.of(baseline));

        TurOnlineEvalSnapshotDto dto = service.sampleAndGrade(AGENT_ID);

        assertThat(dto.baseline()).isFalse();
        assertThat(dto.driftDetected()).isTrue();
        assertThat(dto.driftReason()).contains("failing sessions").contains("negative sentiment");
    }

    @Test
    void belowMinSamples_neverFlagsDrift() {
        when(agentRepository.findById(AGENT_ID)).thenReturn(Optional.of(agent(null)));
        when(analyticsStore.isEnabled()).thenReturn(true);
        // 2 sampled < minSamplesForDrift (default 5), both failing.
        List<Map<String, Object>> sessions = List.of(
                session("c0", "NEGATIVE", "ABANDONED"),
                session("c1", "NEGATIVE", "ABANDONED"));
        when(analyticsStore.findRecentSessions(any(), any(), any(), anyInt())).thenReturn(sessions);
        when(failureMiner.findFailingSessions(AGENT_ID)).thenReturn(sessions);
        lenient().when(citationDriftService.isEnabled()).thenReturn(false);

        TurOnlineEvalSnapshot baseline = new TurOnlineEvalSnapshot();
        baseline.setMeanScore(-1);
        baseline.setPassRate(-1);
        when(snapshotRepository.findFirstByAgentIdAndBaselineTrueOrderByCreatedAtDesc(AGENT_ID))
                .thenReturn(Optional.of(baseline));

        TurOnlineEvalSnapshotDto dto = service.sampleAndGrade(AGENT_ID);

        assertThat(dto.driftDetected()).isFalse();
    }

    // ─────────────────────────── Re-baseline ───────────────────────────

    @Test
    void promoteBaseline_demotesPrior_clearsDrift() {
        TurOnlineEvalSnapshot prior = new TurOnlineEvalSnapshot();
        prior.setId("old");
        prior.setAgentId(AGENT_ID);
        prior.setBaseline(true);
        TurOnlineEvalSnapshot target = new TurOnlineEvalSnapshot();
        target.setId("new");
        target.setAgentId(AGENT_ID);
        target.setDriftDetected(true);
        target.setDriftReason("failing sessions 1.00 ≥ 0.40");
        when(snapshotRepository.findById("new")).thenReturn(Optional.of(target));
        when(snapshotRepository.findByAgentIdAndBaselineTrue(AGENT_ID)).thenReturn(List.of(prior));

        Optional<TurOnlineEvalSnapshotDto> dto = service.promoteBaseline(AGENT_ID, "new");

        assertThat(dto).isPresent();
        assertThat(dto.get().baseline()).isTrue();
        assertThat(dto.get().driftDetected()).isFalse();
        assertThat(dto.get().driftReason()).isNull();
        assertThat(prior.isBaseline()).isFalse();
    }

    @Test
    void promoteBaseline_wrongAgent_returnsEmpty() {
        TurOnlineEvalSnapshot target = new TurOnlineEvalSnapshot();
        target.setId("x");
        target.setAgentId("other-agent");
        when(snapshotRepository.findById("x")).thenReturn(Optional.of(target));

        assertThat(service.promoteBaseline(AGENT_ID, "x")).isEmpty();
    }

    // ─────────────────────────── Fixtures ───────────────────────────

    private static TurAIAgent agent(String graderStackId) {
        TurAIAgent agent = new TurAIAgent();
        agent.setId(AGENT_ID);
        agent.setOnlineEvalGraderStackId(graderStackId);
        return agent;
    }

    private static Map<String, Object> session(String conversationId, String sentiment, String outcome) {
        return Map.of("conversationId", conversationId, "sentiment", sentiment, "outcome", outcome);
    }

    private static TurChatSessionMessagesDto messages(String conversationId) {
        return new TurChatSessionMessagesDto(conversationId, "MONGODB", true, List.of(
                new TurChatSessionMessageDto("user", "hello", "t1"),
                new TurChatSessionMessageDto("assistant", "hi there", "t2")), null);
    }

    private static TurEvalResolvedGrader resolved(TurEvalGrader grader) {
        return new TurEvalResolvedGrader(grader,
                TurEvalGraderConfigView.defaults(grader.graderId(), grader.kind()));
    }

    private static TurEvalGrader passingGrader(double score, boolean passed) {
        return new TurEvalGrader() {
            @Override
            public String graderId() {
                return "contains";
            }

            @Override
            public TurEvalGraderKind kind() {
                return TurEvalGraderKind.CODE;
            }

            @Override
            public boolean appliesTo(TurEvalGradingContext ctx, TurEvalGraderConfigView config) {
                return true;
            }

            @Override
            public TurEvalGraderResult grade(TurEvalGradingContext ctx, TurEvalGraderConfigView config) {
                return TurEvalGraderResult.scored(score, passed, "ok", "rationale");
            }
        };
    }
}
