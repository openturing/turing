/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.authoring.intent;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * LLM-shape of a {@code TurIntent} used by the AI Authoring chat. Decoupled
 * from the JPA entity so the LLM can't accidentally write to id, agentId,
 * sortOrder, or any field that should be controlled server-side.
 *
 * @author Alexandre Oliveira
 * @since 2026.2.5
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record IntentGeneration(
        String title,
        String description,
        String icon,
        Integer enabled,
        List<IntentActionGeneration> actions) {
}
