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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

/**
 * T317 / §IX.4.d — extracts the leading YAML frontmatter block from a
 * {@code SKILL.md} document and maps it to a {@link TurSkillFrontmatter}.
 *
 * <p>Anthropic skill frontmatter looks like:
 * <pre>{@code
 * ---
 * name: brand-content-studio
 * description: >-
 *   ...
 * license: Apache-2.0
 * metadata:
 *   version: 2.3.0
 *   authors:
 *     - acme-content-team
 * ---
 * # body...
 * }</pre>
 *
 * <p>Version resolution prefers a top-level {@code version}, then
 * {@code metadata.version}. Author resolution prefers a top-level
 * {@code author}, then the first entry of {@code metadata.authors}.
 *
 * <p>Parsing is lenient: a missing/malformed frontmatter block yields an
 * {@link TurSkillFrontmatter#isValid() invalid} result (blank name) rather than
 * throwing, so one broken folder never aborts a full reindex.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
@Component
public class TurSkillFrontmatterParser {

    private static final Logger log = LoggerFactory.getLogger(TurSkillFrontmatterParser.class);
    private static final String FENCE = "---";

    /** Parse {@code SKILL.md} contents; never throws — returns an invalid frontmatter on failure. */
    public TurSkillFrontmatter parse(String skillMarkdown) {
        String yaml = extractFrontmatterBlock(skillMarkdown);
        if (yaml == null) {
            return new TurSkillFrontmatter(null, null, null, null);
        }
        try {
            Yaml parser = new Yaml(new SafeConstructor(new LoaderOptions()));
            Object loaded = parser.load(yaml);
            if (!(loaded instanceof Map<?, ?> root)) {
                return new TurSkillFrontmatter(null, null, null, null);
            }
            Map<?, ?> metadata = asMap(root.get("metadata"));
            String name = asString(root.get("name"));
            String description = asString(root.get("description"));
            String version = firstNonBlank(asString(root.get("version")), asString(metadata.get("version")));
            String author = firstNonBlank(asString(root.get("author")), firstAuthor(metadata.get("authors")));
            return new TurSkillFrontmatter(name, description, version, author);
        } catch (RuntimeException e) {
            log.warn("[Skills] Could not parse SKILL.md frontmatter: {}", e.getMessage());
            return new TurSkillFrontmatter(null, null, null, null);
        }
    }

    /**
     * Returns the text between the opening {@code ---} fence and the next
     * closing fence ({@code ---} or {@code ...}), or {@code null} when the
     * document does not begin with a frontmatter block.
     */
    private String extractFrontmatterBlock(String markdown) {
        if (markdown == null) {
            return null;
        }
        // Tolerate a UTF-8 BOM and leading blank lines before the opening fence.
        String content = markdown.startsWith("﻿") ? markdown.substring(1) : markdown;
        String[] lines = content.split("\r\n|\r|\n", -1);
        int start = 0;
        while (start < lines.length && lines[start].isBlank()) {
            start++;
        }
        if (start >= lines.length || !lines[start].trim().equals(FENCE)) {
            return null;
        }
        StringBuilder block = new StringBuilder();
        for (int i = start + 1; i < lines.length; i++) {
            String trimmed = lines[i].trim();
            if (trimmed.equals(FENCE) || trimmed.equals("...")) {
                return block.toString();
            }
            block.append(lines[i]).append('\n');
        }
        // No closing fence — treat as malformed.
        return null;
    }

    private static Map<?, ?> asMap(Object value) {
        return value instanceof Map<?, ?> map ? map : Map.of();
    }

    private static String asString(Object value) {
        return value == null ? null : value.toString();
    }

    private static String firstAuthor(Object authors) {
        if (authors instanceof List<?> list && !list.isEmpty() && list.getFirst() != null) {
            return list.getFirst().toString();
        }
        return asString(authors);
    }

    private static String firstNonBlank(String a, String b) {
        if (a != null && !a.isBlank()) {
            return a;
        }
        return (b != null && !b.isBlank()) ? b : null;
    }
}
