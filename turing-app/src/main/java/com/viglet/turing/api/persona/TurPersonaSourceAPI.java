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

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.annotation.Secured;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import com.viglet.turing.genai.persona.TurPersonaSourceService;
import com.viglet.turing.persistence.dto.persona.TurPersonaSourceDto;
import com.viglet.turing.persistence.mapper.persona.TurPersonaSourceMapper;
import com.viglet.turing.persistence.model.persona.TurPersona;
import com.viglet.turing.persistence.model.persona.TurPersonaSource;
import com.viglet.turing.persistence.model.persona.TurPersonaSourceType;
import com.viglet.turing.persistence.repository.persona.TurPersonaRepository;
import com.viglet.turing.persistence.repository.persona.TurPersonaSourceRepository;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Admin REST surface for a persona's evaluation "notebook" — the set of
 * {@link TurPersonaSource} rows the content-fit evaluator judges against
 * (Block AA / §XXVI.2). Nested under the owning persona; mirrors the
 * {@code TurAIAgentSlotAPI} parent→child CRUD pattern.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@RestController
@RequestMapping("/api/persona/{personaId}/source")
@Tag(name = "AI Persona Source", description = "Persona notebook evaluation sources API")
public class TurPersonaSourceAPI {

    private final TurPersonaSourceRepository sourceRepository;
    private final TurPersonaSourceMapper sourceMapper;
    private final TurPersonaRepository personaRepository;
    private final TurPersonaSourceService sourceService;

    public TurPersonaSourceAPI(TurPersonaSourceRepository sourceRepository,
            TurPersonaSourceMapper sourceMapper,
            TurPersonaRepository personaRepository,
            TurPersonaSourceService sourceService) {
        this.sourceRepository = sourceRepository;
        this.sourceMapper = sourceMapper;
        this.personaRepository = personaRepository;
        this.sourceService = sourceService;
    }

    @Operation(summary = "List a persona's evaluation sources")
    @GetMapping
    @Secured({ "ROLE_ADMIN", "AI_AGENT_VIEW" })
    public List<TurPersonaSourceDto> list(@PathVariable String personaId) {
        return sourceMapper.toDtoList(
                sourceRepository.findByTurPersona_IdOrderBySourceNameAsc(personaId));
    }

    @Operation(summary = "Show one evaluation source")
    @GetMapping("/{id}")
    @Secured({ "ROLE_ADMIN", "AI_AGENT_VIEW" })
    public TurPersonaSourceDto get(@PathVariable String personaId, @PathVariable String id) {
        return sourceMapper.toDto(requireOwned(personaId, id));
    }

    @Operation(summary = "Add an SN-document or URL source (and extract its text)")
    @PostMapping
    @Secured({ "ROLE_ADMIN", "AI_AGENT_CREATE", "AI_AGENT_EDIT" })
    public TurPersonaSourceDto create(@PathVariable String personaId,
            @RequestBody TurPersonaSourceDto dto) {
        TurPersona persona = requirePersona(personaId);
        if (dto.getType() == null || dto.getType() == TurPersonaSourceType.ASSET) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Use /upload for ASSET sources; type must be SN_DOC or URL");
        }
        TurPersonaSource entity = sourceMapper.toEntity(dto);
        entity.setId(null);
        entity.setTurPersona(persona);
        entity.setCachedText(null);
        sourceService.extract(entity);
        sourceRepository.save(entity);
        return sourceMapper.toDto(entity);
    }

    @Operation(summary = "Upload a document (PDF/DOC/…) as an evaluation source")
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Secured({ "ROLE_ADMIN", "AI_AGENT_CREATE", "AI_AGENT_EDIT" })
    public TurPersonaSourceDto upload(@PathVariable String personaId,
            @RequestPart("file") MultipartFile file,
            @RequestParam(value = "sourceName", required = false) String sourceName) {
        TurPersona persona = requirePersona(personaId);
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "File is required");
        }
        TurPersonaSource entity = new TurPersonaSource();
        entity.setTurPersona(persona);
        entity.setType(TurPersonaSourceType.ASSET);
        entity.setRef(file.getOriginalFilename());
        entity.setSourceName(sourceName == null || sourceName.isBlank()
                ? file.getOriginalFilename() : sourceName);
        sourceService.extractFromUpload(entity, file);
        sourceRepository.save(entity);
        return sourceMapper.toDto(entity);
    }

    @Operation(summary = "Re-extract a source's cached text")
    @PostMapping("/{id}/extract")
    @Secured({ "ROLE_ADMIN", "AI_AGENT_CREATE", "AI_AGENT_EDIT" })
    public TurPersonaSourceDto reextract(@PathVariable String personaId, @PathVariable String id) {
        TurPersonaSource entity = requireOwned(personaId, id);
        sourceService.extract(entity);
        sourceRepository.save(entity);
        return sourceMapper.toDto(entity);
    }

    @Transactional
    @Operation(summary = "Delete an evaluation source")
    @DeleteMapping("/{id}")
    @Secured({ "ROLE_ADMIN", "AI_AGENT_DELETE" })
    public boolean delete(@PathVariable String personaId, @PathVariable String id) {
        requireOwned(personaId, id);
        sourceRepository.delete(id);
        return true;
    }

    private TurPersona requirePersona(String personaId) {
        return personaRepository.findById(personaId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Persona not found"));
    }

    private TurPersonaSource requireOwned(String personaId, String id) {
        return sourceRepository.findById(id)
                .filter(s -> s.getTurPersona() != null
                        && personaId.equals(s.getTurPersona().getId()))
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Source not found"));
    }
}
