/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.skill;

import java.util.List;
import java.util.Map;

import tools.jackson.databind.JsonNode;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * T116 / §IX.4 — the canonical {@code skill.manifest.json} shape: a typed,
 * versioned, marketplace-distributable bundle. This record is the exchange
 * format between the REST API, the bundled-catalog loader, and the persisted
 * {@link com.viglet.turing.persistence.model.skill.TurSkill} entity. Mapping in
 * both directions is owned by {@link TurSkillManifestService}.
 *
 * <p>Unknown properties are tolerated so a manifest authored against a newer
 * schema (extra fields) still installs on an older Turing — forward
 * compatibility is a marketplace requirement.
 *
 * <pre>{@code
 * {
 *   "name": "scaffold-markdown",
 *   "version": "1.2.0",
 *   "author": "viglet",
 *   "signature": "sha256:...",
 *   "description": "Generate beat-by-beat markdown scaffolds...",
 *   "personaFragment": {
 *     "instruction": "When asked to compose narrative content...",
 *     "vocab": { "mandatory": ["beat"], "forbidden": [] }
 *   },
 *   "flowSnippet": { "nodes": [...], "edges": [...] },
 *   "tools": ["scaffold.markdown", "scaffold.pickQuote"],
 *   "workspaceDefaults": { "draft.md": "# {{title}}\n\n## Hook\n..." },
 *   "dependencies": ["http@^1.0.0"]
 * }
 * }</pre>
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TurSkillManifest(
        String name,
        String version,
        String author,
        String signature,
        String description,
        PersonaFragment personaFragment,
        JsonNode flowSnippet,
        List<String> tools,
        Map<String, String> workspaceDefaults,
        List<String> dependencies) {

    /**
     * The persona contribution a skill makes — an instruction snippet grafted
     * onto the agent's system prompt plus optional tone-validator vocabulary.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PersonaFragment(String instruction, Vocab vocab) {
    }

    /**
     * Tone-validator vocabulary contributed by the skill — words that must
     * appear ({@code mandatory}) and words that must not ({@code forbidden}).
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Vocab(List<String> mandatory, List<String> forbidden) {
    }
}
