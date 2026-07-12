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
import java.time.Duration;

import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.security.access.annotation.Secured;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.viglet.turing.api.exception.TurNotFoundException;
import com.viglet.turing.genai.authoring.persona.TurPersonaAudioDeriveService;
import com.viglet.turing.genai.authoring.persona.TurPersonaAudioDeriveService.DraftResult;
import com.viglet.turing.genai.authoring.persona.TurPersonaAudioJobEventBus;
import com.viglet.turing.genai.authoring.persona.TurPersonaAudioJobService;
import com.viglet.turing.genai.authoring.persona.TurPersonaAudioJobStatus;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import reactor.core.publisher.Flux;

/**
 * Persona-from-audio authoring endpoint (Block AA / §XXVI.7). Uploads an audio
 * recording, transcribes it, and returns a <strong>draft</strong> persona for
 * human review — it is never auto-saved; the admin reviews and saves it through
 * the normal persona form.
 *
 * <p>T715 / §XLII.7 adds an <b>async</b> surface alongside the original blocking
 * POST, so a long recording can show a live progress bar instead of a spinner:
 * <ul>
 *   <li>{@code POST /api/persona/derive-from-audio} — blocking (legacy).</li>
 *   <li>{@code POST /api/persona/derive-from-audio/jobs} — submit → QUEUED status
 *   (HTTP 429 when the bounded pool is saturated).</li>
 *   <li>{@code GET  /api/persona/derive-from-audio/jobs/{jobId}} — poll status.</li>
 *   <li>{@code GET  /api/persona/derive-from-audio/jobs/{jobId}/stream} — SSE of
 *   state + per-chunk transcription progress; completes on the terminal event
 *   carrying the draft.</li>
 * </ul>
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@RestController
@RequestMapping("/api/persona/derive-from-audio")
@Tag(name = "AI Persona From Audio", description = "Draft a persona from a recording")
public class TurPersonaAudioAPI {

    private final TurPersonaAudioDeriveService deriveService;
    private final TurPersonaAudioJobService jobService;
    private final TurPersonaAudioJobEventBus eventBus;

    public TurPersonaAudioAPI(TurPersonaAudioDeriveService deriveService,
            TurPersonaAudioJobService jobService,
            TurPersonaAudioJobEventBus eventBus) {
        this.deriveService = deriveService;
        this.jobService = jobService;
        this.eventBus = eventBus;
    }

    @Operation(summary = "Transcribe an audio recording and draft a persona (blocking, not saved)")
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Secured({ "ROLE_ADMIN", "AI_AGENT_CREATE", "AI_AGENT_EDIT" })
    public DraftResult deriveFromAudio(
            @RequestPart("file") MultipartFile file,
            @RequestParam(value = "language", required = false) String language) {
        return deriveService.derive(readAudio(file), file.getContentType(), language);
    }

    @Operation(summary = "Submit an audio recording for background persona derivation")
    @PostMapping(path = "/jobs", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Secured({ "ROLE_ADMIN", "AI_AGENT_CREATE", "AI_AGENT_EDIT" })
    public TurPersonaAudioJobStatus submit(
            @RequestPart("file") MultipartFile file,
            @RequestParam(value = "language", required = false) String language) {
        return jobService.submit(readAudio(file), file.getContentType(), language);
    }

    @Operation(summary = "Poll the status/result of a persona-from-audio job")
    @GetMapping("/jobs/{jobId}")
    @Secured({ "ROLE_ADMIN", "AI_AGENT_CREATE", "AI_AGENT_EDIT", "AI_AGENT_VIEW" })
    public TurPersonaAudioJobStatus status(@PathVariable String jobId) {
        return jobService.getStatus(jobId)
                .orElseThrow(() -> new TurNotFoundException(
                        "Persona-from-audio job not found (unknown or expired): " + jobId));
    }

    @Operation(summary = "Live SSE stream of a persona-from-audio job; completes on terminal")
    @GetMapping(value = "/jobs/{jobId}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Secured({ "ROLE_ADMIN", "AI_AGENT_CREATE", "AI_AGENT_EDIT", "AI_AGENT_VIEW" })
    public Flux<ServerSentEvent<TurPersonaAudioJobStatus>> stream(@PathVariable String jobId) {
        TurPersonaAudioJobStatus current = jobService.getStatus(jobId)
                .orElseThrow(() -> new TurNotFoundException(
                        "Persona-from-audio job not found (unknown or expired): " + jobId));
        Flux<ServerSentEvent<TurPersonaAudioJobStatus>> data = eventBus.subscribe(jobId)
                .startWith(current)
                .takeUntil(TurPersonaAudioJobStatus::terminal)
                .map(status -> ServerSentEvent.builder(status).build());
        return Flux.merge(data, heartbeat())
                .takeUntil(sse -> sse.data() != null && sse.data().terminal());
    }

    /** 25s comment heartbeat so idle (long-running) jobs keep the SSE connection alive. */
    private static Flux<ServerSentEvent<TurPersonaAudioJobStatus>> heartbeat() {
        return Flux.interval(Duration.ofSeconds(25))
                .map(tick -> ServerSentEvent.<TurPersonaAudioJobStatus>builder()
                        .comment("heartbeat").build());
    }

    private static byte[] readAudio(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Audio file is required");
        }
        try {
            return file.getBytes();
        } catch (IOException e) {
            throw new IllegalArgumentException("Could not read the uploaded audio", e);
        }
    }
}
