/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.persistence.dto.agent;

/**
 * One tool exposed to the LLM for an AI Agent turn. Tool descriptions are
 * <em>not</em> part of the system-prompt string — Spring AI sends them as a
 * separate tool-schema channel in the chat request. The Live Preview lists
 * them in their own section so operators don't mistake them for prompt text.
 *
 * @param name        the tool name the LLM sees (sanitized custom-tool title,
 *                    native tool id, or MCP server title).
 * @param description what the LLM reads to decide when to call the tool;
 *                    for MCP servers this is a note (the real per-tool schemas
 *                    are discovered from the live server at runtime).
 * @param source      {@code CUSTOM}, {@code NATIVE}, or {@code MCP}.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
public record TurSystemPromptToolDto(
        String name,
        String description,
        String source) {
}
