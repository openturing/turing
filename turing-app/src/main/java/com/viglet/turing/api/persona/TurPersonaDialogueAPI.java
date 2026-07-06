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

import org.springframework.security.access.annotation.Secured;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.viglet.turing.genai.persona.dialogue.TurDialogueTranscript;
import com.viglet.turing.genai.persona.dialogue.TurPersonaDialogueService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Block AI / §XXXII.8 (T585) — automatic persona↔persona conversation.
 *
 * <p>Deliberately <b>global</b> (not scoped to one persona): a dialogue is
 * inherently about two peer speakers plus a topic, neither of which "owns" the
 * conversation, so the endpoint lives at {@code /api/v2/persona-dialogue}
 * instead of under {@code /api/v2/persona/{personaId}}. The orchestration —
 * turn budget, the mandatory-question directive, and driving the shared chat
 * executor per turn — lives in {@link TurPersonaDialogueService}; this
 * controller is a thin, blocking JSON seam that returns the whole transcript.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@RestController
@RequestMapping("/api/v2/persona-dialogue")
@Tag(name = "Persona Dialogue", description = "Run a turn-by-turn conversation between two personas")
public class TurPersonaDialogueAPI {

    private final TurPersonaDialogueService dialogueService;

    public TurPersonaDialogueAPI(TurPersonaDialogueService dialogueService) {
        this.dialogueService = dialogueService;
    }

    /**
     * Request to run a dialogue. {@code turns} is optional (defaults to
     * {@link TurPersonaDialogueService#DEFAULT_TURNS}, clamped server-side).
     */
    public record PersonaDialogueRequest(String topic, String personaAId, String personaBId,
            String llmInstanceId, Integer turns) {
    }

    @PostMapping
    @Secured({"ROLE_ADMIN", "AI_AGENT_VIEW", "AI_AGENT_EDIT"})
    @Operation(summary = "Run a bounded turn-by-turn dialogue between two personas on a topic")
    public TurDialogueTranscript dialogue(@RequestBody PersonaDialogueRequest request) {
        return dialogueService.run(request.topic(), request.personaAId(), request.personaBId(),
                request.llmInstanceId(), request.turns());
    }
}
