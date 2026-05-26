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

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.annotation.Secured;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.viglet.turing.persistence.dto.agent.TurRoutineDto;
import com.viglet.turing.persistence.model.agent.TurRoutine;
import com.viglet.turing.persistence.model.agent.TurRoutineKind;
import com.viglet.turing.persistence.repository.agent.TurRoutineRepository;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * T48 / §VII.4.c — CRUD for {@link TurRoutine}, the catalog of async jobs
 * a {@code scheduleAgent} chat-flow node can fire. Not agent-scoped:
 * routines are shared across every agent in the deployment (a single
 * "generate_proposal_pdf" routine serves both Education and B2B flows).
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
@RestController
@RequestMapping("/api/genai/routine")
@Tag(name = "Routines", description = "Async routines fired by scheduleAgent chat-flow nodes")
public class TurRoutineAPI {

    private final TurRoutineRepository routineRepository;

    public TurRoutineAPI(TurRoutineRepository routineRepository) {
        this.routineRepository = routineRepository;
    }

    @Operation(summary = "List all routines")
    @GetMapping
    @Secured({ "ROLE_ADMIN", "AI_AGENT_VIEW" })
    public List<TurRoutineDto> list() {
        return routineRepository.findAll().stream()
                .sorted(Comparator.comparing(TurRoutine::getName,
                        Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)))
                .map(TurRoutineDto::from)
                .toList();
    }

    @Operation(summary = "Get one routine by id")
    @GetMapping("/{id}")
    @Secured({ "ROLE_ADMIN", "AI_AGENT_VIEW" })
    public TurRoutineDto get(@PathVariable String id) {
        return routineRepository.findById(id)
                .map(TurRoutineDto::from)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Routine not found"));
    }

    @Operation(summary = "Create a new routine")
    @PostMapping
    @Secured({ "ROLE_ADMIN", "AI_AGENT_CREATE" })
    public TurRoutineDto create(@RequestBody TurRoutineDto dto) {
        validateName(dto.name());
        routineRepository.findByName(dto.name()).ifPresent(existing -> {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Routine name already exists");
        });
        TurRoutine entity = new TurRoutine();
        applyDtoToEntity(dto, entity);
        // Clear id so the assignable UUID generator allocates a fresh one
        // (some clients post the empty string by default).
        entity.setId(null);
        return TurRoutineDto.from(routineRepository.save(entity));
    }

    @Operation(summary = "Update an existing routine")
    @PutMapping("/{id}")
    @Secured({ "ROLE_ADMIN", "AI_AGENT_EDIT" })
    public TurRoutineDto update(@PathVariable String id, @RequestBody TurRoutineDto dto) {
        validateName(dto.name());
        TurRoutine existing = routineRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Routine not found"));
        if (!existing.getName().equals(dto.name())) {
            routineRepository.findByName(dto.name())
                    .filter(other -> !other.getId().equals(id))
                    .ifPresent(other -> {
                        throw new ResponseStatusException(HttpStatus.CONFLICT,
                                "Routine name already exists");
                    });
        }
        applyDtoToEntity(dto, existing);
        return TurRoutineDto.from(routineRepository.save(existing));
    }

    @Transactional
    @Operation(summary = "Delete a routine")
    @DeleteMapping("/{id}")
    @Secured({ "ROLE_ADMIN", "AI_AGENT_DELETE" })
    public boolean delete(@PathVariable String id) {
        return routineRepository.findById(id)
                .map(r -> {
                    routineRepository.delete(id);
                    return true;
                }).orElse(false);
    }

    private static void applyDtoToEntity(TurRoutineDto dto, TurRoutine entity) {
        entity.setName(dto.name());
        entity.setDescription(dto.description());
        entity.setKind(dto.kind() == null ? TurRoutineKind.NATIVE : dto.kind());
        entity.setNativeToolName(dto.nativeToolName());
        entity.setGroovyScript(dto.groovyScript());
        // Falls back to the entity's current default (60_000 on insert) when
        // the client omits the field.
        if (dto.defaultTimeoutMs() != null && dto.defaultTimeoutMs() > 0) {
            entity.setDefaultTimeoutMs(dto.defaultTimeoutMs());
        }
        entity.setEnabled(Optional.ofNullable(dto.enabled()).orElse(Boolean.TRUE));
    }

    /**
     * Routine names must be safe as JSON keys and as identifiers in a
     * chat-flow definition: alphanumeric + underscore/hyphen/dot, starting
     * with a letter or underscore. Same rationale as TurAIAgentSlot —
     * defense against malicious payloads that smuggle special characters
     * into the schema.
     */
    private static void validateName(String name) {
        if (name == null || name.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Routine name is required");
        }
        if (name.length() > 128) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Routine name is too long");
        }
        if (!name.matches("[A-Za-z_][A-Za-z0-9_.\\-]*")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Routine name must start with a letter or underscore and contain only letters, digits, underscore, hyphen, or dot");
        }
    }
}
