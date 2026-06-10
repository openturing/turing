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

import com.viglet.turing.genai.skill.TurSkillCatalogService;
import com.viglet.turing.persistence.model.skill.TurSkill;

import lombok.extern.slf4j.Slf4j;

/**
 * T322 / §IX.4.d — the {@code load_skill} activation tool (second-level
 * progressive disclosure).
 *
 * <p>The system prompt lists each offered skill's {@code name} + {@code description}
 * cheaply (level one). When the model judges a skill relevant it calls this tool
 * with that {@code name}; the tool returns the skill's full {@code SKILL.md} body
 * (level two) so the model learns how to operate it — then drives the mounted
 * folder through {@link TurSkillBashToolCallback} (level three).
 *
 * <p>Constrained to the offered set the {@link TurSkillActivationHarness} built
 * this callback with: a skill not exposed to the turn cannot be loaded, even if
 * it exists in the catalog. Errors are returned as plain strings (never thrown)
 * so the model can recover.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
@Slf4j
public class TurLoadSkillToolCallback implements ToolCallback {

    public static final String TOOL_NAME = "load_skill";

    private static final String INPUT_SCHEMA = """
            {
              "type": "object",
              "properties": {
                "skill": {
                  "type": "string",
                  "description": "The name of the skill to load, exactly as listed in the available skills."
                }
              },
              "required": ["skill"]
            }""";

    private static final String DEFAULT_DESCRIPTION = """
            Load a skill's full instructions (its SKILL.md body) so you can operate \
            it. Call this only when one of the available skills, judged by its \
            short description, matches the task at hand. Pass the skill `name` \
            exactly as listed. After loading, follow the returned instructions and \
            use `skill_bash` to run the skill's scripts and read its bundled files.""";

    private final transient Map<String, TurSkill> index;
    private final transient TurSkillCatalogService catalogService;
    private final ToolDefinition definition;

    public TurLoadSkillToolCallback(List<TurSkill> offeredSkills, TurSkillCatalogService catalogService) {
        this.index = TurSkillActivationSupport.index(offeredSkills);
        this.catalogService = catalogService;
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
        String name = TurSkillActivationSupport.stringArg(toolInput, "skill", true);
        if (name == null) {
            return "load_skill requires a non-blank 'skill' name argument.";
        }
        TurSkill skill = TurSkillActivationSupport.resolve(index, name);
        if (skill == null) {
            return "No skill named '" + name + "' is available. Available skills: "
                    + String.join(", ", availableNames()) + ".";
        }
        try {
            return catalogService.getSkillMarkdown(skill.getId())
                    .filter(md -> !md.isBlank())
                    .orElseGet(() -> "Skill '" + skill.getName()
                            + "' has no readable SKILL.md instructions.");
        } catch (RuntimeException e) {
            log.warn("[load_skill] could not load SKILL.md for '{}': {}", skill.getName(), e.getMessage());
            return "Failed to load instructions for skill '" + skill.getName() + "': " + e.getMessage();
        }
    }

    private List<String> availableNames() {
        return index.values().stream().map(TurSkill::getName).distinct().sorted().toList();
    }
}
