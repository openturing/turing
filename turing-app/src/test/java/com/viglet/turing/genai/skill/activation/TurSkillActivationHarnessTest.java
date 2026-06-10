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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.tool.ToolCallback;

import com.viglet.turing.genai.skill.TurSkillCatalogService;
import com.viglet.turing.genai.skill.sandbox.TurSkillSandboxService;
import com.viglet.turing.genai.skill.sandbox.TurSkillSandboxSessionManager;
import com.viglet.turing.persistence.model.skill.TurSkill;

/**
 * T322 — verifies the activation harness gates on sandbox availability, builds a
 * cheap progressive-disclosure prompt block (name + description only), and wires
 * the two activation tool callbacks.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
@ExtendWith(MockitoExtension.class)
class TurSkillActivationHarnessTest {

    @Mock
    TurSkillCatalogService catalogService;
    @Mock
    TurSkillSandboxService sandboxService;
    @Mock
    TurSkillSandboxSessionManager sessionManager;

    private TurSkillActivationHarness harness;

    @BeforeEach
    void setUp() {
        harness = new TurSkillActivationHarness(catalogService, sandboxService, sessionManager);
    }

    private TurSkill skill(String id, String name, String description, String version, int enabled) {
        TurSkill skill = new TurSkill();
        skill.setId(id);
        skill.setName(name);
        skill.setDescription(description);
        skill.setVersion(version);
        skill.setEnabled(enabled);
        return skill;
    }

    @Test
    void unavailableWhenSandboxUnavailable() {
        when(sandboxService.isAvailable()).thenReturn(false);
        assertThat(harness.isAvailable()).isFalse();
    }

    @Test
    void promptBlockEmptyWhenSandboxUnavailable() {
        when(sandboxService.isAvailable()).thenReturn(false);
        assertThat(harness.buildSystemPromptBlock(
                List.of(skill("1", "alpha", "does alpha things", "1.0", 1)))).isEmpty();
    }

    @Test
    void promptBlockEmptyWhenNoSkills() {
        lenient().when(sandboxService.isAvailable()).thenReturn(true);
        assertThat(harness.buildSystemPromptBlock(List.of())).isEmpty();
        assertThat(harness.buildSystemPromptBlock(null)).isEmpty();
    }

    @Test
    void promptBlockListsNameDescriptionAndVersionSorted() {
        when(sandboxService.isAvailable()).thenReturn(true);
        String block = harness.buildSystemPromptBlock(List.of(
                skill("2", "zebra", "stripes things", null, 1),
                skill("1", "alpha", "does alpha things", "1.2.3", 1)));

        assertThat(block)
                .contains(TurSkillActivationHarness.BLOCK_HEADER)
                .contains("## alpha (v1.2.3)")
                .contains("does alpha things")
                .contains("## zebra")
                .contains("stripes things")
                .contains("load_skill")
                .contains("skill_bash")
                .contains("/skill")
                .contains("/workspace");
        // Deterministic order: alpha before zebra.
        assertThat(block.indexOf("## alpha")).isLessThan(block.indexOf("## zebra"));
        // No version parenthetical for the version-less skill.
        assertThat(block).contains("## zebra\n");
    }

    @Test
    void promptBlockHandlesBlankDescription() {
        when(sandboxService.isAvailable()).thenReturn(true);
        String block = harness.buildSystemPromptBlock(List.of(skill("1", "alpha", "  ", "1.0", 1)));
        assertThat(block).contains("(no description)");
    }

    @Test
    void buildToolCallbacksReturnsLoadAndBash() {
        when(sandboxService.isAvailable()).thenReturn(true);
        ToolCallback[] callbacks = harness.buildToolCallbacks(
                List.of(skill("1", "alpha", "does alpha", "1.0", 1)));
        assertThat(callbacks).hasSize(2);
        assertThat(callbacks[0].getToolDefinition().name()).isEqualTo(TurLoadSkillToolCallback.TOOL_NAME);
        assertThat(callbacks[1].getToolDefinition().name()).isEqualTo(TurSkillBashToolCallback.TOOL_NAME);
    }

    @Test
    void buildToolCallbacksEmptyWhenUnavailableOrNoSkills() {
        when(sandboxService.isAvailable()).thenReturn(false);
        assertThat(harness.buildToolCallbacks(List.of(skill("1", "a", "d", "1", 1)))).isEmpty();
    }

    @Test
    void availableSkillsReturnsOnlyEnabledSorted() {
        when(catalogService.listAll()).thenReturn(List.of(
                skill("2", "zebra", "z", "1", 1),
                skill("3", "beta", "b", "1", 0),
                skill("1", "alpha", "a", "1", 1)));
        List<TurSkill> result = harness.availableSkills();
        assertThat(result).extracting(TurSkill::getName).containsExactly("alpha", "zebra");
    }
}
