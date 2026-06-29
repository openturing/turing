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

import org.springframework.stereotype.Component;

import com.viglet.turing.genai.clienttool.TurBuiltInClientToolProvider;
import com.viglet.turing.genai.clienttool.TurClientTool;
import com.viglet.turing.genai.skill.activation.TurSkillRunnerService;
import com.viglet.turing.persistence.model.agent.TurAIAgent;

/**
 * T449 / §XXIII.8 — exposes each available skill's shipped UI components (its
 * {@code ui/components.json}) as generative-UI client tools, so the agent can
 * render a skill's own UI through the same T440 surface as the built-in
 * answer-as-app components. Advertised only when the agent has skills enabled and
 * the skill layer is available; the host registers a renderer per
 * {@link TurSkillUiComponent#toolName()} (discoverable via {@code GET
 * /api/skill/{id}/ui-components}).
 *
 * <p>This is the first dynamic {@link TurBuiltInClientToolProvider}: its
 * declarations vary by the agent's available skills, which is why the seam takes
 * the agent.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Component
public class TurSkillUiClientToolProvider implements TurBuiltInClientToolProvider {

    private final TurSkillRunnerService skillRunnerService;
    private final TurSkillUiService skillUiService;

    public TurSkillUiClientToolProvider(TurSkillRunnerService skillRunnerService,
            TurSkillUiService skillUiService) {
        this.skillRunnerService = skillRunnerService;
        this.skillUiService = skillUiService;
    }

    @Override
    public boolean appliesTo(TurAIAgent agent) {
        return agent != null && agent.isSkillsEnabled() && skillRunnerService.isAvailable();
    }

    @Override
    public List<TurClientTool> declarations(TurAIAgent agent) {
        List<TurClientTool> tools = new ArrayList<>();
        skillRunnerService.availableSkills().forEach(skill ->
                skillUiService.listComponents(skill).forEach(ui ->
                        tools.add(new TurClientTool(ui.toolName(), ui.description(), ui.schema()))));
        return tools;
    }
}
