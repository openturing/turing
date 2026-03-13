package com.viglet.turing.genai.tool;

import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.metadata.ToolMetadata;
import org.springframework.ai.chat.model.ToolContext;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class TurLoggingToolCallback implements ToolCallback {

    private final ToolCallback delegate;

    public TurLoggingToolCallback(ToolCallback delegate) {
        this.delegate = delegate;
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
        try {
            String result = delegate.call(toolInput, toolContext);
            long elapsed = System.currentTimeMillis() - start;
            log.info("[ToolCall] Tool '{}' completed in {}ms, response length: {} chars",
                    toolName, elapsed, result != null ? result.length() : 0);
            log.debug("[ToolCall] Tool '{}' response: {}", toolName,
                    result != null && result.length() > 500 ? result.substring(0, 500) + "..." : result);
            return result;
        } catch (Exception e) {
            long elapsed = System.currentTimeMillis() - start;
            log.error("[ToolCall] Tool '{}' failed after {}ms: {}", toolName, elapsed, e.getMessage());
            throw e;
        }
    }

    public static ToolCallback[] wrap(ToolCallback[] callbacks) {
        ToolCallback[] wrapped = new ToolCallback[callbacks.length];
        for (int i = 0; i < callbacks.length; i++) {
            wrapped[i] = new TurLoggingToolCallback(callbacks[i]);
        }
        return wrapped;
    }
}
