/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.api.persona;

import java.util.List;

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

import com.viglet.turing.persistence.dto.persona.TurPersonaDto;
import com.viglet.turing.persistence.mapper.persona.TurPersonaMapper;
import com.viglet.turing.persistence.model.persona.TurPersona;
import com.viglet.turing.persistence.repository.mcp.TurMcpServerRepository;
import com.viglet.turing.persistence.repository.persona.TurPersonaRepository;
import com.viglet.turing.persistence.repository.store.TurStoreInstanceRepository;
import com.viglet.turing.spring.utils.TurPersistenceUtils;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Admin REST surface for {@link TurPersona}. Mirrors {@code TurAIAgentAPI}:
 * the controller does the relation-resolution step (translating
 * detached-DTO references into managed entities) inline rather than
 * delegating to a separate service.
 *
 * @author Alexandre Oliveira
 * @since 2026.2.6
 */
@RestController
@RequestMapping("/api/persona")
@Tag(name = "AI Persona", description = "AI Persona API")
public class TurPersonaAPI {

    private final TurPersonaRepository turPersonaRepository;
    private final TurPersonaMapper turPersonaMapper;
    private final TurStoreInstanceRepository turStoreInstanceRepository;
    private final TurMcpServerRepository turMcpServerRepository;

    public TurPersonaAPI(TurPersonaRepository turPersonaRepository,
            TurPersonaMapper turPersonaMapper,
            TurStoreInstanceRepository turStoreInstanceRepository,
            TurMcpServerRepository turMcpServerRepository) {
        this.turPersonaRepository = turPersonaRepository;
        this.turPersonaMapper = turPersonaMapper;
        this.turStoreInstanceRepository = turStoreInstanceRepository;
        this.turMcpServerRepository = turMcpServerRepository;
    }

    @Operation(summary = "Persona List")
    @GetMapping
    @Secured({"ROLE_ADMIN", "AI_AGENT_VIEW"})
    public List<TurPersonaDto> turPersonaList() {
        return turPersonaMapper.toDtoList(
                turPersonaRepository.findAll(TurPersistenceUtils.orderByTitleIgnoreCase()));
    }

    @Operation(summary = "Persona structure")
    @GetMapping("/structure")
    @Secured({"ROLE_ADMIN", "AI_AGENT_VIEW"})
    public TurPersonaDto turPersonaStructure() {
        return turPersonaMapper.toDto(new TurPersona());
    }

    @Operation(summary = "Show a Persona")
    @GetMapping("/{id}")
    @Secured({"ROLE_ADMIN", "AI_AGENT_VIEW"})
    public TurPersonaDto turPersonaGet(@PathVariable String id) {
        return turPersonaMapper.toDto(
                turPersonaRepository.findById(id).orElse(new TurPersona()));
    }

    @Operation(summary = "Update a Persona")
    @PutMapping("/{id}")
    @Secured({"ROLE_ADMIN", "AI_AGENT_EDIT"})
    public TurPersonaDto turPersonaUpdate(@PathVariable String id,
            @RequestBody TurPersonaDto dto) {
        TurPersona incoming = turPersonaMapper.toEntity(dto);
        return turPersonaRepository.findById(id).map(persisted -> {
            copyEditableFields(incoming, persisted);
            persisted.setFewShotStore(resolveStore(incoming.getFewShotStore() == null
                    ? null : incoming.getFewShotStore().getId()));
            persisted.setBrandContextMcpServer(resolveMcp(incoming.getBrandContextMcpServer() == null
                    ? null : incoming.getBrandContextMcpServer().getId()));
            turPersonaRepository.save(persisted);
            return turPersonaMapper.toDto(persisted);
        }).orElse(new TurPersonaDto());
    }

    @Operation(summary = "Create a Persona")
    @PostMapping
    @Secured({"ROLE_ADMIN", "AI_AGENT_CREATE"})
    public TurPersonaDto turPersonaAdd(@RequestBody TurPersonaDto dto) {
        TurPersona entity = turPersonaMapper.toEntity(dto);
        entity.setFewShotStore(resolveStore(entity.getFewShotStore() == null
                ? null : entity.getFewShotStore().getId()));
        entity.setBrandContextMcpServer(resolveMcp(entity.getBrandContextMcpServer() == null
                ? null : entity.getBrandContextMcpServer().getId()));
        turPersonaRepository.save(entity);
        return turPersonaMapper.toDto(entity);
    }

    @Transactional
    @Operation(summary = "Delete a Persona")
    @DeleteMapping("/{id}")
    @Secured({"ROLE_ADMIN", "AI_AGENT_DELETE"})
    public boolean turPersonaDelete(@PathVariable String id) {
        turPersonaRepository.delete(id);
        return true;
    }

    private static void copyEditableFields(TurPersona from, TurPersona to) {
        to.setName(from.getName());
        to.setDescription(from.getDescription());
        to.setSystemInstruction(from.getSystemInstruction());
        to.setTone(from.getTone());
        to.setVerbosity(from.getVerbosity());
        to.setLanguageStyle(from.getLanguageStyle());
        to.setMandatoryTerms(from.getMandatoryTerms());
        to.setForbiddenTerms(from.getForbiddenTerms());
        to.setEnabled(from.getEnabled());
    }

    private com.viglet.turing.persistence.model.store.TurStoreInstance resolveStore(String id) {
        if (id == null || id.isBlank()) {
            return null;
        }
        return turStoreInstanceRepository.findById(id).orElse(null);
    }

    private com.viglet.turing.persistence.model.mcp.TurMcpServer resolveMcp(String id) {
        if (id == null || id.isBlank()) {
            return null;
        }
        return turMcpServerRepository.findById(id).orElse(null);
    }
}
