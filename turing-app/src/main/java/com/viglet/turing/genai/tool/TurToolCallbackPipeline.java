/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.tool;

import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.viglet.turing.service.chatanalytics.TurChatAnalyticsService;

/**
 * Single consolidation point for the tool-callback decorator chain.
 * <p>
 * EVERY chat code path that hands tool callbacks to a {@code ChatModel}
 * MUST run them through {@link #decorate(ToolCallback...)}. This
 * guarantees uniform behavior across the platform:
 * <ol>
 *   <li><b>Description override</b> — replaces the {@code "."} placeholder
 *       on each {@code @Tool} annotation with the rich description loaded
 *       from {@code classpath:prompts/tools/&lt;group&gt;/&lt;tool&gt;.md}
 *       via {@link TurToolDescriptionService}. Without this the LLM sees
 *       only an empty description and misuses tools (e.g. passing bare
 *       words instead of the JSON Elasticsearch DSL the tool expects).</li>
 *   <li><b>Logging</b> — wraps each callback with
 *       {@link TurLoggingToolCallback} so every invocation is logged with
 *       its name, input, latency, and response length.</li>
 * </ol>
 *
 * <p>If you're adding a new place in the codebase that exposes
 * {@code ToolCallback[]} to a {@code ChatModel}, inject this service and
 * call {@code pipeline.decorate(rawCallbacks)} — do NOT call
 * {@code TurLoggingToolCallback.wrap} or
 * {@code TurToolDescriptionCallback.wrap} directly.
 *
 * @author Alexandre Oliveira
 * @since 2026.2.5
 */
@Service
public class TurToolCallbackPipeline {

    private final TurToolDescriptionService toolDescriptionService;
    private final TurChatAnalyticsService analyticsService;

    public TurToolCallbackPipeline(TurToolDescriptionService toolDescriptionService,
            @Autowired(required = false) TurChatAnalyticsService analyticsService) {
        this.toolDescriptionService = toolDescriptionService;
        this.analyticsService = analyticsService;
    }

    /**
     * Apply the standard decorator chain to a set of raw tool callbacks.
     * Order is intentional: description override runs first so the LOG
     * statements emitted by {@link TurLoggingToolCallback} reference the
     * (possibly already-overridden) tool name; description override is
     * a no-op for tools without a {@code .md} file.
     *
     * @param raw the un-decorated callbacks
     * @return a new array, same length, with both decorators applied
     */
    public ToolCallback[] decorate(ToolCallback... raw) {
        if (raw == null || raw.length == 0) {
            return new ToolCallback[0];
        }
        ToolCallback[] withDescriptions = TurToolDescriptionCallback.wrap(raw, toolDescriptionService);
        return TurLoggingToolCallback.wrap(withDescriptions, analyticsService);
    }
}
