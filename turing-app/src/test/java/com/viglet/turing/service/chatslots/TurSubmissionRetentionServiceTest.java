/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.service.chatslots;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.viglet.turing.persistence.model.agent.TurAIAgent;
import com.viglet.turing.persistence.model.agent.TurChatFlow;
import com.viglet.turing.persistence.model.agent.TurChatFlowSubmission;
import com.viglet.turing.persistence.model.agent.TurSubmissionRetention;
import com.viglet.turing.persistence.repository.agent.TurAIAgentRepository;
import com.viglet.turing.persistence.repository.agent.TurChatFlowSubmissionRepository;

/**
 * Unit coverage for the T66 submission-retention enforcement.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
@ExtendWith(MockitoExtension.class)
class TurSubmissionRetentionServiceTest {

    @Mock
    private TurAIAgentRepository agentRepository;
    @Mock
    private TurChatFlowSubmissionRepository submissionRepository;
    @InjectMocks
    private TurSubmissionRetentionService service;

    private static TurAIAgent agent(String id, TurSubmissionRetention mode, Integer days) {
        TurAIAgent a = new TurAIAgent();
        a.setId(id);
        a.setTitle("agent-" + id);
        a.setSubmissionRetention(mode);
        a.setSubmissionRetentionDays(days);
        return a;
    }

    private static TurChatFlowSubmission submissionFor(TurAIAgent owner) {
        TurChatFlow flow = new TurChatFlow();
        flow.setTurAIAgent(owner);
        TurChatFlowSubmission s = new TurChatFlowSubmission();
        s.setFlow(flow);
        return s;
    }

    // ─── purgeExpiredSubmissions (RETAIN_DAYS, time-driven) ───────────

    @Test
    void purge_retainDays_deletesExpiredForThatAgent() {
        TurAIAgent a = agent("a1", TurSubmissionRetention.RETAIN_DAYS, 30);
        when(agentRepository.findAll()).thenReturn(List.of(a));
        List<TurChatFlowSubmission> expired = List.of(new TurChatFlowSubmission(), new TurChatFlowSubmission());
        when(submissionRepository.findByFlow_TurAIAgent_IdAndCompletedAtBefore(eq("a1"), any()))
                .thenReturn(expired);

        int deleted = service.purgeExpiredSubmissions();

        assertThat(deleted).isEqualTo(2);
        verify(submissionRepository).deleteAll(expired);
    }

    @Test
    void purge_retainDays_cutoffIsNowMinusDays() {
        TurAIAgent a = agent("a1", TurSubmissionRetention.RETAIN_DAYS, 7);
        when(agentRepository.findAll()).thenReturn(List.of(a));
        when(submissionRepository.findByFlow_TurAIAgent_IdAndCompletedAtBefore(eq("a1"), any()))
                .thenReturn(List.of());

        LocalDateTime before = LocalDateTime.now().minusDays(7).minusMinutes(1);
        service.purgeExpiredSubmissions();
        LocalDateTime after = LocalDateTime.now().minusDays(7).plusMinutes(1);

        ArgumentCaptor<LocalDateTime> cutoff = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(submissionRepository).findByFlow_TurAIAgent_IdAndCompletedAtBefore(eq("a1"), cutoff.capture());
        assertThat(cutoff.getValue()).isBetween(before, after);
    }

    @Test
    void purge_retainForever_isNoOp() {
        TurAIAgent a = agent("a1", TurSubmissionRetention.RETAIN_FOREVER, 30);
        when(agentRepository.findAll()).thenReturn(List.of(a));

        int deleted = service.purgeExpiredSubmissions();

        assertThat(deleted).isZero();
        verify(submissionRepository, never()).findByFlow_TurAIAgent_IdAndCompletedAtBefore(any(), any());
        verify(submissionRepository, never()).deleteAll(any());
    }

    @Test
    void purge_deleteAfterExport_isNotTimeDriven() {
        TurAIAgent a = agent("a1", TurSubmissionRetention.DELETE_AFTER_EXPORT, 5);
        when(agentRepository.findAll()).thenReturn(List.of(a));

        int deleted = service.purgeExpiredSubmissions();

        assertThat(deleted).isZero();
        verify(submissionRepository, never()).findByFlow_TurAIAgent_IdAndCompletedAtBefore(any(), any());
    }

    @Test
    void purge_retainDays_nullOrNonPositiveDays_skipped() {
        TurAIAgent nullDays = agent("a1", TurSubmissionRetention.RETAIN_DAYS, null);
        TurAIAgent zeroDays = agent("a2", TurSubmissionRetention.RETAIN_DAYS, 0);
        TurAIAgent negDays = agent("a3", TurSubmissionRetention.RETAIN_DAYS, -10);
        when(agentRepository.findAll()).thenReturn(List.of(nullDays, zeroDays, negDays));

        int deleted = service.purgeExpiredSubmissions();

        assertThat(deleted).isZero();
        verify(submissionRepository, never()).findByFlow_TurAIAgent_IdAndCompletedAtBefore(any(), any());
    }

    @Test
    void purge_emptyExpiredList_doesNotCallDelete() {
        TurAIAgent a = agent("a1", TurSubmissionRetention.RETAIN_DAYS, 30);
        when(agentRepository.findAll()).thenReturn(List.of(a));
        when(submissionRepository.findByFlow_TurAIAgent_IdAndCompletedAtBefore(eq("a1"), any()))
                .thenReturn(List.of());

        int deleted = service.purgeExpiredSubmissions();

        assertThat(deleted).isZero();
        verify(submissionRepository, never()).deleteAll(any());
    }

    @Test
    void purge_mixedAgents_onlyRetainDaysWithThresholdSwept() {
        TurAIAgent keep = agent("forever", TurSubmissionRetention.RETAIN_FOREVER, null);
        TurAIAgent sweep = agent("sweep", TurSubmissionRetention.RETAIN_DAYS, 14);
        when(agentRepository.findAll()).thenReturn(List.of(keep, sweep));
        when(submissionRepository.findByFlow_TurAIAgent_IdAndCompletedAtBefore(eq("sweep"), any()))
                .thenReturn(List.of(new TurChatFlowSubmission()));

        int deleted = service.purgeExpiredSubmissions();

        assertThat(deleted).isEqualTo(1);
        verify(submissionRepository, never()).findByFlow_TurAIAgent_IdAndCompletedAtBefore(eq("forever"), any());
    }

    // ─── applyPostExportRetention (DELETE_AFTER_EXPORT, event-driven) ──

    @Test
    void postExport_deletesSubmissionsOfDeleteAfterExportAgents() {
        TurAIAgent dae = agent("dae", TurSubmissionRetention.DELETE_AFTER_EXPORT, null);
        TurChatFlowSubmission s1 = submissionFor(dae);
        TurChatFlowSubmission s2 = submissionFor(dae);
        when(submissionRepository.findByConversationIdOrderByCompletedAtDesc("conv"))
                .thenReturn(List.of(s1, s2));

        int deleted = service.applyPostExportRetention("conv");

        assertThat(deleted).isEqualTo(2);
        verify(submissionRepository).deleteAll(List.of(s1, s2));
    }

    @Test
    void postExport_keepsSubmissionsOfOtherModes() {
        TurAIAgent forever = agent("forever", TurSubmissionRetention.RETAIN_FOREVER, null);
        TurAIAgent days = agent("days", TurSubmissionRetention.RETAIN_DAYS, 30);
        when(submissionRepository.findByConversationIdOrderByCompletedAtDesc("conv"))
                .thenReturn(List.of(submissionFor(forever), submissionFor(days)));

        int deleted = service.applyPostExportRetention("conv");

        assertThat(deleted).isZero();
        verify(submissionRepository, never()).deleteAll(any());
    }

    @Test
    void postExport_mixedConversation_onlyDeleteAfterExportRowsPurged() {
        TurAIAgent dae = agent("dae", TurSubmissionRetention.DELETE_AFTER_EXPORT, null);
        TurAIAgent keep = agent("keep", TurSubmissionRetention.RETAIN_FOREVER, null);
        TurChatFlowSubmission daeSub = submissionFor(dae);
        TurChatFlowSubmission keepSub = submissionFor(keep);
        when(submissionRepository.findByConversationIdOrderByCompletedAtDesc("conv"))
                .thenReturn(List.of(daeSub, keepSub));

        int deleted = service.applyPostExportRetention("conv");

        assertThat(deleted).isEqualTo(1);
        verify(submissionRepository).deleteAll(List.of(daeSub));
    }

    @Test
    void postExport_blankConversation_isNoOp() {
        assertThat(service.applyPostExportRetention(null)).isZero();
        assertThat(service.applyPostExportRetention("  ")).isZero();
        verify(submissionRepository, never()).findByConversationIdOrderByCompletedAtDesc(any());
    }

    @Test
    void postExport_nullFlowOrAgent_skippedSafely() {
        TurChatFlowSubmission orphanFlow = new TurChatFlowSubmission(); // null flow
        TurChatFlow flowNoAgent = new TurChatFlow();
        TurChatFlowSubmission orphanAgent = new TurChatFlowSubmission();
        orphanAgent.setFlow(flowNoAgent); // flow with null agent
        when(submissionRepository.findByConversationIdOrderByCompletedAtDesc("conv"))
                .thenReturn(List.of(orphanFlow, orphanAgent));

        int deleted = service.applyPostExportRetention("conv");

        assertThat(deleted).isZero();
        verify(submissionRepository, never()).deleteAll(any());
    }

    @Test
    void postExport_persistenceFailureSwallowed() {
        TurAIAgent dae = agent("dae", TurSubmissionRetention.DELETE_AFTER_EXPORT, null);
        when(submissionRepository.findByConversationIdOrderByCompletedAtDesc("conv"))
                .thenReturn(List.of(submissionFor(dae)));
        lenient().doThrow(new RuntimeException("db down")).when(submissionRepository).deleteAll(any());

        int deleted = service.applyPostExportRetention("conv");

        assertThat(deleted).isZero(); // swallowed, never propagates to break the export download
    }
}
