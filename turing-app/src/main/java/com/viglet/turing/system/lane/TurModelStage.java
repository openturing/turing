/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.system.lane;

/**
 * T517 / §XXVIII.13 — a concrete LLM <em>stage</em> in the platform pipeline,
 * fixed-mapped to a {@link TurModelLane}. Stages are code-defined (each is a real
 * call site); the admin only configures the three lanes, and every stage rides
 * its lane's binding. This keeps the per-stage routing declarative — config, not
 * code — while still letting the admin reason in the coarse fast/reasoning/cheap
 * vocabulary the design intends.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
public enum TurModelStage {
    /** Autocomplete suggestions — interactive, latency-critical. */
    AUTOCOMPLETE(TurModelLane.FAST),
    /** Spell correction — interactive. */
    SPELL(TurModelLane.FAST),
    /** Query rewriting / expansion — interactive. */
    QUERY_REWRITE(TurModelLane.FAST),
    /** Chat-flow router decisions — quality-sensitive. */
    ROUTER(TurModelLane.REASONING),
    /** RAG candidate reranking — quality-sensitive. */
    RERANK(TurModelLane.REASONING),
    /** LLM-as-judge guardrails — quality-sensitive. */
    JUDGE(TurModelLane.REASONING),
    /** Nightly / background summarization + insights — non-interactive. */
    BACKGROUND_SUMMARIZATION(TurModelLane.CHEAP);

    private final TurModelLane lane;

    TurModelStage(TurModelLane lane) {
        this.lane = lane;
    }

    /** The lane this stage rides; the admin binds the lane, the stage follows. */
    public TurModelLane lane() {
        return lane;
    }
}
