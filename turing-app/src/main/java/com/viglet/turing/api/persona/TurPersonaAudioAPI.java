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

import java.io.IOException;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.annotation.Secured;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import com.viglet.turing.genai.authoring.persona.TurPersonaAudioDeriveService;
import com.viglet.turing.genai.authoring.persona.TurPersonaAudioDeriveService.DraftResult;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Persona-from-audio authoring endpoint (Block AA / §XXVI.7). Uploads an audio
 * recording, transcribes it, and returns a <strong>draft</strong> persona for
 * human review — it is never auto-saved; the admin reviews and saves it through
 * the normal persona form.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@RestController
@RequestMapping("/api/persona/derive-from-audio")
@Tag(name = "AI Persona From Audio", description = "Draft a persona from a recording")
public class TurPersonaAudioAPI {

    private final TurPersonaAudioDeriveService deriveService;

    public TurPersonaAudioAPI(TurPersonaAudioDeriveService deriveService) {
        this.deriveService = deriveService;
    }

    @Operation(summary = "Transcribe an audio recording and draft a persona (not saved)")
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Secured({ "ROLE_ADMIN", "AI_AGENT_CREATE", "AI_AGENT_EDIT" })
    public DraftResult deriveFromAudio(
            @RequestPart("file") MultipartFile file,
            @RequestParam(value = "language", required = false) String language) {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Audio file is required");
        }
        try {
            return deriveService.derive(file.getBytes(), file.getContentType(), language);
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Could not read the uploaded audio", e);
        }
    }
}
