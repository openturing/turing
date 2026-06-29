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

/**
 * T438 / §XXII.3 — one frontend ("client") tool an agent declares. The model is
 * told the tool exists (name + description + JSON-Schema {@code parameters}) and
 * may "call" it; the server never executes it — instead the turn parks and a
 * {@code client_tool_call} SSE event asks the browser to run it (see
 * {@link TurClientToolCallback} and
 * {@link com.viglet.turing.genai.TurClientToolParkRegistry TurClientToolParkRegistry}).
 *
 * <p>Declared per-agent as a JSON array on {@code TurAIAgent.clientToolsJson}:
 * {@code [{"name":"get_user_location","description":"…","schema":{…}}]}. The
 * {@code schema} is a JSON-Schema object string advertised to the model as the
 * tool's input parameters.
 *
 * @param name        unique tool name the model invokes (and the browser handles)
 * @param description what the tool does (steers the model on when to call it)
 * @param schema      JSON-Schema (object) string for the tool's input parameters;
 *                    a sensible empty-object default is used when blank
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
public record TurClientTool(String name, String description, String schema) {

    /** Default parameter schema when a declaration omits one. */
    public static final String EMPTY_OBJECT_SCHEMA = "{\"type\":\"object\",\"properties\":{}}";

    public String effectiveSchema() {
        return schema == null || schema.isBlank() ? EMPTY_OBJECT_SCHEMA : schema;
    }

    public String effectiveDescription() {
        return description == null || description.isBlank()
                ? "Frontend tool '" + name + "' — executed by the client."
                : description;
    }
}
