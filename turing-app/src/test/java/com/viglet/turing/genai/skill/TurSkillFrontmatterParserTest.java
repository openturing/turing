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

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * T317 / §IX.4.d — unit tests for {@link TurSkillFrontmatterParser}: it must
 * pull {@code name}/{@code description}/{@code version}/{@code author} out of
 * Anthropic-style {@code SKILL.md} frontmatter and degrade gracefully (never
 * throw) on malformed or missing blocks.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
class TurSkillFrontmatterParserTest {

    private final TurSkillFrontmatterParser parser = new TurSkillFrontmatterParser();

    @Test
    void parsesAnthropicStyleFrontmatterWithNestedMetadata() {
        String md = """
                ---
                name: brand-content-studio
                description: >-
                  Compose on-brand narrative content for Acme Corp. Use this skill
                  whenever the user asks to draft or polish marketing content.
                license: Apache-2.0
                allowed-tools: Read, Write, Bash
                metadata:
                  version: 2.3.0
                  authors:
                    - acme-content-team
                  homepage: https://intranet.acme.example/skills/brand-content-studio
                  tags: [content, marketing]
                ---

                # Brand Content Studio
                Body text that must be ignored.
                """;

        TurSkillFrontmatter fm = parser.parse(md);

        assertThat(fm.isValid()).isTrue();
        assertThat(fm.name()).isEqualTo("brand-content-studio");
        assertThat(fm.description()).contains("Compose on-brand narrative content for Acme Corp.");
        assertThat(fm.version()).isEqualTo("2.3.0");
        assertThat(fm.author()).isEqualTo("acme-content-team");
    }

    @Test
    void prefersTopLevelVersionAndAuthorOverMetadata() {
        String md = """
                ---
                name: top-level-wins
                version: 9.9.9
                author: jane-doe
                metadata:
                  version: 1.0.0
                  authors:
                    - someone-else
                ---
                body
                """;

        TurSkillFrontmatter fm = parser.parse(md);

        assertThat(fm.version()).isEqualTo("9.9.9");
        assertThat(fm.author()).isEqualTo("jane-doe");
    }

    @Test
    void toleratesMissingOptionalFields() {
        String md = """
                ---
                name: minimal
                ---
                body
                """;

        TurSkillFrontmatter fm = parser.parse(md);

        assertThat(fm.isValid()).isTrue();
        assertThat(fm.name()).isEqualTo("minimal");
        assertThat(fm.description()).isNull();
        assertThat(fm.version()).isNull();
        assertThat(fm.author()).isNull();
    }

    @Test
    void returnsInvalidWhenThereIsNoFrontmatterFence() {
        TurSkillFrontmatter fm = parser.parse("# Just a heading\nno frontmatter here");

        assertThat(fm.isValid()).isFalse();
        assertThat(fm.name()).isNull();
    }

    @Test
    void returnsInvalidWhenFrontmatterIsUnterminated() {
        String md = """
                ---
                name: never-closed
                description: oops
                """;

        assertThat(parser.parse(md).isValid()).isFalse();
    }

    @Test
    void returnsInvalidOnNullOrBlankInput() {
        assertThat(parser.parse(null).isValid()).isFalse();
        assertThat(parser.parse("").isValid()).isFalse();
        assertThat(parser.parse("   ").isValid()).isFalse();
    }

    @Test
    void toleratesLeadingBlankLinesBeforeFence() {
        String md = "\n\n---\nname: padded\n---\nbody";

        assertThat(parser.parse(md).name()).isEqualTo("padded");
    }
}
