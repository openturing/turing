/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.api.persona.dialogue;

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
import org.springframework.web.bind.annotation.RestController;

import com.viglet.turing.api.exception.TurNotFoundException;
import com.viglet.turing.genai.persona.dialogue.TurDialogueEvent;
import com.viglet.turing.genai.persona.dialogue.TurPersonaDialogueProjectService;
import com.viglet.turing.genai.persona.dialogue.dto.TurPersonaDialogueProjectDto;
import com.viglet.turing.genai.persona.dialogue.dto.TurPersonaDialogueSpeakerDto;
import com.viglet.turing.genai.persona.dialogue.dto.TurPersonaDialogueTurnDto;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import reactor.core.publisher.Flux;

/**
 * REST API for Persona Dialogue projects (Block AU / §XLIV, T705) — the saved,
 * multi-project analogue of the ephemeral {@code /api/v2/persona-dialogue}
 * stream. Admin console surface (secured by the global {@code authenticated()}
 * rule like the sibling persona / persona-match APIs). Exposes project CRUD, the
 * ordered speaker roster, the persisted last transcript, and a project-scoped run
 * that streams the dialogue live over SSE while persisting the transcript.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@RestController
@RequestMapping("/api/persona-dialogue")
@Tag(name = "Persona Dialogue Projects", description = "Saved persona↔persona dialogue projects")
public class TurPersonaDialogueProjectAPI {

    private final TurPersonaDialogueProjectService service;

    public TurPersonaDialogueProjectAPI(TurPersonaDialogueProjectService service) {
        this.service = service;
    }

    @Operation(summary = "List dialogue projects")
    @GetMapping
    public List<TurPersonaDialogueProjectDto> list() {
        return service.list();
    }

    @Operation(summary = "Show a dialogue project (with speakers + last transcript)")
    @GetMapping("/{id}")
    public TurPersonaDialogueProjectDto get(@PathVariable String id) {
        return service.get(id).orElseThrow(() -> TurNotFoundException.of("Dialogue project", id));
    }

    @Operation(summary = "Create a dialogue project")
    @PostMapping
    public TurPersonaDialogueProjectDto create(@RequestBody TurPersonaDialogueProjectDto dto) {
        return service.create(dto);
    }

    @Operation(summary = "Update a dialogue project")
    @PutMapping("/{id}")
    public TurPersonaDialogueProjectDto update(@PathVariable String id,
            @RequestBody TurPersonaDialogueProjectDto dto) {
        return service.update(id, dto)
                .orElseThrow(() -> TurNotFoundException.of("Dialogue project", id));
    }

    @Operation(summary = "Delete a dialogue project")
    @DeleteMapping("/{id}")
    public boolean delete(@PathVariable String id) {
        return service.delete(id);
    }

    @Operation(summary = "The project's ordered speaker roster")
    @GetMapping("/{id}/speakers")
    public List<TurPersonaDialogueSpeakerDto> speakers(@PathVariable String id) {
        return service.speakers(id);
    }

    @Operation(summary = "Replace the ordered speaker roster (array order = speaking order)")
    @PutMapping("/{id}/speakers")
    public List<TurPersonaDialogueSpeakerDto> setSpeakers(@PathVariable String id,
            @RequestBody List<String> personaIds) {
        return service.setSpeakers(id, personaIds);
    }

    @Operation(summary = "The project's last persisted transcript")
    @GetMapping("/{id}/transcript")
    public List<TurPersonaDialogueTurnDto> transcript(@PathVariable String id) {
        return service.transcript(id);
    }

    @Operation(summary = "Run the dialogue and stream it live over SSE (persists the transcript)")
    @PostMapping(value = "/{id}/run/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<TurDialogueEvent>> run(@PathVariable String id) {
        if (service.get(id).isEmpty()) {
            throw TurNotFoundException.of("Dialogue project", id);
        }
        /*
         * Wrap each turn in a ServerSentEvent (the canonical Spring MVC SSE shape,
         * same as the persona-match stream) so the framework flushes one event at
         * a time — a raw Flux<POJO> can buffer, making the whole dialogue land at
         * once instead of turn-by-turn. A comment heartbeat keeps the connection
         * alive across the long per-turn LLM calls; the stream ends on the
         * terminal DONE/ERROR event.
         */
        Flux<ServerSentEvent<TurDialogueEvent>> data = service.run(id)
                .map(event -> ServerSentEvent.builder(event).build());
        Flux<ServerSentEvent<TurDialogueEvent>> heartbeat = Flux.interval(Duration.ofSeconds(20))
                .map(tick -> ServerSentEvent.<TurDialogueEvent>builder().comment("heartbeat").build());
        return Flux.merge(data, heartbeat)
                .takeUntil(sse -> sse.data() != null && terminal(sse.data()));
    }

    private static boolean terminal(TurDialogueEvent event) {
        return event.type() == TurDialogueEvent.TurDialogueEventType.DONE
                || event.type() == TurDialogueEvent.TurDialogueEventType.ERROR;
    }
}
