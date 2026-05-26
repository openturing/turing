/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.api.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import com.viglet.turing.persistence.dto.agent.TurRoutineDto;
import com.viglet.turing.persistence.model.agent.TurRoutine;
import com.viglet.turing.persistence.model.agent.TurRoutineKind;
import com.viglet.turing.persistence.repository.agent.TurRoutineRepository;

/**
 * Unit tests for the T48 routine CRUD endpoint. Exercises validation,
 * conflict, and not-found branches without booting Spring — the API is
 * a thin layer over the repository so plain Mockito is enough.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
class TurRoutineAPITest {

    @Test
    @DisplayName("list: sorts entries alphabetically (case-insensitive)")
    void list_sortsByNameCaseInsensitive() {
        TurRoutineRepository repo = mock(TurRoutineRepository.class);
        when(repo.findAll()).thenReturn(List.of(
                routine("r2", "zebra"),
                routine("r1", "alpha"),
                routine("r3", "Bravo")));
        TurRoutineAPI api = new TurRoutineAPI(repo);

        List<TurRoutineDto> result = api.list();

        assertThat(result).extracting(TurRoutineDto::name)
                .containsExactly("alpha", "Bravo", "zebra");
    }

    @Test
    @DisplayName("get: returns DTO when found")
    void get_happyPath() {
        TurRoutineRepository repo = mock(TurRoutineRepository.class);
        when(repo.findById("r1")).thenReturn(Optional.of(routine("r1", "alpha")));
        TurRoutineAPI api = new TurRoutineAPI(repo);

        TurRoutineDto dto = api.get("r1");

        assertThat(dto.id()).isEqualTo("r1");
        assertThat(dto.name()).isEqualTo("alpha");
    }

    @Test
    @DisplayName("get: 404 when missing")
    void get_notFound() {
        TurRoutineRepository repo = mock(TurRoutineRepository.class);
        when(repo.findById("ghost")).thenReturn(Optional.empty());
        TurRoutineAPI api = new TurRoutineAPI(repo);

        assertThatThrownBy(() -> api.get("ghost"))
                .isInstanceOf(ResponseStatusException.class)
                .hasFieldOrPropertyWithValue("statusCode", HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("create: persists with cleared id and defaults")
    void create_happyPath() {
        TurRoutineRepository repo = mock(TurRoutineRepository.class);
        when(repo.findByName("hello")).thenReturn(Optional.empty());
        when(repo.save(any(TurRoutine.class))).thenAnswer(inv -> {
            TurRoutine r = inv.getArgument(0);
            r.setId("generated-uuid");
            return r;
        });
        TurRoutineAPI api = new TurRoutineAPI(repo);

        TurRoutineDto dto = api.create(new TurRoutineDto(
                "client-sent-id-ignored", "hello", "desc",
                TurRoutineKind.NATIVE, "say_hi", null, 30_000, true));

        assertThat(dto.id()).isEqualTo("generated-uuid");
        assertThat(dto.name()).isEqualTo("hello");
        verify(repo).save(any(TurRoutine.class));
    }

    @Test
    @DisplayName("create: rejects duplicate name with 409")
    void create_duplicateName_isConflict() {
        TurRoutineRepository repo = mock(TurRoutineRepository.class);
        when(repo.findByName("hello")).thenReturn(Optional.of(routine("r1", "hello")));
        TurRoutineAPI api = new TurRoutineAPI(repo);

        assertThatThrownBy(() -> api.create(new TurRoutineDto(
                null, "hello", null, TurRoutineKind.NATIVE, "x", null, 1000, true)))
                .isInstanceOf(ResponseStatusException.class)
                .hasFieldOrPropertyWithValue("statusCode", HttpStatus.CONFLICT);
        verify(repo, never()).save(any(TurRoutine.class));
    }

    @Test
    @DisplayName("create: blank/invalid name is 400")
    void create_invalidName_isBadRequest() {
        TurRoutineRepository repo = mock(TurRoutineRepository.class);
        TurRoutineAPI api = new TurRoutineAPI(repo);

        for (String bad : new String[] { null, "", "  ", "1starts-with-digit", "has spaces", "has/slash" }) {
            assertThatThrownBy(() -> api.create(new TurRoutineDto(
                    null, bad, null, TurRoutineKind.NATIVE, "x", null, 1000, true)))
                    .as("name '%s'", bad)
                    .isInstanceOf(ResponseStatusException.class)
                    .hasFieldOrPropertyWithValue("statusCode", HttpStatus.BAD_REQUEST);
        }
        verify(repo, never()).save(any(TurRoutine.class));
    }

    @Test
    @DisplayName("update: mutates existing and returns updated DTO")
    void update_happyPath() {
        TurRoutineRepository repo = mock(TurRoutineRepository.class);
        TurRoutine existing = routine("r1", "old_name");
        when(repo.findById("r1")).thenReturn(Optional.of(existing));
        when(repo.findByName("new_name")).thenReturn(Optional.empty());
        when(repo.save(any(TurRoutine.class))).thenAnswer(inv -> inv.getArgument(0));
        TurRoutineAPI api = new TurRoutineAPI(repo);

        TurRoutineDto result = api.update("r1", new TurRoutineDto(
                "r1", "new_name", "new desc",
                TurRoutineKind.NATIVE, "new_tool", null, 90_000, false));

        assertThat(result.name()).isEqualTo("new_name");
        assertThat(result.description()).isEqualTo("new desc");
        assertThat(result.nativeToolName()).isEqualTo("new_tool");
        assertThat(result.defaultTimeoutMs()).isEqualTo(90_000);
        assertThat(result.enabled()).isFalse();
    }

    @Test
    @DisplayName("update: rename collision with another row is 409")
    void update_renameCollision_isConflict() {
        TurRoutineRepository repo = mock(TurRoutineRepository.class);
        TurRoutine existing = routine("r1", "old_name");
        when(repo.findById("r1")).thenReturn(Optional.of(existing));
        when(repo.findByName("taken")).thenReturn(Optional.of(routine("r2", "taken")));
        TurRoutineAPI api = new TurRoutineAPI(repo);

        assertThatThrownBy(() -> api.update("r1", new TurRoutineDto(
                "r1", "taken", null, TurRoutineKind.NATIVE, null, null, 1000, true)))
                .isInstanceOf(ResponseStatusException.class)
                .hasFieldOrPropertyWithValue("statusCode", HttpStatus.CONFLICT);
    }

    @Test
    @DisplayName("update: same-name re-save is allowed (no false conflict)")
    void update_sameName_passesThrough() {
        TurRoutineRepository repo = mock(TurRoutineRepository.class);
        TurRoutine existing = routine("r1", "alpha");
        when(repo.findById("r1")).thenReturn(Optional.of(existing));
        when(repo.save(any(TurRoutine.class))).thenAnswer(inv -> inv.getArgument(0));
        TurRoutineAPI api = new TurRoutineAPI(repo);

        api.update("r1", new TurRoutineDto(
                "r1", "alpha", "new desc",
                TurRoutineKind.NATIVE, "tool", null, 1000, true));

        verify(repo, never()).findByName("alpha");
    }

    @Test
    @DisplayName("delete: returns true when row existed; repo.delete invoked")
    void delete_existing_returnsTrue() {
        TurRoutineRepository repo = mock(TurRoutineRepository.class);
        when(repo.findById("r1")).thenReturn(Optional.of(routine("r1", "alpha")));
        TurRoutineAPI api = new TurRoutineAPI(repo);

        assertThat(api.delete("r1")).isTrue();
        verify(repo).delete("r1");
    }

    @Test
    @DisplayName("delete: returns false when row missing (no delete call)")
    void delete_missing_returnsFalse() {
        TurRoutineRepository repo = mock(TurRoutineRepository.class);
        when(repo.findById("ghost")).thenReturn(Optional.empty());
        TurRoutineAPI api = new TurRoutineAPI(repo);

        assertThat(api.delete("ghost")).isFalse();
        verify(repo, never()).delete("ghost");
    }

    private static TurRoutine routine(String id, String name) {
        TurRoutine r = new TurRoutine();
        r.setId(id);
        r.setName(name);
        r.setKind(TurRoutineKind.NATIVE);
        r.setNativeToolName("tool_" + id);
        r.setDefaultTimeoutMs(60_000);
        r.setEnabled(true);
        return r;
    }
}
