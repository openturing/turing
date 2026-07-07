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

import org.springframework.http.MediaType;
import org.springframework.security.access.annotation.Secured;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.viglet.turing.genai.persona.dialogue.TurDialogueEvent;
import com.viglet.turing.genai.persona.dialogue.TurPersonaDialogueService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import reactor.core.publisher.Flux;

/**
 * Block AI / §XXXII.8 (T585; N-persona + streaming in T604) — automatic
 * persona↔persona conversation.
 *
 * <p>Deliberately <b>global</b> (not scoped to one persona): a dialogue is
 * inherently about peer speakers plus a topic, none of which "owns" the
 * conversation, so the endpoint lives at {@code /api/v2/persona-dialogue}
 * instead of under {@code /api/v2/persona/{personaId}}. It <b>streams</b> the
 * dialogue as server-sent {@link TurDialogueEvent}s (one per completed turn) so
 * the client renders the conversation live. The orchestration — round-robin over
 * N personas, the turn budget, the mandatory-question directive, and driving the
 * shared chat executor per turn — lives in {@link TurPersonaDialogueService}.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@RestController
@RequestMapping("/api/v2/persona-dialogue")
@Tag(name = "Persona Dialogue", description = "Stream a turn-by-turn conversation between two or more personas")
public class TurPersonaDialogueAPI {

    private final TurPersonaDialogueService dialogueService;

    public TurPersonaDialogueAPI(TurPersonaDialogueService dialogueService) {
        this.dialogueService = dialogueService;
    }

    /**
     * Request to run a dialogue. {@code personaIds} is the ordered roster (two or
     * more speakers); {@code turns} is optional (defaults to
     * {@link TurPersonaDialogueService#DEFAULT_TURNS}, clamped server-side).
     */
    public record PersonaDialogueRequest(String topic, List<String> personaIds,
            String llmInstanceId, Integer turns) {
    }

    @PostMapping(produces = MediaType.TEXT_EVENT_STREAM_VALUE, consumes = MediaType.APPLICATION_JSON_VALUE)
    @Secured({"ROLE_ADMIN", "AI_AGENT_VIEW", "AI_AGENT_EDIT"})
    @Operation(summary = "Stream a bounded turn-by-turn dialogue between two or more personas on a topic")
    public Flux<TurDialogueEvent> dialogue(@RequestBody PersonaDialogueRequest request) {
        return dialogueService.stream(request.topic(), request.personaIds(),
                request.llmInstanceId(), request.turns());
    }
}
