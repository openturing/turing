/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.clienttool;

import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.DefaultToolDefinition;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.metadata.DefaultToolMetadata;
import org.springframework.ai.tool.metadata.ToolMetadata;

/**
 * T438 — advertises one frontend ("client") tool to the model so it can decide
 * to call it, exactly like any other tool (name + description + parameter
 * schema). It is <b>never executed server-side</b>: the client-tool-aware tool
 * loop ({@code TurToolExecutionLoop.callWithClientTools}) detects the call by
 * name and parks the turn <em>before</em> invoking the callback. The
 * {@code call(...)} body is therefore a safety net — if it is ever reached, it
 * means the loop wasn't told this tool is a client tool, which is a bug.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
public class TurClientToolCallback implements ToolCallback {

    private final ToolDefinition definition;

    public TurClientToolCallback(TurClientTool tool) {
        this.definition = new DefaultToolDefinition(
                tool.name(), tool.effectiveDescription(), tool.effectiveSchema());
    }

    @Override
    public ToolDefinition getToolDefinition() {
        return definition;
    }

    @Override
    public ToolMetadata getToolMetadata() {
        return new DefaultToolMetadata(false);
    }

    @Override
    public String call(String toolInput) {
        return call(toolInput, null);
    }

    @Override
    public String call(String toolInput, ToolContext toolContext) {
        // Unreachable in normal operation — the loop parks on this tool's name
        // before executing. Returning a clear message (never throwing) keeps a
        // misconfiguration from aborting the turn.
        return "Client tool '" + definition.name()
                + "' must be executed by the browser, not the server.";
    }
}
