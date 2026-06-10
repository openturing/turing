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

import java.util.Arrays;

import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.viglet.turing.genai.workspace.TurAgentWorkspace;
import com.viglet.turing.properties.TurConfigProperties;
import com.viglet.turing.properties.TurGenAiProperty.TurGenAiToolResultOffloadProperty;
import com.viglet.turing.service.chatanalytics.TurChatAnalyticsService;
import com.viglet.turing.service.storage.TurStorageService;

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
 *   <li><b>Auto-offload large results</b> (T114) — when offloading is
 *       enabled and a storage backend is configured, each callback is wrapped
 *       with {@link TurToolResultOffloadCallback} (a result &gt;
 *       {@code inlineMaxChars} is written to the agent workspace and replaced
 *       with a {@code workspace://} reference) and the always-present
 *       {@link TurWorkspaceReadToolCallback} ({@code workspace_read}) is
 *       appended so the LLM can pull the payload back when it needs it.</li>
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
    private final TurAgentWorkspace agentWorkspace;
    private final TurStorageService storageService;
    private final TurConfigProperties configProperties;

    public TurToolCallbackPipeline(TurToolDescriptionService toolDescriptionService,
            @Autowired(required = false) TurChatAnalyticsService analyticsService,
            TurAgentWorkspace agentWorkspace,
            TurStorageService storageService,
            TurConfigProperties configProperties) {
        this.toolDescriptionService = toolDescriptionService;
        this.analyticsService = analyticsService;
        this.agentWorkspace = agentWorkspace;
        this.storageService = storageService;
        this.configProperties = configProperties;
    }

    /**
     * Apply the standard decorator chain to a set of raw tool callbacks.
     * Order is intentional: description override runs first so the LOG
     * statements emitted by {@link TurLoggingToolCallback} reference the
     * (possibly already-overridden) tool name; description override is
     * a no-op for tools without a {@code .md} file.
     *
     * <p>When auto-offload (T114) is active, the raw callbacks are first
     * wrapped with {@link TurToolResultOffloadCallback} and the
     * {@code workspace_read} tool is appended, so the returned array may be
     * one element <em>longer</em> than {@code raw}; the description override
     * and logging then apply to every callback (including
     * {@code workspace_read}, which therefore gets its {@code .md} description
     * and is logged like any other tool).
     *
     * @param raw the un-decorated callbacks
     * @return a new array with the decorator chain applied
     */
    public ToolCallback[] decorate(ToolCallback... raw) {
        if (raw == null || raw.length == 0) {
            return new ToolCallback[0];
        }
        ToolCallback[] base = raw;
        if (isOffloadActive()) {
            int inlineMax = offloadProperty().getInlineMaxChars();
            // Offload-wrap the real tools, then append workspace_read so the
            // model can pull an offloaded payload back. workspace_read is
            // appended AFTER offload wrapping so its own (potentially large)
            // output is never re-offloaded — that would defeat the read.
            ToolCallback[] offloaded = TurToolResultOffloadCallback.wrap(raw, agentWorkspace, inlineMax);
            base = Arrays.copyOf(offloaded, offloaded.length + 1);
            base[offloaded.length] = new TurWorkspaceReadToolCallback(agentWorkspace);
        }
        ToolCallback[] withDescriptions = TurToolDescriptionCallback.wrap(base, toolDescriptionService);
        return TurLoggingToolCallback.wrap(withDescriptions, analyticsService);
    }

    /**
     * Offloading is only active when the feature is enabled <em>and</em> a
     * storage backend is configured ({@code turing.storage.type != none}).
     * Without storage there is nowhere to put the payload, so results stay
     * inline and {@code workspace_read} is not added — keeping the default
     * {@code none} deployment unchanged.
     */
    private boolean isOffloadActive() {
        return offloadProperty().isEnabled() && storageService.isEnabled();
    }

    private TurGenAiToolResultOffloadProperty offloadProperty() {
        return configProperties.getGenai().getToolResultOffload();
    }
}
