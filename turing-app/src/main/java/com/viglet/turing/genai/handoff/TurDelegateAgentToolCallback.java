/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.handoff;

import java.util.Map;

import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.DefaultToolDefinition;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.metadata.DefaultToolMetadata;
import org.springframework.ai.tool.metadata.ToolMetadata;

import com.viglet.turing.genai.TurAgentChatExecutor;
import com.viglet.turing.genai.tool.TurCustomToolAgentHelper;
import com.viglet.turing.genai.tool.TurCustomToolCallbackService;
import com.viglet.turing.persistence.repository.agent.TurAIAgentRepository;
import com.viglet.turing.persistence.repository.llm.TurLLMInstanceRepository;

import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * T448 / §XXIII.7 — the router-facing {@code delegate_to_agent} tool: hands a
 * sub-task to one of the router's allowlisted SPECIALIST agents and returns its
 * answer. The handoff is visible to the UI because this is a normal decorated
 * tool, so the T436 tool-call events fire for it automatically.
 *
 * <p>The specialist runs one-shot via {@link TurCustomToolAgentHelper} (same
 * cross-agent delegation the Custom Tool {@code agent.invoke} uses), so it gets
 * the shared recursion-depth cap for free — A→B→A can never loop forever. Only
 * agents on the router's allowlist are reachable (resolved by id or, for model
 * convenience, by case-insensitive title). Errors are returned as text, never
 * thrown, so the router LLM can recover.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Slf4j
public class TurDelegateAgentToolCallback implements ToolCallback {

    public static final String TOOL_NAME = "delegate_to_agent";

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private static final String INPUT_SCHEMA = """
            {
              "type": "object",
              "properties": {
                "agent": {
                  "type": "string",
                  "description": "The specialist agent to delegate to, exactly as listed in the available specialists."
                },
                "task": {
                  "type": "string",
                  "description": "A clear, self-contained description of the sub-task for the specialist."
                }
              },
              "required": ["agent", "task"]
            }""";

    private static final String DEFAULT_DESCRIPTION = """
            Delegate a focused sub-task to a specialist agent (a colleague with its \
            own expertise, tools and skills) and use its answer. Call this when the \
            task is squarely in another specialist's domain. Pass the specialist's \
            `agent` name exactly as listed and a clear, self-contained `task`. The \
            specialist runs autonomously and returns its result.""";

    /** Lowercased id AND title → canonical specialist agent id. */
    private final Map<String, String> allowedByKey;
    private final TurAgentChatExecutor executor;
    private final TurAIAgentRepository agentRepository;
    private final TurLLMInstanceRepository llmRepository;
    private final ToolDefinition definition;

    public TurDelegateAgentToolCallback(Map<String, String> allowedByKey,
            TurAgentChatExecutor executor, TurAIAgentRepository agentRepository,
            TurLLMInstanceRepository llmRepository) {
        this.allowedByKey = allowedByKey;
        this.executor = executor;
        this.agentRepository = agentRepository;
        this.llmRepository = llmRepository;
        this.definition = new DefaultToolDefinition(TOOL_NAME, DEFAULT_DESCRIPTION, INPUT_SCHEMA);
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
        String requested = arg(toolInput, "agent");
        String task = arg(toolInput, "task");
        if (requested == null || requested.isBlank()) {
            return "delegate_to_agent requires a non-blank 'agent' argument.";
        }
        if (task == null || task.isBlank()) {
            return "delegate_to_agent requires a non-blank 'task' argument.";
        }
        String specialistId = allowedByKey.get(requested.trim().toLowerCase());
        if (specialistId == null) {
            return "No specialist named '" + requested + "' is available. Available specialists: "
                    + String.join(", ", distinctNames()) + ".";
        }
        int parentDepth = depth(toolContext);
        String conversationId = contextValue(toolContext,
                TurCustomToolCallbackService.TOOL_CONTEXT_CONVERSATION_ID);
        log.info("[delegate_to_agent] routing to specialist={} conv={} depth={}",
                specialistId, conversationId, parentDepth);
        // parentDepth + 1: identical increment to the Custom Tool agent.invoke path,
        // so the shared depth cap bounds A→B→A recursion.
        TurCustomToolAgentHelper helper = new TurCustomToolAgentHelper(
                executor, agentRepository, llmRepository, conversationId, parentDepth + 1);
        return helper.invoke(specialistId, task);
    }

    private java.util.List<String> distinctNames() {
        return allowedByKey.values().stream().distinct().sorted().toList();
    }

    private static String arg(String toolInput, String field) {
        if (toolInput == null || toolInput.isBlank()) {
            return null;
        }
        try {
            JsonNode node = OBJECT_MAPPER.readTree(toolInput).get(field);
            return node == null || node.isNull() ? null : node.asString();
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static int depth(ToolContext toolContext) {
        Object value = toolContext == null ? null
                : toolContext.getContext().get(TurCustomToolCallbackService.TOOL_CONTEXT_AGENT_INVOKE_DEPTH);
        if (value instanceof Number n) {
            return Math.max(0, n.intValue());
        }
        return 0;
    }

    private static String contextValue(ToolContext toolContext, String key) {
        Object value = toolContext == null ? null : toolContext.getContext().get(key);
        return value == null ? null : value.toString();
    }
}
