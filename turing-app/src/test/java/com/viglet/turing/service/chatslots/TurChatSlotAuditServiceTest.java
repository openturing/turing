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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.viglet.turing.persistence.model.agent.TurChatSlotAuditEntry;
import com.viglet.turing.persistence.model.agent.TurChatSlotAuditSource;
import com.viglet.turing.persistence.repository.agent.TurChatSlotAuditRepository;

/**
 * Pins the T60 audit-service contract: every {@code record(...)} call lands
 * in exactly one persisted row with the source tag intact, defensive guards
 * skip writes for blank conversation ids / slot names / null source, and
 * {@link TurChatSlotAuditService#diff(Map, Map)} reports adds, updates, and
 * removes as separate diff entries.
 */
@ExtendWith(MockitoExtension.class)
class TurChatSlotAuditServiceTest {

    @Mock
    private TurChatSlotAuditRepository repository;

    @InjectMocks
    private TurChatSlotAuditService service;

    @Test
    void recordPersistsOneRowPerCall() {
        service.record("conv-1", "name", null, "Alex",
                TurChatSlotAuditSource.NODE, "node=ai-name");

        ArgumentCaptor<TurChatSlotAuditEntry> captor =
                ArgumentCaptor.forClass(TurChatSlotAuditEntry.class);
        verify(repository, times(1)).save(captor.capture());
        TurChatSlotAuditEntry entry = captor.getValue();
        assertThat(entry.getConversationId()).isEqualTo("conv-1");
        assertThat(entry.getSlotName()).isEqualTo("name");
        assertThat(entry.getOldValue()).isNull();
        assertThat(entry.getNewValue()).isEqualTo("Alex");
        assertThat(entry.getSource()).isEqualTo(TurChatSlotAuditSource.NODE);
        assertThat(entry.getOriginDetail()).isEqualTo("node=ai-name");
        assertThat(entry.getTs()).isNotNull();
    }

    @Test
    void recordSkipsBlankConversationId() {
        service.record("", "name", null, "Alex", TurChatSlotAuditSource.NODE, null);
        service.record("  ", "name", null, "Alex", TurChatSlotAuditSource.NODE, null);
        service.record(null, "name", null, "Alex", TurChatSlotAuditSource.NODE, null);
        verify(repository, never()).save(any());
    }

    @Test
    void recordSkipsBlankSlotName() {
        service.record("conv-1", "", null, "Alex", TurChatSlotAuditSource.NODE, null);
        service.record("conv-1", "   ", null, "Alex", TurChatSlotAuditSource.NODE, null);
        service.record("conv-1", null, null, "Alex", TurChatSlotAuditSource.NODE, null);
        verify(repository, never()).save(any());
    }

    @Test
    void recordSkipsNullSource() {
        service.record("conv-1", "name", null, "Alex", null, null);
        verify(repository, never()).save(any());
    }

    @Test
    void recordSwallowsPersistenceFailure() {
        when(repository.save(any())).thenThrow(new RuntimeException("DB down"));
        // Must not throw — audit is best-effort. Reaching this verify proves the
        // save was attempted and the persistence exception was swallowed.
        service.record("conv-1", "name", null, "Alex", TurChatSlotAuditSource.NODE, null);
        verify(repository).save(any());
    }

    @Test
    void recordTruncatesLongOriginDetail() {
        String long256 = "a".repeat(400);
        service.record("conv-1", "name", null, "Alex",
                TurChatSlotAuditSource.NODE, long256);
        ArgumentCaptor<TurChatSlotAuditEntry> captor =
                ArgumentCaptor.forClass(TurChatSlotAuditEntry.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getOriginDetail()).hasSize(256);
        assertThat(captor.getValue().getOriginDetail()).endsWith("...");
    }

    @Test
    void listReturnsRepositoryRows() {
        TurChatSlotAuditEntry row = new TurChatSlotAuditEntry();
        when(repository.findByConversationIdOrderByTsAsc("conv-1")).thenReturn(List.of(row));
        assertThat(service.list("conv-1")).containsExactly(row);
    }

    @Test
    void listReturnsEmptyForBlankConversation() {
        assertThat(service.list(null)).isEmpty();
        assertThat(service.list("")).isEmpty();
        assertThat(service.list("  ")).isEmpty();
        verify(repository, never()).findByConversationIdOrderByTsAsc(any());
    }

    @Test
    void diffReportsAddedSlots() {
        Map<String, String> before = Map.of();
        Map<String, String> after = Map.of("name", "Alex");
        Map<String, TurChatSlotAuditService.DiffEntry> diff = TurChatSlotAuditService.diff(before, after);
        assertThat(diff).hasSize(1);
        assertThat(diff.get("name").oldValue()).isNull();
        assertThat(diff.get("name").newValue()).isEqualTo("Alex");
    }

    @Test
    void diffReportsUpdatedSlots() {
        Map<String, String> before = Map.of("name", "Alex");
        Map<String, String> after = Map.of("name", "Alexandre");
        Map<String, TurChatSlotAuditService.DiffEntry> diff = TurChatSlotAuditService.diff(before, after);
        assertThat(diff).hasSize(1);
        assertThat(diff.get("name").oldValue()).isEqualTo("Alex");
        assertThat(diff.get("name").newValue()).isEqualTo("Alexandre");
    }

    @Test
    void diffReportsRemovedSlots() {
        Map<String, String> before = Map.of("name", "Alex");
        Map<String, String> after = Map.of();
        Map<String, TurChatSlotAuditService.DiffEntry> diff = TurChatSlotAuditService.diff(before, after);
        assertThat(diff).hasSize(1);
        assertThat(diff.get("name").oldValue()).isEqualTo("Alex");
        assertThat(diff.get("name").newValue()).isNull();
    }

    @Test
    void diffSkipsUnchangedSlots() {
        Map<String, String> before = Map.of("name", "Alex");
        Map<String, String> after = Map.of("name", "Alex");
        Map<String, TurChatSlotAuditService.DiffEntry> diff = TurChatSlotAuditService.diff(before, after);
        assertThat(diff).isEmpty();
    }

    @Test
    void diffHandlesNullInputs() {
        Map<String, String> after = Map.of("name", "Alex");
        assertThat(TurChatSlotAuditService.diff(null, after))
                .hasSize(1)
                .containsKey("name");
        assertThat(TurChatSlotAuditService.diff(after, null))
                .hasSize(1)
                .extractingByKey("name")
                .extracting(TurChatSlotAuditService.DiffEntry::newValue)
                .isNull();
        assertThat(TurChatSlotAuditService.diff(null, null)).isEmpty();
    }

    @Test
    void recordDiffPersistsOneRowPerChangedSlot() {
        Map<String, String> before = new LinkedHashMap<>();
        before.put("name", "Alex");
        before.put("cargo", "Engenheiro");
        Map<String, String> after = new LinkedHashMap<>();
        after.put("name", "Alexandre"); // updated
        after.put("cargo", "Engenheiro"); // unchanged
        after.put("objetivo", "Aprender"); // added

        service.recordDiff("conv-1", before, after,
                TurChatSlotAuditSource.NODE, "node=ai-name->ai-cargo");

        verify(repository, times(2)).save(any());
    }
}
