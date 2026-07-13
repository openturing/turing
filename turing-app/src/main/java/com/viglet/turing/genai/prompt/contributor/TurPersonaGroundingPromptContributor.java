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

import com.viglet.turing.genai.persona.TurPersonaGroundingService;
import com.viglet.turing.genai.prompt.TurPromptAssemblyContext;
import com.viglet.turing.genai.prompt.TurPromptContributor;
import com.viglet.turing.genai.prompt.TurPromptSegment;
import com.viglet.turing.genai.prompt.TurPromptStability;
import com.viglet.turing.persistence.model.persona.TurPersona;

/**
 * T718 / §XLVI.1 — emits the persona's per-turn grounded-knowledge block when the
 * persona is bound to a knowledge source (an SN site or its Block AA notebook).
 * The block sits right after the persona head and before the agent base, so a
 * grounded participant answers from retrieved proprietary content rather than the
 * model's priors. A persona with no grounding (the default) contributes nothing,
 * so the empty/legacy pipeline is byte-for-byte unchanged.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Component
public class TurPersonaGroundingPromptContributor implements TurPromptContributor {

    private final TurPersonaGroundingService groundingService;

    public TurPersonaGroundingPromptContributor(TurPersonaGroundingService groundingService) {
        this.groundingService = groundingService;
    }

    @Override
    public int order() {
        return ORDER_GROUNDING;
    }

    @Override
    public List<TurPromptSegment> contribute(TurPromptAssemblyContext context) {
        TurPersona persona = context.persona();
        if (persona == null) {
            return List.of();
        }
        String block = groundingService.groundingBlock(persona, context.userQuery());
        if (!StringUtils.hasText(block)) {
            return List.of();
        }
        return List.of(TurPromptSegment.of(TurPromptSegment.ORIGIN_GROUNDING,
                persona.getName(), block, TurPromptStability.PER_TURN));
    }
}
