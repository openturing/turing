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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.ai.chat.model.ToolContext;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.viglet.turing.persistence.model.skill.TurSkill;

/**
 * T322 / §IX.4.d — small shared helpers for the skill activation tool
 * callbacks ({@link TurLoadSkillToolCallback}, {@link TurSkillBashToolCallback}):
 * resolving an offered skill by {@code name} (or id), reading a string argument
 * from a tool's JSON input, and reading a value from the Spring AI
 * {@link ToolContext}.
 *
 * <p>Resolution is constrained to the <em>offered</em> set the harness built the
 * callback with — a skill the turn did not expose cannot be loaded or run, even
 * if it exists in the catalog.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
final class TurSkillActivationSupport {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private TurSkillActivationSupport() {
    }

    /** Index the offered skills by lowercased name and by id for tolerant lookup. */
    static Map<String, TurSkill> index(List<TurSkill> offered) {
        Map<String, TurSkill> index = new LinkedHashMap<>();
        for (TurSkill skill : offered) {
            if (skill == null) {
                continue;
            }
            if (skill.getName() != null && !skill.getName().isBlank()) {
                index.put(skill.getName().trim().toLowerCase(), skill);
            }
            if (skill.getId() != null && !skill.getId().isBlank()) {
                index.putIfAbsent(skill.getId().trim().toLowerCase(), skill);
            }
        }
        return index;
    }

    /** Resolve an offered skill by name (case-insensitive) or id; {@code null} if not offered. */
    static TurSkill resolve(Map<String, TurSkill> index, String nameOrId) {
        if (nameOrId == null || nameOrId.isBlank()) {
            return null;
        }
        return index.get(nameOrId.trim().toLowerCase());
    }

    /**
     * Read a single string argument from a tool's JSON input. Falls back to
     * treating the whole input as the value when it is a bare (non-JSON) string —
     * models occasionally pass {@code "my-skill"} instead of
     * {@code {"skill":"my-skill"}}, but only for single-argument tools.
     */
    @SuppressWarnings("unchecked")
    static String stringArg(String toolInput, String key, boolean allowBareFallback) {
        if (toolInput == null || toolInput.isBlank()) {
            return null;
        }
        try {
            Map<String, Object> args = MAPPER.readValue(toolInput, Map.class);
            Object value = args.get(key);
            if (value == null) {
                return null;
            }
            String text = value.toString().strip();
            return text.isEmpty() ? null : text;
        } catch (Exception e) {
            if (!allowBareFallback) {
                return null;
            }
            String text = toolInput.strip();
            return text.isEmpty() ? null : text;
        }
    }

    /** Read a value from the Spring AI tool context; {@code null} when absent/blank. */
    static String contextValue(ToolContext toolContext, String key) {
        if (toolContext == null) {
            return null;
        }
        Map<String, Object> ctx = toolContext.getContext();
        Object value = ctx.get(key);
        if (value == null) {
            return null;
        }
        String text = value.toString();
        return text.isBlank() ? null : text;
    }
}
