/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.skill.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Service;

import com.viglet.turing.genai.skill.TurSkillCatalogService;
import com.viglet.turing.genai.skill.TurSkillFileService;
import com.viglet.turing.persistence.model.skill.TurSkill;

import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * T449 / §XXIII.8 — reads a skill folder's {@code ui/components.json} and turns it
 * into {@link TurSkillUiComponent}s. This is what lets a skill graduate from
 * prompt+scripts to a vertical app: it ships UI alongside its logic.
 *
 * <p>Convention — {@code <skill>/ui/components.json}:
 * <pre>
 * { "components": [
 *     { "name": "rma_form",
 *       "description": "Collect the items to return and the reason.",
 *       "schema": { "type": "object", "properties": { ... } } }
 * ] }
 * </pre>
 *
 * <p>Each component is exposed under a skill-qualified, globally-unique
 * {@code toolName} ({@code <skill>__<component>}) so two skills can declare the
 * same component name without colliding. Read results are cached per skill id
 * (small files, hot path is the per-turn client-tool resolution); call
 * {@link #clearCache()} after a reindex.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Slf4j
@Service
public class TurSkillUiService {

    /** Path of the UI manifest within a skill folder. */
    public static final String MANIFEST_PATH = "ui/components.json";

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final TurSkillCatalogService catalogService;
    private final TurSkillFileService fileService;

    /** skillId → its parsed UI components (empty list when the skill ships none). */
    private final Map<String, List<TurSkillUiComponent>> cache = new ConcurrentHashMap<>();

    public TurSkillUiService(TurSkillCatalogService catalogService,
            TurSkillFileService fileService) {
        this.catalogService = catalogService;
        this.fileService = fileService;
    }

    /** UI components for a skill id (empty when unknown or none declared). */
    public List<TurSkillUiComponent> listComponents(String skillId) {
        if (skillId == null || skillId.isBlank()) {
            return List.of();
        }
        return cache.computeIfAbsent(skillId, id ->
                catalogService.findById(id).map(this::parse).orElseGet(List::of));
    }

    /** UI components for a resolved skill. */
    public List<TurSkillUiComponent> listComponents(TurSkill skill) {
        if (skill == null || skill.getId() == null) {
            return List.of();
        }
        return cache.computeIfAbsent(skill.getId(), id -> parse(skill));
    }

    /** Drop cached manifests — call after a catalog reindex or a UI file edit. */
    public void clearCache() {
        cache.clear();
    }

    private List<TurSkillUiComponent> parse(TurSkill skill) {
        String json = fileService.readFile(skill.getId(), MANIFEST_PATH).orElse(null);
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            JsonNode root = OBJECT_MAPPER.readTree(json);
            JsonNode components = root.get("components");
            if (components == null || !components.isArray()) {
                return List.of();
            }
            List<TurSkillUiComponent> out = new ArrayList<>();
            for (JsonNode node : components) {
                String name = text(node, "name");
                if (name == null || name.isBlank()) {
                    continue;
                }
                JsonNode schemaNode = node.get("schema");
                String schema = schemaNode == null || schemaNode.isNull() ? null
                        : (schemaNode.isString() ? schemaNode.asString() : schemaNode.toString());
                out.add(new TurSkillUiComponent(skill.getId(), skill.getName(), name.trim(),
                        toolName(skill.getName(), name), text(node, "description"), schema));
            }
            return List.copyOf(out);
        } catch (RuntimeException e) {
            log.warn("[SkillUI] skill '{}' has a malformed {}: {}", skill.getName(),
                    MANIFEST_PATH, e.getMessage());
            return List.of();
        }
    }

    /** {@code <skill>__<component>}, sanitised to a safe tool-name charset. */
    static String toolName(String skillName, String componentName) {
        return (sanitize(skillName) + "__" + sanitize(componentName));
    }

    private static String sanitize(String s) {
        return s == null ? "" : s.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "_");
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.get(field);
        return value == null || value.isNull() ? null : value.asString();
    }
}
