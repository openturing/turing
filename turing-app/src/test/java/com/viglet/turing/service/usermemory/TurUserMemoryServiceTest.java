/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.service.usermemory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.viglet.turing.persistence.model.agent.TurUserMemory;
import com.viglet.turing.persistence.repository.agent.TurUserMemoryRepository;

@ExtendWith(MockitoExtension.class)
class TurUserMemoryServiceTest {

    @Mock
    private TurUserMemoryRepository repository;

    @InjectMocks
    private TurUserMemoryService service;

    private static TurUserMemory mem(String key, String content) {
        TurUserMemory m = new TurUserMemory();
        m.setId("m-" + key);
        m.setUserId("u1");
        m.setAgentId("a1");
        m.setMemoryKey(key);
        m.setContent(content);
        return m;
    }

    @Test
    void rememberInsertsWhenKeyAbsent() {
        when(repository.findByUserIdAndAgentIdAndMemoryKey("u1", "a1", "role"))
                .thenReturn(Optional.empty());
        when(repository.save(any(TurUserMemory.class))).thenAnswer(inv -> inv.getArgument(0));

        TurUserMemory saved = service.remember("u1", "a1", "role", "Solutions Engineer");
        assertThat(saved.getMemoryKey()).isEqualTo("role");
        assertThat(saved.getContent()).isEqualTo("Solutions Engineer");
        assertThat(saved.getCreatedAt()).isPositive();
        assertThat(saved.getUpdatedAt()).isPositive();
    }

    @Test
    void rememberUpdatesExistingKeyInPlace() {
        TurUserMemory existing = mem("role", "old");
        existing.setCreatedAt(1L);
        when(repository.findByUserIdAndAgentIdAndMemoryKey("u1", "a1", "role"))
                .thenReturn(Optional.of(existing));
        when(repository.save(any(TurUserMemory.class))).thenAnswer(inv -> inv.getArgument(0));

        TurUserMemory saved = service.remember("u1", "a1", "role", "new");
        assertThat(saved.getId()).isEqualTo("m-role"); // same row
        assertThat(saved.getContent()).isEqualTo("new");
        assertThat(saved.getCreatedAt()).isEqualTo(1L); // createdAt preserved
    }

    @Test
    void recallRendersFactsOrSentinel() {
        when(repository.findByUserIdAndAgentIdOrderByUpdatedAtDesc("u1", "a1"))
                .thenReturn(List.of(mem("role", "SE"), mem("language", "pt")));
        String out = service.recall("u1", "a1");
        assertThat(out).contains("role: SE").contains("language: pt");
    }

    @Test
    void recallSentinelWhenEmpty() {
        when(repository.findByUserIdAndAgentIdOrderByUpdatedAtDesc("u1", "a1"))
                .thenReturn(List.of());
        assertThat(service.recall("u1", "a1")).contains("Nothing remembered");
    }

    // ---- T651 / §XXXVII.13 — scoped delete (IDOR fix) ----------------------

    @Test
    void deleteRemovesRowWhenOwnedByCaller() {
        TurUserMemory m = mem("role", "dev");
        when(repository.findById("m-role")).thenReturn(Optional.of(m));

        assertThat(service.delete("m-role", "u1", "a1")).isTrue();
        org.mockito.Mockito.verify(repository).delete(m);
    }

    @Test
    void deleteRefusesWhenUserIdDoesNotMatch() {
        TurUserMemory m = mem("role", "dev"); // userId=u1
        when(repository.findById("m-role")).thenReturn(Optional.of(m));

        assertThat(service.delete("m-role", "attacker", "a1")).isFalse();
        org.mockito.Mockito.verify(repository, org.mockito.Mockito.never()).delete(any());
    }

    @Test
    void deleteRefusesWhenAgentDoesNotMatch() {
        TurUserMemory m = mem("role", "dev"); // agentId=a1
        when(repository.findById("m-role")).thenReturn(Optional.of(m));

        assertThat(service.delete("m-role", "u1", "other-agent")).isFalse();
        org.mockito.Mockito.verify(repository, org.mockito.Mockito.never()).delete(any());
    }

    @Test
    void deleteReturnsFalseWhenRowAbsent() {
        when(repository.findById("missing")).thenReturn(Optional.empty());
        assertThat(service.delete("missing", "u1", "a1")).isFalse();
    }
}
