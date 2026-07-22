/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.api.llm.cost;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.viglet.turing.service.llm.rightsizing.TurLLMRightsizingService;
import com.viglet.turing.service.llm.rightsizing.TurLLMRightsizingService.RightsizingSuggestion;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * T786 / §LIII.3 (Block BE) — right-sizing / savings suggestions: from each
 * agent's observed usage over the window, suggests a cheaper same-vendor catalog
 * model that still fits, with projected monthly savings. Read-only.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@RestController
@RequestMapping("/api/v2/llm/cost")
@Tag(name = "LLM Right-sizing", description = "Block BE — cheaper-model savings suggestions")
public class TurLLMRightsizingAPI {

    private final TurLLMRightsizingService rightsizingService;

    public TurLLMRightsizingAPI(TurLLMRightsizingService rightsizingService) {
        this.rightsizingService = rightsizingService;
    }

    @Operation(summary = "Suggest cheaper models per agent from observed usage, with projected savings")
    @GetMapping("/rightsizing")
    public List<RightsizingSuggestion> rightsizing(
            @RequestParam(name = "windowDays", required = false) Integer windowDays) {
        return rightsizingService.suggest(windowDays);
    }
}
