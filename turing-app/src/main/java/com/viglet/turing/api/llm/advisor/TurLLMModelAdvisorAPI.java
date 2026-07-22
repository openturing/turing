/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.api.llm.advisor;

import java.util.List;

import org.springframework.security.access.annotation.Secured;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.viglet.turing.service.llm.advisor.TurLLMModelAdvisorService;
import com.viglet.turing.service.llm.advisor.TurLLMModelAdvisorService.AdvisorQuery;
import com.viglet.turing.service.llm.advisor.TurLLMModelAdvisorService.Recommendation;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * T785 / §LIII.3 (Block BE) — the "find me a model" advisor endpoint: takes a
 * constraint set (required capabilities/modalities, minimum context, budget
 * ceiling, minimum tier/intelligence) and returns a ranked catalog shortlist the
 * admin can one-click into a new LLM instance (via the existing instance-create
 * API). Read-only over catalog metadata.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@RestController
@RequestMapping("/api/v2/llm/model-advisor")
@Tag(name = "LLM Model Advisor", description = "Block BE — constraint-driven model recommender")
public class TurLLMModelAdvisorAPI {

    private final TurLLMModelAdvisorService advisorService;

    public TurLLMModelAdvisorAPI(TurLLMModelAdvisorService advisorService) {
        this.advisorService = advisorService;
    }

    @Secured({"ROLE_ADMIN", "LLM_VIEW", "LLM_CREATE", "LLM_EDIT"})
    @Operation(summary = "Rank catalog models that satisfy the given constraints")
    @PostMapping("/recommend")
    public List<Recommendation> recommend(@RequestBody AdvisorQuery query) {
        return advisorService.recommend(query);
    }
}
