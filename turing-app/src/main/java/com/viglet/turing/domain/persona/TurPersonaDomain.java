/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.domain.persona;

import com.viglet.turing.persistence.model.persona.TurPersonaLanguageStyle;
import com.viglet.turing.persistence.model.persona.TurPersonaTone;

/**
 * Immutable, JPA-free projection of a Persona. Neighbouring aggregates
 * (few-shot store and brand-context MCP server) are projected to their IDs
 * — resolve them through the corresponding ports when the full aggregate
 * is needed.
 *
 * @author Alexandre Oliveira
 * @since 2026.2.6
 */
public record TurPersonaDomain(
        String id,
        String name,
        String description,
        String systemInstruction,
        TurPersonaTone tone,
        int verbosity,
        TurPersonaLanguageStyle languageStyle,
        String mandatoryTerms,
        String forbiddenTerms,
        int enabled,
        String fewShotStoreId,
        String brandContextMcpServerId) {

    /** True when the persona is enabled for runtime use (admin toggle). */
    public boolean isEnabled() {
        return enabled == 1;
    }
}
