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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.viglet.turing.genai.skill.TurSkillCatalogService;
import com.viglet.turing.persistence.model.skill.TurSkill;

/**
 * T322 — verifies {@code load_skill} resolves an offered skill by name (and id),
 * returns its SKILL.md body, refuses skills not offered, and degrades gracefully.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
@ExtendWith(MockitoExtension.class)
class TurLoadSkillToolCallbackTest {

    @Mock
    TurSkillCatalogService catalogService;

    private TurSkill alpha;
    private TurLoadSkillToolCallback callback;

    @BeforeEach
    void setUp() {
        alpha = new TurSkill();
        alpha.setId("id-alpha");
        alpha.setName("alpha");
        alpha.setDescription("does alpha");
        callback = new TurLoadSkillToolCallback(List.of(alpha), catalogService);
    }

    @Test
    void hasExpectedDefinition() {
        assertThat(callback.getToolDefinition().name()).isEqualTo("load_skill");
        assertThat(callback.getToolDefinition().inputSchema()).contains("skill");
    }

    @Test
    void returnsSkillMarkdownByName() {
        when(catalogService.getSkillMarkdown("id-alpha")).thenReturn(Optional.of("# Alpha\nbody"));
        assertThat(callback.call("{\"skill\":\"alpha\"}")).contains("# Alpha").contains("body");
    }

    @Test
    void resolvesCaseInsensitivelyAndById() {
        when(catalogService.getSkillMarkdown("id-alpha")).thenReturn(Optional.of("body"));
        assertThat(callback.call("{\"skill\":\"ALPHA\"}")).isEqualTo("body");
        assertThat(callback.call("{\"skill\":\"id-alpha\"}")).isEqualTo("body");
    }

    @Test
    void acceptsBareStringInput() {
        when(catalogService.getSkillMarkdown("id-alpha")).thenReturn(Optional.of("body"));
        assertThat(callback.call("alpha")).isEqualTo("body");
    }

    @Test
    void unknownSkillListsAvailable() {
        String result = callback.call("{\"skill\":\"ghost\"}");
        assertThat(result).contains("No skill named 'ghost'").contains("alpha");
    }

    @Test
    void blankArgReturnsError() {
        assertThat(callback.call("{}")).contains("requires a non-blank 'skill'");
    }

    @Test
    void missingMarkdownReturnsMessage() {
        when(catalogService.getSkillMarkdown("id-alpha")).thenReturn(Optional.empty());
        assertThat(callback.call("{\"skill\":\"alpha\"}")).contains("no readable SKILL.md");
    }

    @Test
    void loadFailureIsReportedNotThrown() {
        lenient().when(catalogService.getSkillMarkdown("id-alpha"))
                .thenThrow(new RuntimeException("storage down"));
        assertThat(callback.call("{\"skill\":\"alpha\"}"))
                .contains("Failed to load instructions").contains("storage down");
    }
}
