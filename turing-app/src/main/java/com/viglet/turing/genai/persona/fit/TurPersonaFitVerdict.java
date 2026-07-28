/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.persona.fit;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

/**
 * T829 / §LXI.5 (Block BN) — the content-fit verdict <em>as a type</em>, so the
 * shape can be handed to a provider as a JSON Schema instead of being described in
 * prose inside the prompt.
 *
 * <p>It replaces nothing at runtime: the same object was already being decoded from
 * the reply. What changes is that {@code BeanOutputConverter} can now derive a
 * schema from it, which OpenAI ({@code json_schema}) and Anthropic (forced tool)
 * enforce — and the property descriptions below travel <em>with</em> the schema, so
 * the model gets the same guidance the prompt used to carry.
 *
 * <p>Public and top-level because schema generation reflects over it.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@JsonClassDescription("How well a document suits one specific reader (audience persona).")
@JsonIgnoreProperties(ignoreUnknown = true)
public record TurPersonaFitVerdict(

        @JsonPropertyDescription("How well the document suits THIS reader, 0-100 "
                + "(100 = perfect fit, 0 = wholly inappropriate).")
        Integer fitScore,

        @JsonPropertyDescription("One sentence summarising the verdict, in the "
                + "audience's primary language when one is set.")
        String summary,

        @JsonPropertyDescription("Short bullets naming what suits this reader.")
        List<String> fits,

        @JsonPropertyDescription("Passages that do NOT suit this reader. Every span "
                + "must be copied verbatim from the document; omit anything you "
                + "cannot ground in the text.")
        List<Misfit> misfits) {

    /** One flagged passage plus why it misses and how to rewrite it. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Misfit(

            @JsonPropertyDescription("The offending phrase, copied verbatim from the document.")
            String span,

            @JsonPropertyDescription("One of: too-complex, jargon, tone, missing-context.")
            String reason,

            @JsonPropertyDescription("A simpler rewrite of that phrase.")
            String suggestion) {
    }
}
