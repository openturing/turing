/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai;

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.model.tool.ToolCallingChatOptions;

/**
 * Seeds a per-turn {@link ChatOptions} builder from a {@link ChatModel}'s own
 * concrete options instance (e.g. {@code OpenAiChatOptions}).
 *
 * <h2>Why this exists</h2>
 *
 * <p>Spring AI 2.0.0 (final) changed how {@code OpenAiChatModel} handles the
 * prompt's runtime options. When the prompt already carries options,
 * {@code buildRequestPrompt} passes them through untouched and
 * {@code createRequest} then does a <em>hard cast</em>
 * {@code (OpenAiChatOptions) prompt.getOptions()}. Handing the model a generic
 * {@code DefaultToolCallingChatOptions} therefore throws:
 *
 * <pre>
 *   ClassCastException: DefaultToolCallingChatOptions cannot be cast to OpenAiChatOptions
 * </pre>
 *
 * <p>Earlier milestones (M2–M8/RC1) used {@code ModelOptionsUtils.copyToTarget}
 * to coerce the options, which is why building a bare
 * {@code DefaultToolCallingChatOptions} used to work. It no longer does.
 *
 * <p>The fix is to start from the model's <em>own</em> options via
 * {@link ToolCallingChatOptions#mutate()} — which preserves the provider-specific
 * runtime type (and the model / temperature / topP it already carries) — and
 * then layer the per-turn tool callbacks, tool context, and any token ceiling on
 * top. Provider-agnostic: every provider's options class implements
 * {@link ToolCallingChatOptions}.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
public final class TurChatToolOptions {

    private TurChatToolOptions() {
    }

    /**
     * Returns a {@link ToolCallingChatOptions.Builder} seeded from
     * {@code chatModel}'s concrete options, so the built {@link ChatOptions}
     * keeps the provider-specific runtime type the model hard-casts to.
     * Callers layer their per-turn settings (tool callbacks, tool context,
     * {@code maxTokens}, …) onto the returned builder before calling
     * {@code build()}.
     *
     * <p>Falls back to a generic {@link ToolCallingChatOptions#builder()} when
     * the model exposes no tool-calling options (or none at all) — e.g. a bare
     * mock in a unit test — so tool wiring still flows.
     */
    // S1452: the wildcard is intentional. ToolCallingChatOptions.Builder is a
    // self-referential generic type, and each provider returns its own concrete
    // builder subtype, so no single concrete type fits the signature and a raw
    // type would be worse. Callers only chain builder methods and then build.
    @SuppressWarnings("java:S1452")
    public static ToolCallingChatOptions.Builder<?> builderFrom(ChatModel chatModel) {
        ChatOptions base = chatModel == null ? null : chatModel.getOptions();
        if (base instanceof ToolCallingChatOptions toolOptions) {
            return toolOptions.mutate();
        }
        return ToolCallingChatOptions.builder();
    }
}
