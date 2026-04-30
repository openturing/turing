/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.authoring.chatflow;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * LLM-facing shape of a {@code TurChatFlow} used by the AI Authoring chat.
 * <p>
 * Decoupled from the JPA entity so the LLM can't accidentally write to id,
 * agentId, or persistence-only fields. The {@code nodes} and {@code edges}
 * lists mirror the React Flow graph that the regular editor produces — the
 * skill serializes them into {@code definitionJson} before the entity is
 * saved.
 *
 * @author Alexandre Oliveira
 * @since 2026.2.5
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ChatFlowGeneration(
        String name,
        String description,
        /** {@code "HEURISTIC" | "LLM_JUDGE" | "STRUCTURED_OUTPUT"} */
        String guardrailMethod,
        String triggerDescription,
        /** {@code "ONCE" | "ALWAYS"} */
        String triggerMode,
        Integer enabled,
        List<ChatFlowNodeGeneration> nodes,
        List<ChatFlowEdgeGeneration> edges) {
}
