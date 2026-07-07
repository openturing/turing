/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.eval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.viglet.turing.genai.selftuning.TurSelfTuningMiner;
import com.viglet.turing.persistence.dto.agent.TurChatSessionMessageDto;
import com.viglet.turing.persistence.dto.agent.TurChatSessionMessagesDto;
import com.viglet.turing.persistence.model.agent.TurAgentEvalExpectedOutcome;
import com.viglet.turing.persistence.model.agent.TurChatFlowSubmission;
import com.viglet.turing.persistence.model.agent.TurEvalDataset;
import com.viglet.turing.persistence.model.agent.TurEvalDatasetRow;
import com.viglet.turing.persistence.repository.agent.TurChatFlowSubmissionRepository;
import com.viglet.turing.persistence.repository.agent.TurEvalDatasetRepository;
import com.viglet.turing.persistence.repository.agent.TurEvalDatasetRowRepository;
import com.viglet.turing.service.chatanalytics.TurChatAnalyticsStore;
import com.viglet.turing.service.chatmemory.TurChatMemoryService;

import org.springframework.web.server.ResponseStatusException;

/**
 * T596 / §XXXIII.11 — sourcing an eval dataset from production transcripts (T61)
 * and mined failing conversations (T447), composing the shipped chat-memory /
 * analytics / submission subsystems through the file importer's persistence.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@ExtendWith(MockitoExtension.class)
class TurEvalDatasetSourceImportServiceTest {

    @Mock
    private TurChatMemoryService chatMemoryService;
    @Mock
    private TurChatAnalyticsStore analyticsStore;
    @Mock
    private TurSelfTuningMiner failureMiner;
    @Mock
    private TurChatFlowSubmissionRepository submissionRepository;
    @Mock
    private TurEvalDatasetRepository datasetRepository;
    @Mock
    private TurEvalDatasetRowRepository datasetRowRepository;

    /** Real import service so canonical-row mapping + persistence are exercised end-to-end. */
    private TurEvalDatasetSourceImportService service() {
        lenient().when(datasetRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        TurEvalDatasetImportService importService =
                new TurEvalDatasetImportService(datasetRepository, datasetRowRepository);
        return new TurEvalDatasetSourceImportService(importService, chatMemoryService,
                analyticsStore, failureMiner, submissionRepository);
    }

    private void stubTranscript(String conversationId, TurChatSessionMessageDto... messages) {
        lenient().when(chatMemoryService.listMessages(eq(conversationId), anyInt()))
                .thenReturn(new TurChatSessionMessagesDto(conversationId, "MONGODB", true,
                        List.of(messages), null));
    }

    private static TurChatSessionMessageDto user(String content) {
        return new TurChatSessionMessageDto("user", content, null);
    }

    private static TurChatSessionMessageDto assistant(String content) {
        return new TurChatSessionMessageDto("assistant", content, null);
    }

    private static List<TurEvalDatasetRow> rowsOf(TurEvalDataset d) {
        return List.copyOf(d.getRows());
    }

    @Test
    void transcriptBecomesGoldenRow() {
        stubTranscript("c1", user("what is the price?"), assistant("It is $10."));
        TurChatFlowSubmission submission = new TurChatFlowSubmission();
        submission.setConversationId("c1");
        submission.setEndNodeId("end-1");
        submission.setVariablesJson("{\"price\":\"10\"}");
        when(submissionRepository.findByConversationIdOrderByCompletedAtDesc("c1"))
                .thenReturn(List.of(submission));
        when(analyticsStore.findRecentSessions(any(), any(), any(), anyInt()))
                .thenReturn(List.of(Map.of("conversationId", "c1", "outcome", "COMPLETED")));

        TurEvalDataset dataset = service().importFromTranscripts("a1", null, 100);

        List<TurEvalDatasetRow> rows = rowsOf(dataset);
        assertThat(rows).hasSize(1);
        TurEvalDatasetRow row = rows.get(0);
        assertThat(row.getSeedTurnsJson()).contains("what is the price?");
        assertThat(row.getReferenceAnswer()).isEqualTo("It is $10.");
        assertThat(row.getExpectedOutcome()).isEqualTo(TurAgentEvalExpectedOutcome.CAPTURED);
        assertThat(row.getExpectedNodeId()).isEqualTo("end-1");
        assertThat(row.getExpectedSlotsJson()).contains("price");
        assertThat(row.getTags()).contains("transcript");
    }

    @Test
    void abandonedSubmissionMapsToAbandonedOutcomeWithoutNode() {
        stubTranscript("c2", user("hi"), assistant("hello"));
        TurChatFlowSubmission submission = new TurChatFlowSubmission();
        submission.setConversationId("c2");
        submission.setEndNodeId("__abandoned__");
        when(submissionRepository.findByConversationIdOrderByCompletedAtDesc("c2"))
                .thenReturn(List.of(submission));
        when(analyticsStore.findRecentSessions(any(), any(), any(), anyInt()))
                .thenReturn(List.of(Map.of("conversationId", "c2")));

        TurEvalDatasetRow row = rowsOf(service().importFromTranscripts("a1", "ds", 100)).get(0);

        assertThat(row.getExpectedOutcome()).isEqualTo(TurAgentEvalExpectedOutcome.ABANDONED);
        assertThat(row.getExpectedNodeId()).isNull();
    }

    @Test
    void failureRowHasNoGoldenAnswerAndStaysAny() {
        stubTranscript("f1", user("this is broken"), assistant("sorry, I cannot help"));
        when(submissionRepository.findByConversationIdOrderByCompletedAtDesc("f1"))
                .thenReturn(List.of());
        when(failureMiner.findFailingSessions("a1"))
                .thenReturn(List.of(Map.of("conversationId", "f1",
                        "outcome", "ABANDONED", "sentiment", "FRUSTRATED")));

        TurEvalDatasetRow row = rowsOf(service().importFromFailures("a1", null, 100)).get(0);

        assertThat(row.getSeedTurnsJson()).contains("this is broken");
        assertThat(row.getReferenceAnswer()).isNull();
        assertThat(row.getExpectedOutcome()).isEqualTo(TurAgentEvalExpectedOutcome.ANY);
        assertThat(row.getTags()).contains("mined-failure");
        assertThat(row.getMetadataJson()).contains("FRUSTRATED").contains("ABANDONED");
    }

    @Test
    void conversationWithNoUserTurnIsSkipped() {
        stubTranscript("c3", assistant("greeting only"));
        stubTranscript("c4", user("real question"), assistant("answer"));
        lenient().when(submissionRepository.findByConversationIdOrderByCompletedAtDesc(any()))
                .thenReturn(List.of());
        when(analyticsStore.findRecentSessions(any(), any(), any(), anyInt()))
                .thenReturn(List.of(Map.of("conversationId", "c3"), Map.of("conversationId", "c4")));

        List<TurEvalDatasetRow> rows = rowsOf(service().importFromTranscripts("a1", "ds", 100));

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).getName()).isEqualTo("c4");
    }

    @Test
    void emptySourceThrowsBadRequest() {
        when(failureMiner.findFailingSessions("a1")).thenReturn(List.of());
        assertThatThrownBy(() -> service().importFromFailures("a1", null, 100))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("No rows");
    }

    @Test
    void blankAgentIdThrowsBadRequest() {
        assertThatThrownBy(() -> service().importFromTranscripts("  ", null, 100))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("agentId");
    }
}
