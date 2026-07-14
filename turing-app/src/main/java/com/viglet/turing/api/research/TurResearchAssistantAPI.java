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

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.viglet.turing.genai.research.TurResearchAssistantService;
import com.viglet.turing.genai.research.TurResearchAssistantService.ProposalResult;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Research Assistant endpoint (Block AW / §XLVI.5, T732): a free-text "what do you
 * want to learn?" in, a structured (never-applied) study proposal out, for the
 * Synthetic Research studio's guided create flow. Secured by the global
 * {@code authenticated()} rule like the sibling research API.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@RestController
@RequestMapping("/api/research-study/assistant")
@Tag(name = "Research Assistant", description = "Guided synthetic-research study authoring")
public class TurResearchAssistantAPI {

    private final TurResearchAssistantService assistantService;

    public TurResearchAssistantAPI(TurResearchAssistantService assistantService) {
        this.assistantService = assistantService;
    }

    /** Guided-authoring request: the researcher's free-text intent + regenerate flag. */
    public record AssistantRequest(String brief, boolean regenerate) {
    }

    @Operation(summary = "Propose a study configuration from a free-text research goal (not saved)")
    @PostMapping("/propose")
    public ProposalResult propose(@RequestBody AssistantRequest request) {
        return assistantService.propose(request.brief(), request.regenerate());
    }
}
