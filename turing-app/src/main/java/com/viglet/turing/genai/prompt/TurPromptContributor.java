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
package com.viglet.turing.genai.prompt;

import java.util.List;

/**
 * Block AL / §XXXV.1 — SPI for one source of the turn's system prompt. Each
 * contributor emits zero or more {@link TurPromptSegment}s from a
 * {@link TurPromptAssemblyContext}; {@link TurPromptAssemblyPipeline} runs the
 * ordered contributor list <b>once</b> and joins the segments into the exact
 * system message both the runtime and the Live Preview render — the single
 * source of truth that retires the parallel "rebuild a lookalike" path.
 *
 * <p>The built-in contributors (persona, agent-base, MCP, capability, skill,
 * flow) are ordered so that, with the empty/default pipeline, the joined output
 * is byte-identical to the legacy {@code TurPersonaPromptComposer} concatenation.
 * Adding a new prompt source becomes "register a contributor" (T615) rather than
 * editing a monolith plus a parallel rebuild.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
public interface TurPromptContributor {

    /** Persona head (static block + few-shot) — wraps the body. */
    int ORDER_PERSONA = 100;
    /** T718 — persona grounded-knowledge block — right after the persona head. */
    int ORDER_GROUNDING = 150;
    /** Agent base / RAG-override system prompt — first body segment. */
    int ORDER_AGENT_BASE = 200;
    /** MCP server instructions. */
    int ORDER_MCP = 300;
    /** Opt-in capability guidance (render / answer-as-app / co-browse / memory / action-widget). */
    int ORDER_CAPABILITY = 400;
    /** T787 — opt-in knowledge-cutoff disclosure line, right after the capability blocks. */
    int ORDER_KNOWLEDGE_CUTOFF = 450;
    /** Skill progressive-disclosure catalog. */
    int ORDER_SKILL = 500;
    /** Active chat-flow node addendum — last body segment. */
    int ORDER_FLOW = 600;

    /**
     * Contribute this source's segments for the turn, or an empty list when the
     * source does not apply (disabled flag, no persona, blank block). Must never
     * return {@code null}. Implementations are pure string builders — no
     * mutation of the context, no side effects.
     */
    List<TurPromptSegment> contribute(TurPromptAssemblyContext context);

    /**
     * Ascending assembly order. Lower runs earlier (closer to the top of the
     * prompt). Defaults to {@link #ORDER_AGENT_BASE}.
     */
    default int order() {
        return ORDER_AGENT_BASE;
    }
}
