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

import com.viglet.turing.genai.authoring.persona.TurAudienceCohortService;
import com.viglet.turing.genai.authoring.persona.TurAudienceCohortService.CohortResult;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Audience cohort synthesis endpoint (Block AW / §XLVI.5, T731): a one-paragraph
 * audience brief in, a <strong>diverse</strong> set of never-saved persona drafts
 * (with spread OCEAN facets) out, for the Synthetic Research studio's cohort-from-
 * brief flow. Like persona-from-audio, the drafts are handed to the existing
 * persona review form — nothing is persisted here.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@RestController
@RequestMapping("/api/persona/cohort")
@Tag(name = "AI Audience Cohort", description = "Synthesize a diverse persona cohort from a brief")
public class TurAudienceCohortAPI {

    private final TurAudienceCohortService cohortService;

    public TurAudienceCohortAPI(TurAudienceCohortService cohortService) {
        this.cohortService = cohortService;
    }

    /** Cohort synthesis request: a brief, how many personas, and a regenerate flag. */
    public record CohortRequest(String brief, int count, boolean regenerate) {
    }

    @Operation(summary = "Synthesize a diverse persona cohort from an audience brief (drafts, not saved)")
    @PostMapping
    @Secured({ "ROLE_ADMIN", "AI_AGENT_CREATE", "AI_AGENT_EDIT" })
    public CohortResult synthesize(@RequestBody CohortRequest request) {
        return cohortService.synthesize(request.brief(), request.count(), request.regenerate());
    }
}
