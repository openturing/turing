/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.api.genai;

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
import com.viglet.turing.genai.transcription.TurTranscriptionJobEventBus;
import com.viglet.turing.genai.transcription.TurTranscriptionJobService;
import com.viglet.turing.genai.transcription.TurTranscriptionJobStatus;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import reactor.core.publisher.Flux;

/**
 * T692 / §XLII.6 — async transcription job surface. Upload long audio, get a job
 * id back immediately, then poll the status or subscribe to the SSE stream for
 * the transcript — keeping multi-hour recordings off the HTTP request thread.
 *
 * <ul>
 *   <li>{@code POST /api/genai/transcription/jobs} — submit audio → QUEUED
 *   status (HTTP 429 when the bounded pool is saturated).</li>
 *   <li>{@code GET  /api/genai/transcription/jobs/{jobId}} — poll status.</li>
 *   <li>{@code GET  /api/genai/transcription/jobs/{jobId}/stream} — SSE of state
 *   changes; completes on the terminal event.</li>
 * </ul>
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@RestController
@RequestMapping("/api/genai/transcription/jobs")
@Tag(name = "Transcription Jobs", description = "Async, chunk-aware speech-to-text jobs")
public class TurTranscriptionJobAPI {

    private final TurTranscriptionJobService jobService;
    private final TurTranscriptionJobEventBus eventBus;

    public TurTranscriptionJobAPI(TurTranscriptionJobService jobService,
            TurTranscriptionJobEventBus eventBus) {
        this.jobService = jobService;
        this.eventBus = eventBus;
    }

    @Operation(summary = "Submit an audio recording for background transcription")
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Secured({ "ROLE_ADMIN", "AI_AGENT_CREATE", "AI_AGENT_EDIT" })
    public TurTranscriptionJobStatus submit(
            @RequestPart("file") MultipartFile file,
            @RequestParam(value = "language", required = false) String language) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Audio file is required");
        }
        try {
            return jobService.submit(file.getBytes(), file.getContentType(), language);
        } catch (IOException e) {
            throw new IllegalArgumentException("Could not read the uploaded audio", e);
        }
    }

    @Operation(summary = "Poll the status/result of a transcription job")
    @GetMapping("/{jobId}")
    @Secured({ "ROLE_ADMIN", "AI_AGENT_CREATE", "AI_AGENT_EDIT", "AI_AGENT_VIEW" })
    public TurTranscriptionJobStatus status(@PathVariable String jobId) {
        return jobService.getStatus(jobId)
                .orElseThrow(() -> new TurNotFoundException(
                        "Transcription job not found (unknown or expired): " + jobId));
    }

    @Operation(summary = "Live SSE stream of a transcription job's state; completes on terminal")
    @GetMapping(value = "/{jobId}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Secured({ "ROLE_ADMIN", "AI_AGENT_CREATE", "AI_AGENT_EDIT", "AI_AGENT_VIEW" })
    public Flux<ServerSentEvent<TurTranscriptionJobStatus>> stream(@PathVariable String jobId) {
        TurTranscriptionJobStatus current = jobService.getStatus(jobId).orElse(null);
        if (current == null) {
            throw new TurNotFoundException(
                    "Transcription job not found (unknown or expired): " + jobId);
        }
        Flux<ServerSentEvent<TurTranscriptionJobStatus>> data = eventBus.subscribe(jobId)
                .startWith(current)
                .takeUntil(TurTranscriptionJobStatus::terminal)
                .map(status -> ServerSentEvent.builder(status).build());
        return Flux.merge(data, heartbeat())
                .takeUntil(sse -> sse.data() != null && sse.data().terminal());
    }

    /** 25s comment heartbeat so idle (long-running) jobs keep the SSE connection alive. */
    private static Flux<ServerSentEvent<TurTranscriptionJobStatus>> heartbeat() {
        return Flux.interval(Duration.ofSeconds(25))
                .map(tick -> ServerSentEvent.<TurTranscriptionJobStatus>builder()
                        .comment("heartbeat").build());
    }
}
