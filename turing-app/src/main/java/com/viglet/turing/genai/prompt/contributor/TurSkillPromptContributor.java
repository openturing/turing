/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.prompt.contributor;

import java.util.List;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.viglet.turing.genai.prompt.TurPromptAssemblyContext;
import com.viglet.turing.genai.prompt.TurPromptContributor;
import com.viglet.turing.genai.prompt.TurPromptSegment;
import com.viglet.turing.genai.prompt.TurPromptStability;
import com.viglet.turing.genai.skill.activation.TurSkillRunnerService;
import com.viglet.turing.persistence.model.agent.TurAIAgent;

/**
 * Block AL / §XXXV.1 — emits the T323 skill progressive-disclosure catalog (each
 * enabled skill's name + description + the {@code run_skill} instruction), honing
 * an optional single-skill pin (T325) via
 * {@link TurSkillRunnerService#offeredSkills}. No-op unless the agent enables
 * skills AND the delegation layer is available. The block already carries its
 * leading {@code "\n\n"} separator. STABLE — the catalog is constant for the
 * agent's (optionally pinned) skill set.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Component
public class TurSkillPromptContributor implements TurPromptContributor {

    private final TurSkillRunnerService skillRunnerService;

    public TurSkillPromptContributor(TurSkillRunnerService skillRunnerService) {
        this.skillRunnerService = skillRunnerService;
    }

    @Override
    public int order() {
        return ORDER_SKILL;
    }

    @Override
    public List<TurPromptSegment> contribute(TurPromptAssemblyContext context) {
        TurAIAgent agent = context.agent();
        if (!agent.isSkillsEnabled() || !skillRunnerService.isAvailable()) {
            return List.of();
        }
        String block = skillRunnerService.buildSystemPromptBlock(
                skillRunnerService.offeredSkills(context.selectedSkillId()));
        if (!StringUtils.hasText(block)) {
            return List.of();
        }
        return List.of(TurPromptSegment.of(TurPromptSegment.ORIGIN_SKILL,
                null, block, TurPromptStability.STABLE));
    }
}
