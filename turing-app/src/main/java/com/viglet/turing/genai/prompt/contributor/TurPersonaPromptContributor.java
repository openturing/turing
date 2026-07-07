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

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.viglet.turing.genai.persona.TurPersonaPromptComposer;
import com.viglet.turing.genai.persona.TurPersonaStaticPromptCache;
import com.viglet.turing.genai.prompt.TurPromptAssemblyContext;
import com.viglet.turing.genai.prompt.TurPromptContributor;
import com.viglet.turing.genai.prompt.TurPromptSegment;
import com.viglet.turing.genai.prompt.TurPromptStability;
import com.viglet.turing.persistence.model.persona.TurPersona;

/**
 * Block AL / §XXXV.1 — emits the persona "head" of the system prompt: the
 * deterministic static block (STABLE, T31-cached) plus, when a live query and
 * embedding model are supplied, the retrieved few-shot examples (PER_TURN). This
 * is the exact text the legacy {@link TurPersonaPromptComposer} placed before the
 * {@code "\n\n----\n"} wrap; the pipeline reuses the composer's own
 * {@link TurPersonaPromptComposer#fewShotBlock} so the two never drift.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Component
public class TurPersonaPromptContributor implements TurPromptContributor {

    private final TurPersonaStaticPromptCache staticPromptCache;
    private final TurPersonaPromptComposer personaPromptComposer;

    public TurPersonaPromptContributor(TurPersonaStaticPromptCache staticPromptCache,
            TurPersonaPromptComposer personaPromptComposer) {
        this.staticPromptCache = staticPromptCache;
        this.personaPromptComposer = personaPromptComposer;
    }

    @Override
    public int order() {
        return ORDER_PERSONA;
    }

    @Override
    public List<TurPromptSegment> contribute(TurPromptAssemblyContext context) {
        TurPersona persona = context.persona();
        if (persona == null) {
            return List.of();
        }
        List<TurPromptSegment> segments = new ArrayList<>(2);
        String staticBlock = staticPromptCache.composeStaticBlock(persona);
        if (StringUtils.hasText(staticBlock)) {
            segments.add(TurPromptSegment.of(TurPromptSegment.ORIGIN_PERSONA,
                    persona.getName(), staticBlock, TurPromptStability.STABLE));
        }
        String fewShot = personaPromptComposer.fewShotBlock(persona,
                context.embeddingModel(), context.userQuery());
        if (StringUtils.hasText(fewShot)) {
            segments.add(TurPromptSegment.of(TurPromptSegment.ORIGIN_FEW_SHOT,
                    persona.getName(), fewShot, TurPromptStability.PER_TURN));
        }
        return segments;
    }
}
