/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.flow;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Directed edge between two {@link ChatFlowNode}s.
 *
 * @author Alexandre Oliveira
 * @since 2026.2.5
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ChatFlowEdge(
        String id,
        String source,
        String target,
        String sourceHandle,
        String targetHandle,
        String label) {
}
