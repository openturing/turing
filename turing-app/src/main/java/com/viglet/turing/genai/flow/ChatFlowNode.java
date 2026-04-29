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
 * A single step in a {@link ChatFlowGraph}. Mirrors the React Flow node
 * shape: identity + position carried for the editor, and the {@code data}
 * sub-object holding the runtime fields the engine consumes.
 *
 * @author Alexandre Oliveira
 * @since 2026.2.5
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ChatFlowNode(String id, String type, NodeData data) {

    public ChatFlowNode {
        data = data == null ? new NodeData(null, null, null, null, null, null, null, null, null) : data;
    }

    public String label() {
        return data.label();
    }

    public String aiInstruction() {
        return data.aiInstruction();
    }

    public String outputVariable() {
        return data.outputVariable();
    }

    public String validationRule() {
        return data.validationRule();
    }

    public String functionName() {
        return data.functionName();
    }

    public String conditionExpression() {
        return data.conditionExpression();
    }

    public String toolSource() {
        return data.toolSource();
    }

    public String mcpServerId() {
        return data.mcpServerId();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record NodeData(
            String label,
            String type,
            String aiInstruction,
            String outputVariable,
            String validationRule,
            String functionName,
            String conditionExpression,
            String toolSource,
            String mcpServerId) {
    }
}
