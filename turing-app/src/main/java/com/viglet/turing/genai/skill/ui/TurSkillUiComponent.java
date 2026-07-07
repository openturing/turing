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

/**
 * T449 / §XXIII.8 — one UI component a skill folder ships in its {@code
 * ui/components.json}. Surfaced both as a generative-UI client tool (so the agent
 * can render it) and via the discovery endpoint (so the host registers a renderer
 * for {@link #toolName}).
 *
 * @param skillId        owning skill id
 * @param skillName      owning skill name (from SKILL.md frontmatter)
 * @param componentName  the component name as authored in {@code ui/components.json}
 * @param toolName       the globally-unique, skill-qualified client-tool name the
 *                       agent calls and the host registers (e.g. {@code returns__rma_form})
 * @param description    what the component renders / when to use it
 * @param schema         JSON-Schema for the component props (may be null → empty object)
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
public record TurSkillUiComponent(
        String skillId,
        String skillName,
        String componentName,
        String toolName,
        String description,
        String schema) {
}
