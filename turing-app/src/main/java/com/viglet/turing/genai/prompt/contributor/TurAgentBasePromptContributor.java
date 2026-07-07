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

/**
 * Block AL / §XXXV.1 — emits the agent's base system prompt (or the SN-site RAG
 * override the executor already resolved into {@code baseSystemPrompt}). It is
 * the first body segment, so the pipeline prepends the persona
 * {@code "\n\n----\n"} wrap in front of it when a persona applies. STABLE — a
 * RAG override, when present, replaces this text but is itself constant for the
 * retrieved context of the turn.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Component
public class TurAgentBasePromptContributor implements TurPromptContributor {

    @Override
    public int order() {
        return ORDER_AGENT_BASE;
    }

    @Override
    public List<TurPromptSegment> contribute(TurPromptAssemblyContext context) {
        String base = context.baseSystemPrompt();
        if (!StringUtils.hasText(base)) {
            return List.of();
        }
        return List.of(TurPromptSegment.of(TurPromptSegment.ORIGIN_AGENT,
                null, base, TurPromptStability.STABLE));
    }
}
