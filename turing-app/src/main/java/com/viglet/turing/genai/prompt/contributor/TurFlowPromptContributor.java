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

import com.viglet.turing.genai.TurAgentChatFlowContext;
import com.viglet.turing.genai.flow.TurChatFlowEngineService;
import com.viglet.turing.genai.prompt.TurPromptAssemblyContext;
import com.viglet.turing.genai.prompt.TurPromptContributor;
import com.viglet.turing.genai.prompt.TurPromptSegment;
import com.viglet.turing.genai.prompt.TurPromptStability;

/**
 * Block AL / §XXXV.1 — emits the active chat-flow node addendum
 * ({@link TurChatFlowEngineService#buildSystemPromptAddendum}): the "## ACTIVE
 * FLOW STEP" goal + collect/validation + "already collected" lines. It carries
 * its own leading {@code "\n\n"} separator. PER_TURN — it changes as the
 * conversation walks the flow, and is the reason the cache-hostile ordering
 * matters (T614): keeping it after the STABLE prefix lets the prefix cache.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Component
public class TurFlowPromptContributor implements TurPromptContributor {

    private final TurChatFlowEngineService chatFlowEngineService;

    public TurFlowPromptContributor(TurChatFlowEngineService chatFlowEngineService) {
        this.chatFlowEngineService = chatFlowEngineService;
    }

    @Override
    public int order() {
        return ORDER_FLOW;
    }

    @Override
    public List<TurPromptSegment> contribute(TurPromptAssemblyContext context) {
        TurAgentChatFlowContext flowContext = context.flowContext();
        if (flowContext == null) {
            return List.of();
        }
        String addendum = chatFlowEngineService.buildSystemPromptAddendum(
                flowContext.flow(), flowContext.state(), flowContext.graph());
        if (!StringUtils.hasText(addendum)) {
            return List.of();
        }
        String title = flowContext.flow() == null ? null : flowContext.flow().getName();
        return List.of(TurPromptSegment.of(TurPromptSegment.ORIGIN_FLOW,
                title, addendum, TurPromptStability.PER_TURN));
    }
}
