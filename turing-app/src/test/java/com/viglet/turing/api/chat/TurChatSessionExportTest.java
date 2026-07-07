/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.api.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import com.viglet.turing.genai.flow.TurChatFlowEngineService;
import com.viglet.turing.persistence.dto.agent.TurChatFlowSubmissionDto;
import com.viglet.turing.persistence.dto.agent.TurChatSessionExportDto;
import com.viglet.turing.persistence.dto.agent.TurChatSessionMessageDto;
import com.viglet.turing.persistence.dto.agent.TurChatSessionMessagesDto;
import com.viglet.turing.persistence.dto.agent.TurChatSessionSlotsDto;
import com.viglet.turing.persistence.model.agent.TurChatSlotAuditEntry;
import com.viglet.turing.persistence.model.agent.TurChatSlotAuditSource;
import com.viglet.turing.service.chatmemory.TurChatMemoryService;
import com.viglet.turing.service.chatslots.TurChatSlotAuditService;
import com.viglet.turing.service.chatslots.TurSubmissionRetentionService;
import com.viglet.turing.system.TurLlmSummaryService;

/**
 * Unit tests for the T65 conversation-export endpoint on
 * {@link TurChatSessionAPI}. The controller is lean — it bundles four service
 * reads into one DTO and serves it as a download — so a plain Mockito unit
 * test pins the assembly + the attachment headers without booting Spring.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
class TurChatSessionExportTest {

    // Fixed timestamp — the export tests only need a value, not the wall clock.
    private static final LocalDateTime FIXED_NOW = LocalDateTime.parse("2026-06-15T12:00:00");

    private TurChatFlowEngineService engine;
    private TurChatMemoryService memory;
    private TurChatSlotAuditService audit;
    private TurSubmissionRetentionService retention;
    private TurChatSessionAPI api;

    @BeforeEach
    void setUp() {
        engine = mock(TurChatFlowEngineService.class);
        memory = mock(TurChatMemoryService.class);
        TurLlmSummaryService summary = mock(TurLlmSummaryService.class);
        audit = mock(TurChatSlotAuditService.class);
        retention = mock(TurSubmissionRetentionService.class);
        api = new TurChatSessionAPI(engine, memory, summary, audit, retention,
                mock(com.viglet.turing.genai.spectator.TurChatMessageEventBus.class),
                mock(com.viglet.turing.service.chatslots.TurChatSlotEventBus.class),
                mock(com.viglet.turing.genai.workspace.TurWorkspaceEventBus.class),
                mock(com.viglet.turing.service.chatslots.TurChatSlotSseRegistry.class),
                mock(com.viglet.turing.genai.spectator.TurCopilotService.class),
                mock(com.viglet.turing.genai.citation.TurCitationDriftService.class));
    }

    private TurChatSlotAuditEntry auditEntry(String slot, String oldV, String newV,
            TurChatSlotAuditSource source, String origin) {
        TurChatSlotAuditEntry e = new TurChatSlotAuditEntry();
        e.setId("a-" + slot);
        e.setSlotName(slot);
        e.setOldValue(oldV);
        e.setNewValue(newV);
        e.setSource(source);
        e.setOriginDetail(origin);
        e.setTs(FIXED_NOW);
        return e;
    }

    @Test
    void exportBundlesAllFourSources() {
        String conv = "conv-123";
        var flowState = new TurChatSessionExportDto.FlowState(
                "state-1", "flow-1", "Lead Capture", "node-7", null,
                Map.of("name", "Ada"), FIXED_NOW);
        var submission = new TurChatFlowSubmissionDto(
                conv, "flow-1", FIXED_NOW, Map.of("email", "a@b.com"), "u1", "end");
        var transcript = new TurChatSessionMessagesDto(conv, "MONGODB", true,
                List.of(new TurChatSessionMessageDto("user", "hi", "2026-06-02T10:00:00")), null);

        when(engine.exportFlowStates(conv)).thenReturn(List.of(flowState));
        when(engine.listSubmissionsForConversation(conv)).thenReturn(List.of(submission));
        when(engine.listSlotsForConversation(conv))
                .thenReturn(new TurChatSessionSlotsDto(conv, Map.of("name", "Ada", "email", "a@b.com")));
        when(memory.listMessages(eq(conv), anyInt())).thenReturn(transcript);
        when(audit.list(conv)).thenReturn(List.of(
                auditEntry("name", null, "Ada", TurChatSlotAuditSource.NODE, "node=q1")));

        ResponseEntity<TurChatSessionExportDto> response = api.exportConversation(conv, 1000);
        TurChatSessionExportDto body = response.getBody();

        assertThat(body).isNotNull();
        assertThat(body.conversationId()).isEqualTo(conv);
        assertThat(body.exportedAt()).isNotNull();
        assertThat(body.flowStates()).containsExactly(flowState);
        assertThat(body.submissions()).containsExactly(submission);
        assertThat(body.slots()).containsEntry("name", "Ada").containsEntry("email", "a@b.com");
        assertThat(body.transcriptEngine()).isEqualTo("MONGODB");
        assertThat(body.transcriptEnabled()).isTrue();
        assertThat(body.transcript()).hasSize(1);
        assertThat(body.slotAudit()).hasSize(1);
        assertThat(body.slotAudit().getFirst().source()).isEqualTo("NODE");
        assertThat(body.slotAudit().getFirst().newValue()).isEqualTo("Ada");
    }

    @Test
    void exportServesAsJsonAttachmentWithSanitizedFilename() {
        String conv = "../../etc/passwd weird:id";
        when(engine.exportFlowStates(conv)).thenReturn(List.of());
        when(engine.listSubmissionsForConversation(conv)).thenReturn(List.of());
        when(engine.listSlotsForConversation(conv)).thenReturn(new TurChatSessionSlotsDto(conv, Map.of()));
        when(memory.listMessages(eq(conv), anyInt()))
                .thenReturn(new TurChatSessionMessagesDto(conv, "NONE", false, List.of(), null));
        when(audit.list(conv)).thenReturn(List.of());

        ResponseEntity<TurChatSessionExportDto> response = api.exportConversation(conv, 1000);

        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_JSON);
        String disposition = response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION);
        assertThat(disposition)
                .isNotNull()
                .startsWith("attachment")
                .contains(".json")
                // The real injection vectors — path separators and spaces — are stripped
                // (dots are kept so the ".json" extension and dotted ids stay intact).
                .doesNotContain("/")
                .doesNotContain("\\")
                .doesNotContain("passwd weird");
    }

    @Test
    void exportPropagatesTranscriptLimitToMemoryService() {
        String conv = "conv-9";
        when(engine.exportFlowStates(conv)).thenReturn(List.of());
        when(engine.listSubmissionsForConversation(conv)).thenReturn(List.of());
        when(engine.listSlotsForConversation(conv)).thenReturn(new TurChatSessionSlotsDto(conv, Map.of()));
        when(memory.listMessages(conv, 25))
                .thenReturn(new TurChatSessionMessagesDto(conv, "REDIS", true, List.of(), null));
        when(audit.list(conv)).thenReturn(List.of());

        api.exportConversation(conv, 25);

        org.mockito.Mockito.verify(memory).listMessages(conv, 25);
    }

    @Test
    void exportTriggersPostExportRetentionHook() {
        String conv = "conv-retention";
        when(engine.exportFlowStates(conv)).thenReturn(List.of());
        when(engine.listSubmissionsForConversation(conv)).thenReturn(List.of());
        when(engine.listSlotsForConversation(conv)).thenReturn(new TurChatSessionSlotsDto(conv, Map.of()));
        when(memory.listMessages(eq(conv), anyInt()))
                .thenReturn(new TurChatSessionMessagesDto(conv, "NONE", false, List.of(), null));
        when(audit.list(conv)).thenReturn(List.of());

        api.exportConversation(conv, 1000);

        // T66 — DELETE_AFTER_EXPORT enforcement runs after the bundle is built.
        org.mockito.Mockito.verify(retention).applyPostExportRetention(conv);
    }
}
