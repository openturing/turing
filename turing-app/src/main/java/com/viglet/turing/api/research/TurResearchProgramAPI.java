/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.api.research;

import java.util.List;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.viglet.turing.genai.research.TurResearchProgramService;
import com.viglet.turing.genai.research.dto.TurResearchProgramRollupDto;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Multi-study program rollup endpoint (Block AW / §XLVI.5, T733 — PRISMA). Takes a
 * set of study ids and returns a deterministic program-level rollup (totals,
 * per-study sufficiency, cross-study aggregated themes). Secured by the global
 * {@code authenticated()} rule like the sibling research API.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@RestController
@RequestMapping("/api/research-study/program")
@Tag(name = "Research Program", description = "Multi-study planner & cross-study rollup")
public class TurResearchProgramAPI {

    private final TurResearchProgramService programService;

    public TurResearchProgramAPI(TurResearchProgramService programService) {
        this.programService = programService;
    }

    @Operation(summary = "Roll several studies up into a program-level view (totals + cross-study themes)")
    @PostMapping("/rollup")
    public TurResearchProgramRollupDto rollup(@RequestBody List<String> studyIds) {
        return programService.rollup(studyIds);
    }
}
