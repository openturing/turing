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

import java.util.List;

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

import com.viglet.turing.persistence.dto.agent.TurAIAgentSlotDto;
import com.viglet.turing.persistence.mapper.agent.TurAIAgentSlotMapper;
import com.viglet.turing.persistence.model.agent.TurAIAgent;
import com.viglet.turing.persistence.model.agent.TurAIAgentSlot;
import com.viglet.turing.persistence.repository.agent.TurAIAgentRepository;
import com.viglet.turing.persistence.repository.agent.TurAIAgentSlotRepository;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Agent-scoped REST API for typed slots — the catalogue of variables the
 * chat-flow editor offers as {@code outputVariable} so values land in a
 * declared schema instead of free-form strings.
 *
 * @author Alexandre Oliveira
 * @since 2026.2.7
 */
@RestController
@RequestMapping("/api/ai-agent/{agentId}/slot")
@Tag(name = "AI Agent Slot", description = "AI Agent typed slots (variable catalog) API")
public class TurAIAgentSlotAPI {

    private final TurAIAgentSlotRepository slotRepository;
    private final TurAIAgentSlotMapper slotMapper;
    private final TurAIAgentRepository turAIAgentRepository;

    public TurAIAgentSlotAPI(TurAIAgentSlotRepository slotRepository,
            TurAIAgentSlotMapper slotMapper,
            TurAIAgentRepository turAIAgentRepository) {
        this.slotRepository = slotRepository;
        this.slotMapper = slotMapper;
        this.turAIAgentRepository = turAIAgentRepository;
    }

    private TurAIAgent loadAgent(String agentId) {
        return turAIAgentRepository.findById(agentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "AI Agent not found"));
    }

    @Operation(summary = "List slots of an Agent")
    @GetMapping
    @Secured({ "ROLE_ADMIN", "AI_AGENT_VIEW" })
    public List<TurAIAgentSlotDto> list(@PathVariable String agentId) {
        return slotMapper.toDtoList(
                slotRepository.findByTurAIAgent_IdOrderByNameAsc(agentId));
    }

    @Operation(summary = "Get one slot")
    @GetMapping("/{id}")
    @Secured({ "ROLE_ADMIN", "AI_AGENT_VIEW" })
    public TurAIAgentSlotDto get(@PathVariable String agentId, @PathVariable String id) {
        return slotRepository.findById(id)
                .filter(s -> s.getTurAIAgent() != null
                        && agentId.equals(s.getTurAIAgent().getId()))
                .map(slotMapper::toDto)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Slot not found"));
    }

    @Operation(summary = "Create a slot for an Agent")
    @PostMapping
    @Secured({ "ROLE_ADMIN", "AI_AGENT_CREATE" })
    public TurAIAgentSlotDto create(@PathVariable String agentId,
            @RequestBody TurAIAgentSlotDto dto) {
        TurAIAgent agent = loadAgent(agentId);
        validateName(dto.getName());
        slotRepository.findByTurAIAgent_IdAndName(agentId, dto.getName())
                .ifPresent(existing -> {
                    throw new ResponseStatusException(HttpStatus.CONFLICT,
                            "Slot name already exists for this agent");
                });
        TurAIAgentSlot entity = slotMapper.toEntity(dto);
        // The UUID generator preserves any pre-assigned id, including the
        // empty string sent by some clients. Force null so a fresh UUID is
        // generated on insert.
        entity.setId(null);
        entity.setTurAIAgent(agent);
        slotRepository.save(entity);
        return slotMapper.toDto(entity);
    }

    @Operation(summary = "Update a slot")
    @PutMapping("/{id}")
    @Secured({ "ROLE_ADMIN", "AI_AGENT_EDIT" })
    public TurAIAgentSlotDto update(@PathVariable String agentId,
            @PathVariable String id,
            @RequestBody TurAIAgentSlotDto dto) {
        validateName(dto.getName());
        TurAIAgentSlot existing = slotRepository.findById(id)
                .filter(s -> s.getTurAIAgent() != null
                        && agentId.equals(s.getTurAIAgent().getId()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Slot not found"));
        // Renaming to a name already used by another slot on the same agent
        // would violate the unique constraint at flush time; fail early with
        // a clear 409 instead of bubbling up the DB exception.
        if (!existing.getName().equals(dto.getName())) {
            slotRepository.findByTurAIAgent_IdAndName(agentId, dto.getName())
                    .filter(other -> !other.getId().equals(id))
                    .ifPresent(other -> {
                        throw new ResponseStatusException(HttpStatus.CONFLICT,
                                "Slot name already exists for this agent");
                    });
        }
        existing.setName(dto.getName());
        existing.setDescription(dto.getDescription());
        if (dto.getType() != null) {
            existing.setType(dto.getType());
        }
        slotRepository.save(existing);
        return slotMapper.toDto(existing);
    }

    @Transactional
    @Operation(summary = "Delete a slot")
    @DeleteMapping("/{id}")
    @Secured({ "ROLE_ADMIN", "AI_AGENT_DELETE" })
    public boolean delete(@PathVariable String agentId, @PathVariable String id) {
        return slotRepository.findById(id)
                .filter(s -> s.getTurAIAgent() != null
                        && agentId.equals(s.getTurAIAgent().getId()))
                .map(s -> {
                    slotRepository.delete(id);
                    return true;
                }).orElse(false);
    }

    /**
     * Slot names must be safe as JSON keys and as variables in a chat-flow
     * outputVariable: alphanumeric + underscore, must start with a letter or
     * underscore. Validated server-side so a malicious payload cannot
     * smuggle dots or brackets into the schema.
     */
    private static void validateName(String name) {
        if (name == null || name.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Slot name is required");
        }
        if (name.length() > 100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Slot name is too long");
        }
        if (!name.matches("[A-Za-z_][A-Za-z0-9_]*")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Slot name must start with a letter or underscore and contain only letters, digits, and underscores");
        }
    }
}
