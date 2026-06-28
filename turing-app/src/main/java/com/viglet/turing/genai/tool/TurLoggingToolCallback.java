package com.viglet.turing.genai.tool;

import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.metadata.ToolMetadata;
import org.springframework.ai.chat.model.ToolContext;

import com.viglet.turing.service.chatanalytics.TurChatAnalyticsService;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class TurLoggingToolCallback implements ToolCallback {

    private final ToolCallback delegate;
    private final TurChatAnalyticsService analyticsService;

    public TurLoggingToolCallback(ToolCallback delegate) {
        this(delegate, null);
    }

    public TurLoggingToolCallback(ToolCallback delegate, TurChatAnalyticsService analyticsService) {
        this.delegate = delegate;
        this.analyticsService = analyticsService;
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
        String toolName = delegate.getToolDefinition().name();
        log.info("[ToolCall] Invoking tool '{}' with input: {}", toolName, toolInput);
        long start = System.currentTimeMillis();
        try {
            String result = delegate.call(toolInput);
            long elapsed = System.currentTimeMillis() - start;
            log.info("[ToolCall] Tool '{}' completed in {}ms, response length: {} chars",
                    toolName, elapsed, result != null ? result.length() : 0);
            log.debug("[ToolCall] Tool '{}' response: {}", toolName,
                    result != null && result.length() > 500 ? result.substring(0, 500) + "..." : result);
            // No ToolContext on this overload — analytics needs the conversationId,
            // so we can't record here. Spring AI calls the ToolContext overload when
            // tools are invoked through a real chat session anyway.
            return result;
        } catch (Exception e) {
            long elapsed = System.currentTimeMillis() - start;
            log.error("[ToolCall] Tool '{}' failed after {}ms: {}", toolName, elapsed, e.getMessage());
            throw e;
        }
    }

    @Override
    public String call(String toolInput, ToolContext toolContext) {
        String toolName = delegate.getToolDefinition().name();
        log.info("[ToolCall] Invoking tool '{}' with input: {}", toolName, toolInput);
        long start = System.currentTimeMillis();
        boolean success = false;
        // T427 / T436 — open the tool-call lifecycle: a `start` event (live, if
        // the agent opted in) carrying the redacted arg digest, then an `end`
        // event + trace row on completion. callId is unique per invocation.
        String callId = java.util.UUID.randomUUID().toString();
        String argsSummary = TurToolArgsSummary.summarize(toolInput);
        TurToolCallCollector toolCalls = toolCallCollector(toolContext);
        if (toolCalls != null) {
            toolCalls.onStart(callId, toolName, argsSummary);
        }
        try {
            String result = delegate.call(toolInput, toolContext);
            long elapsed = System.currentTimeMillis() - start;
            success = true;
            log.info("[ToolCall] Tool '{}' completed in {}ms, response length: {} chars",
                    toolName, elapsed, result != null ? result.length() : 0);
            log.debug("[ToolCall] Tool '{}' response: {}", toolName,
                    result != null && result.length() > 500 ? result.substring(0, 500) + "..." : result);
            return result;
        } catch (Exception e) {
            long elapsed = System.currentTimeMillis() - start;
            log.error("[ToolCall] Tool '{}' failed after {}ms: {}", toolName, elapsed, e.getMessage());
            throw e;
        } finally {
            long elapsed = System.currentTimeMillis() - start;
            recordAnalytics(toolContext, toolName, elapsed, success);
            if (toolCalls != null) {
                toolCalls.onEnd(callId, toolName, argsSummary, success, elapsed);
            }
        }
    }

    private static TurToolCallCollector toolCallCollector(ToolContext toolContext) {
        if (toolContext == null || toolContext.getContext() == null) {
            return null;
        }
        Object raw = toolContext.getContext().get(TurCustomToolCallbackService.TOOL_CONTEXT_TOOL_CALLS);
        return raw instanceof TurToolCallCollector collector ? collector : null;
    }

    private void recordAnalytics(ToolContext toolContext, String toolName, long elapsedMs, boolean success) {
        if (analyticsService == null || toolContext == null) return;
        Object cid = toolContext.getContext().get(TurCustomToolCallbackService.TOOL_CONTEXT_CONVERSATION_ID);
        if (cid instanceof String s && !s.isBlank()) {
            try {
                analyticsService.recordToolCall(s, toolName, elapsedMs, success);
            } catch (RuntimeException ex) {
                log.debug("[ToolCall] analytics record failed for tool '{}': {}", toolName, ex.getMessage());
            }
        }
    }

    public static ToolCallback[] wrap(ToolCallback[] callbacks) {
        return wrap(callbacks, null);
    }

    public static ToolCallback[] wrap(ToolCallback[] callbacks, TurChatAnalyticsService analyticsService) {
        ToolCallback[] wrapped = new ToolCallback[callbacks.length];
        for (int i = 0; i < callbacks.length; i++) {
            wrapped[i] = new TurLoggingToolCallback(callbacks[i], analyticsService);
        }
        return wrapped;
    }
}
