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

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * LLM-facing edge between two chat-flow nodes. Maps directly to
 * {@code ChatFlowEdge} when serialized.
 *
 * <p>For condition nodes, the {@code sourceHandle} <strong>must</strong>
 * be {@code "yes"} or {@code "no"} so the engine knows which branch the
 * edge belongs to. For all other source node types, leave it null.
 *
 * @author Alexandre Oliveira
 * @since 2026.2.5
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ChatFlowEdgeGeneration(
        String source,
        String target,
        /** {@code "yes" | "no"} for condition branches; null otherwise. */
        String sourceHandle,
        /** Optional label shown on the edge. */
        String label) {
}
