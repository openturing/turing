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
 * LLM-facing flat shape of a chat-flow node. Combines what the editor
 * stores as {@code ChatFlowNode.NodeData} into a single record so the
 * model has a simpler schema to fill — the skill copies the relevant
 * fields back into nested {@code data} when serializing.
 *
 * <p>{@code id} is a stable, semantic identifier (e.g. {@code "ask-name"},
 * {@code "validate-email"}, {@code "decide-interested"}) chosen by the
 * LLM — it is preserved across turns so edges keep referring to the
 * same node when the user asks for incremental changes.
 *
 * @author Alexandre Oliveira
 * @since 2026.2.5
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ChatFlowNodeGeneration(
        /** Stable, semantic id (e.g. "ask-name"). */
        String id,
        /** {@code "start" | "end" | "aiQuestion" | "condition" | "functionCall"}. */
        String type,
        /** Short uppercase title shown on the canvas card. */
        String label,

        // ---- aiQuestion / functionCall ----
        /** Prompt sent to the LLM when the node runs. */
        String aiInstruction,

        // ---- aiQuestion only ----
        /** Variable name where the captured answer is stored (e.g. {@code "email"}). */
        String outputVariable,
        /** Validation rule for the captured answer.
         *  One of: {@code "email" | "phone" | "url" | "number" | "date"}, or null/{@code "none"}. */
        String validationRule,

        // ---- condition only ----
        /** Boolean expression evaluated to pick the "yes" / "no" branch. */
        String conditionExpression,

        // ---- functionCall only ----
        /** {@code "NATIVE" | "MCP"} — which side the function lives on. */
        String toolSource,
        /** Name of the native @Tool method (when toolSource = NATIVE). */
        String functionName,
        /** Id of the MCP server to call (when toolSource = MCP). */
        String mcpServerId) {
}
