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

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.metadata.ToolMetadata;

import com.viglet.turing.genai.workspace.TurAgentWorkspace;

import lombok.extern.slf4j.Slf4j;

/**
 * Auto-offload-large-tool-results decorator (T114, §IX.3.d).
 *
 * <p>Wraps a {@link ToolCallback} and intercepts its result. When the tool
 * returns more than {@code inlineMaxChars} characters <em>and</em> the call
 * carries an active conversation context (agent id + conversation id in the
 * {@link ToolContext}), the wrapper:
 * <ol>
 *   <li>writes the full payload to the per-conversation
 *       {@link TurAgentWorkspace} under
 *       {@code tool-results/{toolName}-{ts}.json}, and</li>
 *   <li>returns a short reference in its place —
 *       {@code Stored at workspace://{key} ({size} KB). Call workspace_read
 *       with key="{key}" to read it.}</li>
 * </ol>
 *
 * <p>The LLM sees only the reference; it pulls the data back through the
 * always-present {@code workspace_read} tool
 * ({@link TurWorkspaceReadToolCallback}) when (and only when) it actually
 * needs it. This keeps large catalogs / search dumps / JSON arrays out of the
 * prompt context while leaving an audit trail (the workspace write + T60 audit
 * log).
 *
 * <h2>When offloading does NOT happen (pass-through)</h2>
 * <ul>
 *   <li>Result is {@code null} or already within the inline budget.</li>
 *   <li>No {@link ToolContext} (the legacy one-arg {@code call(String)} path —
 *       offloading needs the conversation scope, so it can't apply there).</li>
 *   <li>The context lacks an agent id or conversation id (admin preview,
 *       authoring, intent, unit tests).</li>
 *   <li>The workspace write fails for any reason — the original result is
 *       returned so a storage hiccup never breaks a tool call.</li>
 * </ul>
 *
 * <p>The pipeline only wraps tools with this decorator when offloading is
 * enabled and a storage backend is configured (see
 * {@link TurToolCallbackPipeline}), so the default {@code turing.storage.type=none}
 * deployment is byte-for-byte unchanged.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
@Slf4j
public class TurToolResultOffloadCallback implements ToolCallback {

    /** Workspace folder every offloaded tool result lands under. */
    static final String OFFLOAD_PREFIX = "tool-results/";

    /** Disambiguates two offloads from the same tool within the same millisecond. */
    private static final AtomicLong SEQUENCE = new AtomicLong();

    private final ToolCallback delegate;
    private final TurAgentWorkspace workspace;
    private final int inlineMaxChars;

    public TurToolResultOffloadCallback(ToolCallback delegate, TurAgentWorkspace workspace, int inlineMaxChars) {
        this.delegate = delegate;
        this.workspace = workspace;
        this.inlineMaxChars = inlineMaxChars;
    }

    @Override
    public ToolDefinition getToolDefinition() {
        return delegate.getToolDefinition();
    }

    @Override
    public ToolMetadata getToolMetadata() {
        return delegate.getToolMetadata();
    }

    @Override
    public String call(String toolInput) {
        // No context → no conversation scope to offload into. Pass through.
        return delegate.call(toolInput);
    }

    @Override
    public String call(String toolInput, ToolContext toolContext) {
        String result = delegate.call(toolInput, toolContext);
        return maybeOffload(result, toolContext);
    }

    private String maybeOffload(String result, ToolContext toolContext) {
        if (result == null || result.length() <= inlineMaxChars) {
            return result;
        }
        String agentId = contextValue(toolContext, TurCustomToolCallbackService.TOOL_CONTEXT_AGENT_ID);
        String conversationId = contextValue(toolContext, TurCustomToolCallbackService.TOOL_CONTEXT_CONVERSATION_ID);
        if (agentId == null || conversationId == null) {
            // Outside an active chat session (authoring / intent / preview / tests).
            return result;
        }
        String toolName = delegate.getToolDefinition().name();
        String key = OFFLOAD_PREFIX + toolName + "-" + System.currentTimeMillis()
                + "-" + SEQUENCE.incrementAndGet() + ".json";
        try {
            byte[] payload = result.getBytes(StandardCharsets.UTF_8);
            workspace.put(agentId, conversationId, key, payload, "application/json");
            long sizeKb = Math.max(1, Math.round(payload.length / 1024.0));
            log.info("[ToolOffload] tool='{}' conv={} offloaded {} chars → workspace://{}",
                    toolName, conversationId, result.length(), key);
            return "Stored at workspace://" + key + " (" + sizeKb + " KB). "
                    + "Call workspace_read with key=\"" + key + "\" to read the full result when you need it.";
        } catch (RuntimeException e) {
            // Storage hiccup must never break the tool call — fall back to inline.
            log.warn("[ToolOffload] tool='{}' conv={} offload failed ({}); returning result inline",
                    toolName, conversationId, e.getMessage());
            return result;
        }
    }

    private static String contextValue(ToolContext toolContext, String key) {
        if (toolContext == null) {
            return null;
        }
        Map<String, Object> ctx = toolContext.getContext();
        if (ctx == null) {
            return null;
        }
        Object value = ctx.get(key);
        if (value == null) {
            return null;
        }
        String text = value.toString();
        return text.isBlank() ? null : text;
    }

    /**
     * Wraps every callback with the offload decorator. Same length, same
     * order, names/definitions preserved.
     */
    public static ToolCallback[] wrap(ToolCallback[] callbacks, TurAgentWorkspace workspace, int inlineMaxChars) {
        ToolCallback[] wrapped = new ToolCallback[callbacks.length];
        for (int i = 0; i < callbacks.length; i++) {
            wrapped[i] = new TurToolResultOffloadCallback(callbacks[i], workspace, inlineMaxChars);
        }
        return wrapped;
    }
}
