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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.viglet.turing.genai.clienttool.TurClientTool;
import com.viglet.turing.genai.skill.activation.TurSkillRunnerService;
import com.viglet.turing.persistence.model.agent.TurAIAgent;
import com.viglet.turing.persistence.model.skill.TurSkill;

@ExtendWith(MockitoExtension.class)
class TurSkillUiClientToolProviderTest {

    @Mock private TurSkillRunnerService skillRunnerService;
    @Mock private TurSkillUiService skillUiService;

    private TurSkillUiClientToolProvider provider() {
        return new TurSkillUiClientToolProvider(skillRunnerService, skillUiService);
    }

    private static TurAIAgent agent(boolean skillsEnabled) {
        TurAIAgent a = new TurAIAgent();
        a.setSkillsEnabled(skillsEnabled);
        return a;
    }

    @Test
    void appliesOnlyWhenSkillsEnabledAndAvailable() {
        lenient().when(skillRunnerService.isAvailable()).thenReturn(true);
        assertThat(provider().appliesTo(agent(false))).isFalse();
        assertThat(provider().appliesTo(agent(true))).isTrue();
        assertThat(provider().appliesTo(null)).isFalse();
    }

    @Test
    void notAvailableNeverApplies() {
        when(skillRunnerService.isAvailable()).thenReturn(false);
        assertThat(provider().appliesTo(agent(true))).isFalse();
    }

    @Test
    void mapsEachSkillsUiComponentsToClientTools() {
        TurSkill s1 = new TurSkill();
        s1.setId("s1");
        when(skillRunnerService.availableSkills()).thenReturn(List.of(s1));
        when(skillUiService.listComponents(s1)).thenReturn(List.of(
                new TurSkillUiComponent("s1", "Returns", "rma_form", "returns__rma_form",
                        "Collect items", "{\"type\":\"object\"}")));
        List<TurClientTool> tools = provider().declarations(agent(true));
        assertThat(tools).hasSize(1);
        assertThat(tools.get(0).name()).isEqualTo("returns__rma_form");
        assertThat(tools.get(0).description()).isEqualTo("Collect items");
    }
}
