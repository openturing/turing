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

import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.annotation.Secured;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.viglet.turing.genai.persona.fit.TurPersonaSuggestionReport;
import com.viglet.turing.genai.persona.fit.TurPersonaSuggestionService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Auto-suggest endpoint (Block AA / §XXVI.8): given a piece of content, rank
 * which audience persona it best fits by batching the content-fit evaluator.
 * Feeds Block Z A/B persona selection and editorial content routing.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@RestController
@RequestMapping("/api/persona/suggest")
@Tag(name = "AI Persona Suggestion", description = "Best-fit audience persona for content")
public class TurPersonaSuggestAPI {

    private final TurPersonaSuggestionService suggestionService;

    public TurPersonaSuggestAPI(TurPersonaSuggestionService suggestionService) {
        this.suggestionService = suggestionService;
    }

    @Operation(summary = "Rank which audience persona a piece of content best fits")
    @PostMapping
    @Secured({ "ROLE_ADMIN", "AI_AGENT_VIEW", "AI_AGENT_EDIT" })
    public TurPersonaSuggestionReport suggest(@RequestBody TurPersonaSuggestRequest request,
            @RequestParam(defaultValue = "false") boolean regenerate) {
        if (request == null || StringUtils.isBlank(request.content())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "content is required");
        }
        return suggestionService.suggest(request.content(), request.personaIds(), regenerate);
    }

    /**
     * Request body: the content to place plus an optional explicit subset of
     * audience persona ids (when omitted, every enabled audience persona is
     * considered).
     */
    public record TurPersonaSuggestRequest(String content, List<String> personaIds) {
    }
}
