/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.authoring.persona;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * LLM-shape of a {@code TurPersona} used by the AI Authoring chat. Decoupled
 * from the JPA entity so the LLM cannot accidentally write to id, the
 * fewShotStore relation, or the brandContextMcpServer relation — those stay
 * server-side. The LLM only authors the voice profile itself.
 *
 * <p>Tone values are constrained to the {@code TurPersonaTone} enum (FORMAL,
 * CASUAL, TECHNICAL, EXECUTIVE) and language style to
 * {@code TurPersonaLanguageStyle} (NEUTRAL, DIRECT, NARRATIVE, PERSUASIVE,
 * INSTRUCTIONAL) — the system prompt instructs the model to choose only from
 * those, and the persona controller validates them on save.
 *
 * <p>{@code mandatoryTerms} and {@code forbiddenTerms} are pipe
 * (<code>|</code>)-separated strings — same on-the-wire shape as the form.
 *
 * @author Alexandre Oliveira
 * @since 2026.2.7
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PersonaGeneration(
        String name,
        String description,
        String systemInstruction,
        String tone,
        Integer verbosity,
        String languageStyle,
        String mandatoryTerms,
        String forbiddenTerms,
        Integer enabled) {
}
