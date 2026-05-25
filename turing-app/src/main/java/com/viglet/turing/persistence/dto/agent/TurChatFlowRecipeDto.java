/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.persistence.dto.agent;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * T96 / §VII.11.f — full chat-flow recipe shipped in the Turing Recipes
 * marketplace. Combines the catalog metadata
 * ({@link TurChatFlowRecipeSummaryDto}'s fields) with the install bundle —
 * a list of {@link TurChatFlowImportDto} entries that reuses the existing
 * {@code /import-bundle} payload shape so adding a recipe never required
 * touching the import pipeline.
 *
 * <p>Recipes are loaded once from the classpath ({@code resources/flow-recipes/*.json})
 * at startup and cached in {@code TurChatFlowRecipeService}; the file shape
 * matches this DTO 1-to-1 so authoring a new recipe is "write a JSON".
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
@Getter
@Setter
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class TurChatFlowRecipeDto {

    /** Stable kebab-case id ({@code lead-capture-b2c}). Drives install audit trail + URLs. */
    private String id;

    /** Semantic version of this recipe (e.g. {@code 1.0.0}). */
    private String version;

    /** Human-readable title for the catalog card. */
    private String name;

    /** One-paragraph pitch. */
    private String description;

    /** Vertical tag — {@code education}, {@code finance}, {@code healthcare}, {@code general}. */
    private String vertical;

    /** Free-form keyword tags for in-catalog search. */
    private List<String> tags;

    /**
     * The install bundle. Reuses the existing
     * {@code POST /api/ai-agent/{agentId}/chat-flow/import-bundle} payload
     * shape — each item carries a flow plus the personas/slots it depends on.
     * Cross-item {@code subFlowId} references are auto-wired by the import
     * pipeline, so a recipe with a {@code lead-capture → b2b-quote} chain
     * just works.
     */
    private List<TurChatFlowImportDto> bundle;
}
