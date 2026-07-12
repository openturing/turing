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

import java.util.List;
import java.util.Map;

import org.springframework.security.access.annotation.Secured;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.viglet.turing.genai.transcription.TurTranscriptionMetricsService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * T693 / §XLII.7 — per-backend transcription metrics for the admin surface: one
 * percentile row per backend (OPENAI / OPENAI_COMPATIBLE / NONE)
 * with count, error rate, latency percentiles and total bytes — the data an
 * operator uses to judge whether the local path is fast enough or the cloud
 * fallback is carrying the load.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@RestController
@RequestMapping("/api/genai/transcription/metrics")
@Tag(name = "Transcription Metrics", description = "Per-backend transcription latency/cost")
public class TurTranscriptionMetricsAPI {

    private final TurTranscriptionMetricsService metricsService;

    public TurTranscriptionMetricsAPI(TurTranscriptionMetricsService metricsService) {
        this.metricsService = metricsService;
    }

    @Operation(summary = "Per-backend transcription metrics (latency percentiles, error rate, bytes)")
    @GetMapping
    @Secured({ "ROLE_ADMIN", "AI_AGENT_VIEW" })
    public List<Map<String, Object>> metrics() {
        return metricsService.rows();
    }
}
