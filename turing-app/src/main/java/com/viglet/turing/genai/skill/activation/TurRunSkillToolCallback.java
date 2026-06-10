/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.skill.activation;

import java.util.List;
import java.util.Map;

import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.DefaultToolDefinition;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.metadata.DefaultToolMetadata;
import org.springframework.ai.tool.metadata.ToolMetadata;

import com.viglet.turing.genai.tool.TurCustomToolCallbackService;
import com.viglet.turing.persistence.model.skill.TurSkill;

import lombok.extern.slf4j.Slf4j;

/**
 * T323 / §IX.4.d — the parent-facing {@code run_skill} delegation tool.
 *
 * <p>This is the only skill tool the chat/SN turn (driven by the agent's own
 * model) ever sees. The progressive-disclosure block lists each available skill
 * cheaply (name + description); when the model judges a skill relevant it calls
 * this tool with that skill {@code name} and a {@code task}. The call routes to
 * {@link TurSkillRunnerService#runSkill} which operates the skill autonomously on
 * the Global Settings <em>Default LLM</em> (a separate sub-loop with the skill's
 * {@code SKILL.md} and its sandbox) and returns the final result here, as the
 * tool output.
 *
 * <p>The agent / conversation ids are read from the Spring AI {@link ToolContext}
 * — the same keys the Code Interpreter and {@code skill_bash} use — so the
 * delegated sub-loop drives the conversation's own sandbox session. Constrained
 * to the offered skill set; errors are returned as plain strings (never thrown)
 * so the model can recover rather than aborting the turn.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
@Slf4j
public class TurRunSkillToolCallback implements ToolCallback {

    public static final String TOOL_NAME = "run_skill";

    private static final String INPUT_SCHEMA = """
            {
              "type": "object",
              "properties": {
                "skill": {
                  "type": "string",
                  "description": "The name of the skill to use, exactly as listed in the available skills."
                },
                "task": {
                  "type": "string",
                  "description": "A clear, self-contained description of what you need the skill to do for the user."
                }
              },
              "required": ["skill", "task"]
            }""";

    private static final String DEFAULT_DESCRIPTION = """
            Use a skill to accomplish a task. Call this when one of the available \
            skills, judged by its short description, matches the user's need. Pass \
            the skill `name` exactly as listed and a clear, self-contained `task`. \
            The skill runs autonomously in its own sandboxed container (operated by \
            a dedicated model) and returns the result, which you should use to \
            answer the user.""";

    private final transient Map<String, TurSkill> index;
    private final transient TurSkillRunnerService runnerService;
    private final ToolDefinition definition;

    public TurRunSkillToolCallback(List<TurSkill> offeredSkills, TurSkillRunnerService runnerService) {
        this.index = TurSkillActivationSupport.index(offeredSkills);
        this.runnerService = runnerService;
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
        String name = TurSkillActivationSupport.stringArg(toolInput, "skill", false);
        if (name == null) {
            return "run_skill requires a non-blank 'skill' name argument.";
        }
        String task = TurSkillActivationSupport.stringArg(toolInput, "task", false);
        TurSkill skill = TurSkillActivationSupport.resolve(index, name);
        if (skill == null) {
            return "No skill named '" + name + "' is available. Available skills: "
                    + String.join(", ", availableNames()) + ".";
        }
        String agentId = TurSkillActivationSupport.contextValue(
                toolContext, TurCustomToolCallbackService.TOOL_CONTEXT_AGENT_ID);
        String conversationId = TurSkillActivationSupport.contextValue(
                toolContext, TurCustomToolCallbackService.TOOL_CONTEXT_CONVERSATION_ID);
        log.info("[run_skill] delegating skill='{}' conv={} to the Default LLM", skill.getName(), conversationId);
        return runnerService.runSkill(skill, task, agentId, conversationId);
    }

    private List<String> availableNames() {
        return index.values().stream().map(TurSkill::getName).distinct().sorted().toList();
    }
}
