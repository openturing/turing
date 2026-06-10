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

import com.viglet.turing.genai.skill.sandbox.TurSkillSandboxResult;
import com.viglet.turing.genai.skill.sandbox.TurSkillSandboxService;
import com.viglet.turing.genai.skill.sandbox.TurSkillSandboxSession;
import com.viglet.turing.genai.skill.sandbox.TurSkillSandboxSessionManager;
import com.viglet.turing.genai.tool.TurCustomToolCallbackService;
import com.viglet.turing.persistence.model.skill.TurSkill;

import lombok.extern.slf4j.Slf4j;

/**
 * T322 / §IX.4.d — the {@code skill_bash} activation tool (third-level
 * progressive disclosure): mounted-folder code execution.
 *
 * <p>Once the model has loaded a skill's instructions ({@link TurLoadSkillToolCallback}),
 * it drives that skill's sandbox by calling this tool with the {@code skill} name
 * and a {@code command}. Each call opens (or reuses) the persistent session keyed
 * by {@code (agentId, conversationId, skillId)} — the agent / conversation ids are
 * read from the Spring AI {@link ToolContext}, the same keys the Code Interpreter
 * and {@code workspace_read} use — and runs the command in a fresh hardened Docker
 * container with the skill folder mounted read-only at {@code /skill} and a
 * persistent {@code /workspace} that survives across turns.
 *
 * <p>This is what lets a skill <em>run</em>: read its {@code references/}, execute
 * its {@code scripts/}, and write drafts to the workspace. Constrained to the
 * offered skill set; errors (unavailable sandbox, unknown skill, runtime failure)
 * are returned as plain strings so the model can recover rather than aborting the
 * turn.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
@Slf4j
public class TurSkillBashToolCallback implements ToolCallback {

    public static final String TOOL_NAME = "skill_bash";

    private static final String INPUT_SCHEMA = """
            {
              "type": "object",
              "properties": {
                "skill": {
                  "type": "string",
                  "description": "The name of the skill whose sandbox to run in (must be loaded first)."
                },
                "command": {
                  "type": "string",
                  "description": "The bash command to run. The skill folder is read-only at /skill ($SKILL_DIR); /workspace is writable and persists across turns."
                }
              },
              "required": ["skill", "command"]
            }""";

    private static final String DEFAULT_DESCRIPTION = """
            Run a bash command inside a skill's sandboxed container to operate the \
            skill: read its bundled files under /skill (also $SKILL_DIR, read-only), \
            run its scripts/, and write output to /workspace (writable, persists \
            across turns; it is the working directory). Load the skill first with \
            `load_skill`. Pass the skill `name` and the `command`. The container has \
            no network by default and limited CPU/memory.""";

    private final transient Map<String, TurSkill> index;
    private final transient TurSkillSandboxSessionManager sessionManager;
    private final transient TurSkillSandboxService sandboxService;
    private final ToolDefinition definition;

    public TurSkillBashToolCallback(List<TurSkill> offeredSkills,
            TurSkillSandboxSessionManager sessionManager,
            TurSkillSandboxService sandboxService) {
        this.index = TurSkillActivationSupport.index(offeredSkills);
        this.sessionManager = sessionManager;
        this.sandboxService = sandboxService;
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
        if (!sandboxService.isAvailable()) {
            return "skill_bash is unavailable: skills require the Code Interpreter execution "
                    + "mode to be DOCKER and object storage to be configured.";
        }
        String name = TurSkillActivationSupport.stringArg(toolInput, "skill", false);
        String command = TurSkillActivationSupport.stringArg(toolInput, "command", false);
        if (name == null) {
            return "skill_bash requires a non-blank 'skill' name argument.";
        }
        if (command == null) {
            return "skill_bash requires a non-blank 'command' argument.";
        }
        TurSkill skill = TurSkillActivationSupport.resolve(index, name);
        if (skill == null) {
            return "No skill named '" + name + "' is available for execution.";
        }
        String agentId = TurSkillActivationSupport.contextValue(
                toolContext, TurCustomToolCallbackService.TOOL_CONTEXT_AGENT_ID);
        String conversationId = TurSkillActivationSupport.contextValue(
                toolContext, TurCustomToolCallbackService.TOOL_CONTEXT_CONVERSATION_ID);
        try {
            TurSkillSandboxSession session = sessionManager.openSession(agentId, conversationId, skill.getId());
            TurSkillSandboxResult result = sandboxService.runBash(session, command);
            return result.combinedOutput();
        } catch (RuntimeException e) {
            log.warn("[skill_bash] skill='{}' conv={} command failed: {}",
                    skill.getName(), conversationId, e.getMessage());
            return "Failed to run command in skill '" + skill.getName() + "': " + e.getMessage();
        }
    }
}
