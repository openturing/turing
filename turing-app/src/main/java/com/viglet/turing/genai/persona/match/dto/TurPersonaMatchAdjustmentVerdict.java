/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.persona.match.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

/**
 * T829 / §LXI.5 (Block BN) — the raw adjustment verdict <em>as a type</em>, so the
 * provider can enforce its shape (OpenAI {@code json_schema} / Anthropic forced
 * tool) instead of the shape living only as prose in the prompt.
 *
 * <p>Distinct from {@link TurPersonaMatchAdjustmentDto}: this is what the
 * <em>model</em> returns, before grounding. Every id is still verified against the
 * project and anything invented is dropped — a schema constrains the shape, never
 * the truthfulness.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@JsonClassDescription("Suggested edits on both sides of a content/audience portfolio.")
@JsonIgnoreProperties(ignoreUnknown = true)
public record TurPersonaMatchAdjustmentVerdict(

        @JsonPropertyDescription("The suggested adjustments, lowest-scoring and "
                + "sharpest-regressing targets first.")
        List<Item> items) {

    /** One suggested adjustment against one content or one persona. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Item(

            @JsonPropertyDescription("Either CONTENT (edit the document) or PERSONA "
                    + "(correct the audience description).")
            String target,

            @JsonPropertyDescription("The id of that content or persona, copied "
                    + "verbatim from the input. Never invent one.")
            String targetId,

            @JsonPropertyDescription("One sentence justifying the suggestion, citing "
                    + "the score or the delta.")
            String rationale,

            @JsonPropertyDescription("Concrete edits someone can carry out, at most "
                    + "three. Never generic advice like 'improve the text'.")
            List<String> actions) {
    }
}
