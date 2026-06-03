/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.flow.router;

/**
 * T89 / §VII.10.f — which mechanism produced a chat-flow router decision.
 *
 * <p>The router runs a three-phase cascade in
 * {@code TurChatFlowEngineService.selectActiveFlow}: a deterministic
 * keyword (Lucene {@code MoreLikeThis}) pass, then a cached LLM decision,
 * then a full LLM call. This enum tags a {@link TurChatFlowRouterDecision}
 * with the phase that actually chose the winner, so an operator triaging a
 * complaint can tell "the keyword scorer committed to B2C" apart from
 * "the LLM picked B2C" apart from "this was a replay of an earlier
 * identical message".
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
public enum TurChatFlowRouterMethod {

    /**
     * The deterministic keyword scorer dominated the runner-up by the
     * configured ratio; the LLM was skipped entirely.
     */
    PROCEDURAL,

    /** A full LLM router round-trip chose the winner. */
    LLM,

    /**
     * The LLM router decision was served from the
     * {@code turChatFlowRouterDecision} cache (same agent + same wording as
     * an earlier turn) — no LLM round-trip this turn.
     */
    LLM_CACHE,

    /**
     * Neither the keyword scorer nor the LLM matched any flow — the message
     * is off-topic for every candidate's trigger description.
     */
    NONE
}
