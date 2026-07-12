/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.api.persona.match;

import java.time.Duration;
import java.util.List;

import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.viglet.turing.api.exception.TurNotFoundException;
import com.viglet.turing.genai.persona.match.TurPersonaMatchAnalysisService;
import com.viglet.turing.genai.persona.match.TurPersonaMatchEventBus;
import com.viglet.turing.genai.persona.match.TurPersonaMatchMatrixDto;
import com.viglet.turing.genai.persona.match.TurPersonaMatchRunEvent;
import com.viglet.turing.genai.persona.match.TurPersonaMatchService;
import com.viglet.turing.genai.persona.match.dto.TurPersonaMatchPersonaRefDto;
import com.viglet.turing.genai.persona.match.dto.TurPersonaMatchProjectDto;
import com.viglet.turing.genai.persona.match.dto.TurPersonaMatchReportDto;
import com.viglet.turing.genai.persona.match.dto.TurPersonaMatchSourceDto;
import com.viglet.turing.genai.persona.match.dto.TurPersonaMatchSourceRequest;
import com.viglet.turing.tenant.TurTenantContext;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * REST API for Persona Match projects (Block AT / §XLIII, T699). Admin console
 * surface (secured by the global {@code authenticated()} rule like the sibling
 * persona APIs). Exposes project CRUD, the persona set, project-scoped content
 * management (URL/indexed/upload/re-extract), the two report lenses, the
 * persisted matrix, and the N×N run — both blocking and as a live SSE stream
 * that fills the studio heatmap as cells complete.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@RestController
@RequestMapping("/api/persona-match")
@Tag(name = "Persona Match", description = "N×N persona↔content fit projects")
public class TurPersonaMatchAPI {

    private final TurPersonaMatchService service;
    private final TurPersonaMatchAnalysisService analysisService;
    private final TurPersonaMatchEventBus eventBus;
    private final TurTenantContext tenantContext;

    public TurPersonaMatchAPI(TurPersonaMatchService service,
            TurPersonaMatchAnalysisService analysisService,
            TurPersonaMatchEventBus eventBus,
            TurTenantContext tenantContext) {
        this.service = service;
        this.analysisService = analysisService;
        this.eventBus = eventBus;
        this.tenantContext = tenantContext;
    }

    // ---- project CRUD ------------------------------------------------------

    @Operation(summary = "List Persona Match projects")
    @GetMapping
    public List<TurPersonaMatchProjectDto> list() {
        return service.list();
    }

    @Operation(summary = "Show a project (with contents + personas)")
    @GetMapping("/{id}")
    public TurPersonaMatchProjectDto get(@PathVariable String id) {
        return service.get(id).orElseThrow(() -> TurNotFoundException.of("Persona Match project", id));
    }

    @Operation(summary = "Create a project")
    @PostMapping
    public TurPersonaMatchProjectDto create(@RequestBody TurPersonaMatchProjectDto dto) {
        return service.create(dto);
    }

    @Operation(summary = "Update a project")
    @PutMapping("/{id}")
    public TurPersonaMatchProjectDto update(@PathVariable String id,
            @RequestBody TurPersonaMatchProjectDto dto) {
        return service.update(id, dto)
                .orElseThrow(() -> TurNotFoundException.of("Persona Match project", id));
    }

    @Operation(summary = "Delete a project")
    @DeleteMapping("/{id}")
    public boolean delete(@PathVariable String id) {
        return service.delete(id);
    }

    // ---- persona set -------------------------------------------------------

    @Operation(summary = "The project's referenced personas")
    @GetMapping("/{id}/personas")
    public List<TurPersonaMatchPersonaRefDto> personas(@PathVariable String id) {
        return service.personas(id);
    }

    @Operation(summary = "Replace the project's referenced persona set")
    @PutMapping("/{id}/personas")
    public List<TurPersonaMatchPersonaRefDto> setPersonas(@PathVariable String id,
            @RequestBody List<String> personaIds) {
        return service.setPersonas(id, personaIds);
    }

    // ---- sources -----------------------------------------------------------

    @Operation(summary = "The project's contents")
    @GetMapping("/{id}/sources")
    public List<TurPersonaMatchSourceDto> sources(@PathVariable String id) {
        return service.sources(id);
    }

    @Operation(summary = "Add a URL or indexed-document content (auto-extracts)")
    @PostMapping("/{id}/sources")
    public TurPersonaMatchSourceDto addSource(@PathVariable String id,
            @RequestBody TurPersonaMatchSourceRequest req) {
        return service.addSource(id, req)
                .orElseThrow(() -> TurNotFoundException.of("Persona Match project", id));
    }

    @Operation(summary = "Upload a file content (auto-extracts)")
    @PostMapping(value = "/{id}/sources/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public TurPersonaMatchSourceDto uploadSource(@PathVariable String id,
            @RequestPart("file") MultipartFile file) {
        return service.uploadSource(id, file)
                .orElseThrow(() -> TurNotFoundException.of("Persona Match project", id));
    }

    @Operation(summary = "Re-extract a content's text")
    @PostMapping("/{id}/sources/{sourceId}/extract")
    public TurPersonaMatchSourceDto reExtract(@PathVariable String id,
            @PathVariable String sourceId) {
        return service.reExtract(id, sourceId)
                .orElseThrow(() -> TurNotFoundException.of("Persona Match content", sourceId));
    }

    @Operation(summary = "Remove a content")
    @DeleteMapping("/{id}/sources/{sourceId}")
    public boolean deleteSource(@PathVariable String id, @PathVariable String sourceId) {
        return service.deleteSource(id, sourceId);
    }

    // ---- matrix + report ---------------------------------------------------

    @Operation(summary = "The persisted N×N matrix cells")
    @GetMapping("/{id}/matrix")
    public TurPersonaMatchMatrixDto matrix(@PathVariable String id) {
        return analysisService.getMatrix(id);
    }

    @Operation(summary = "By-content and by-persona report lenses")
    @GetMapping("/{id}/report")
    public TurPersonaMatchReportDto report(@PathVariable String id) {
        return service.report(id);
    }

    // ---- run ---------------------------------------------------------------

    @Operation(summary = "Run the N×N analysis (blocking) and return the matrix")
    @PostMapping("/{id}/run")
    public TurPersonaMatchMatrixDto run(@PathVariable String id,
            @RequestParam(defaultValue = "false") boolean force) {
        if (service.get(id).isEmpty()) {
            throw TurNotFoundException.of("Persona Match project", id);
        }
        return analysisService.run(id, force);
    }

    @Operation(summary = "Run the N×N analysis and stream progress over SSE (live heatmap fill)")
    @PostMapping(value = "/{id}/run/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<TurPersonaMatchRunEvent>> runStream(@PathVariable String id,
            @RequestParam(defaultValue = "false") boolean force) {
        if (service.get(id).isEmpty()) {
            throw TurNotFoundException.of("Persona Match project", id);
        }
        String tenant = tenantContext.getCurrentTenant();

        Flux<ServerSentEvent<TurPersonaMatchRunEvent>> data = eventBus.subscribe(id)
                .takeUntil(TurPersonaMatchAPI::terminal)
                .map(event -> ServerSentEvent.builder(event).build());

        Flux<ServerSentEvent<TurPersonaMatchRunEvent>> run = Mono
                .fromRunnable(() -> tenantContext.runAs(tenant, () -> analysisService.run(id, force)))
                .subscribeOn(Schedulers.boundedElastic())
                .thenMany(Flux.empty());

        return Flux.merge(data, run, heartbeat())
                .takeUntil(sse -> sse.data() != null && terminal(sse.data()));
    }

    private static boolean terminal(TurPersonaMatchRunEvent event) {
        return event.type() == TurPersonaMatchRunEvent.Type.DONE
                || event.type() == TurPersonaMatchRunEvent.Type.ERROR;
    }

    /** 25s comment heartbeat so a long-running analysis keeps the SSE alive. */
    private static Flux<ServerSentEvent<TurPersonaMatchRunEvent>> heartbeat() {
        return Flux.interval(Duration.ofSeconds(25))
                .map(tick -> ServerSentEvent.<TurPersonaMatchRunEvent>builder()
                        .comment("heartbeat").build());
    }
}
