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

import java.util.List;

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
        data = data == null
                ? new NodeData(null, null, null, null, null, null, null, null, null, null, null, null, null, null, null)
                : data;
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

    public String subFlowId() {
        return data.subFlowId();
    }

    public String subFlowName() {
        return data.subFlowName();
    }

    /**
     * Persona id this node should switch the conversation's active voice to.
     * Only meaningful when the node's type is {@code persona}; ignored otherwise.
     *
     * @since 2026.2.7
     */
    public String personaId() {
        return data.personaId();
    }

    /**
     * Variable name read from the flow's collected variables to drive a {@code switch} node's
     * multi-way branch. Typically the {@code outputVariable} of an upstream AI Question so the
     * switch routes on what the user just said.
     *
     * @since 2026.2.7
     */
    public String switchVariable() {
        return data.switchVariable();
    }

    /**
     * Branches exposed by a {@code switch} node. Each entry's {@code id} matches an outgoing
     * edge's {@code sourceHandle}; the {@code label} is the human-readable text the engine
     * matches against the resolved switch variable.
     *
     * @since 2026.2.7
     */
    public List<SwitchOption> switchOptions() {
        return data.switchOptions() == null ? List.of() : data.switchOptions();
    }

    /**
     * Optional list of suggested answers for an {@code aiQuestion} node — surfaces as
     * clickable chips in the chat UI so the user can pick instead of typing. Unlike
     * {@link #switchOptions()}, picking one of these does <em>not</em> branch the flow —
     * the chosen label is sent as the user message and captured into {@code outputVariable}
     * exactly as if typed. Free-text input remains available in parallel.
     *
     * @since 2026.2.7
     */
    public List<String> inlineOptions() {
        return data.inlineOptions() == null ? List.of() : data.inlineOptions();
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
            String mcpServerId,
            String subFlowId,
            String subFlowName,
            String personaId,
            String switchVariable,
            List<SwitchOption> switchOptions,
            List<String> inlineOptions) {
    }

    /**
     * A single branch on a {@code switch} node. {@code id} is the edge's {@code sourceHandle};
     * {@code label} is the human-readable text the engine classifies against.
     *
     * @since 2026.2.7
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SwitchOption(String id, String label) {
    }
}
