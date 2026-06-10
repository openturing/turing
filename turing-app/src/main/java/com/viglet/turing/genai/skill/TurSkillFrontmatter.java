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

/**
 * T317 / §IX.4.d — the subset of {@code SKILL.md} YAML frontmatter that the
 * thin catalog index cares about. Parsed by {@link TurSkillFrontmatterParser}.
 *
 * <p>An empty/blank {@link #name} signals "not a valid skill" — the indexer
 * skips such folders.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
public record TurSkillFrontmatter(String name, String description, String version, String author) {

    public boolean isValid() {
        return name != null && !name.isBlank();
    }
}
