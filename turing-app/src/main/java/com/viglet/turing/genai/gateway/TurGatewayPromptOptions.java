/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.gateway;

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;

import com.viglet.turing.genai.TurPromptOverrides;
import com.viglet.turing.genai.gateway.TurOpenAiWire.ChatCompletionRequest;
import com.viglet.turing.genai.gateway.TurOpenAiWire.ResponseFormat;

/**
 * T1099 — reads what an OpenAI client set on the request ({@code temperature},
 * {@code top_p}, {@code max_tokens}, {@code stop}, {@code response_format}) into
 * {@link TurPromptOverrides}, which applies it over the instance's own options
 * the same way the native chat endpoint does (T1102).
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
final class TurGatewayPromptOptions {

    private TurGatewayPromptOptions() {
    }

    /** {@code prompt} carrying the request's options, or untouched when it set none. */
    static Prompt apply(Prompt prompt, ChatModel chatModel, ChatCompletionRequest request) {
        return overrides(request).applyTo(prompt, chatModel, request.model(), TurGatewayResponseFormat.PARAM);
    }

    static TurPromptOverrides overrides(ChatCompletionRequest request) {
        ResponseFormat format = request.response_format();
        TurOpenAiWire.JsonSchemaSpec schema = format == null ? null : format.json_schema();
        return new TurPromptOverrides(request.temperature(), request.top_p(), request.max_tokens(),
                request.stop(), format == null ? null : format.type(),
                schema == null ? null : schema.schema(), schema == null ? null : schema.strict());
    }
}
