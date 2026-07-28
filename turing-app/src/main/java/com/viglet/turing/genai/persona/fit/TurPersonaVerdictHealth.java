/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.persona.fit;

import java.util.concurrent.atomic.AtomicLong;

import org.springframework.stereotype.Component;

import com.viglet.turing.system.TurVerdictSource;

/**
 * T829 / §LXI.5 (Block BN) — how the persona verdicts are <em>actually</em> being
 * produced, counted since boot.
 *
 * <p>Both LLM verdicts in this feature degrade quietly: an unreadable fit verdict
 * becomes a readability-only score with a plausible number, and an unreadable
 * adjustment verdict becomes the deterministic list. Individually each degradation
 * is invisible; in aggregate it is the difference between "the LLM is grading this
 * portfolio" and "the LLM has not successfully graded anything in a week". Counting
 * makes that answerable, and lets the studio say heuristic-only <em>because the
 * schema call failed</em> rather than leaving the operator to guess.
 *
 * <p>Process-local and monotonic — this is an observability counter, not a metric
 * store. In a cluster each node reports its own tallies.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Component
public class TurPersonaVerdictHealth {

    private final AtomicLong providerSchema = new AtomicLong();
    private final AtomicLong promptJson = new AtomicLong();
    private final AtomicLong cached = new AtomicLong();
    private final AtomicLong unavailable = new AtomicLong();
    private final AtomicLong unreadable = new AtomicLong();

    /** Record one verdict outcome. */
    public void record(TurVerdictSource source) {
        if (source == null) {
            return;
        }
        switch (source) {
            case PROVIDER_SCHEMA -> providerSchema.incrementAndGet();
            case PROMPT_JSON -> promptJson.incrementAndGet();
            case CACHE -> cached.incrementAndGet();
            case UNAVAILABLE -> unavailable.incrementAndGet();
            case UNREADABLE -> unreadable.incrementAndGet();
        }
    }

    public Snapshot snapshot() {
        return new Snapshot(providerSchema.get(), promptJson.get(), cached.get(),
                unavailable.get(), unreadable.get());
    }

    /** Reset — for tests and for an operator who wants a fresh window. */
    public void reset() {
        providerSchema.set(0);
        promptJson.set(0);
        cached.set(0);
        unavailable.set(0);
        unreadable.set(0);
    }

    /**
     * The tallies as returned by the API.
     *
     * @param providerSchema the provider enforced the schema — the healthy path
     * @param promptJson     a plain chat call answered and the JSON was scraped out
     * @param cached         served from the LLM cache
     * @param unavailable    no LLM configured, or the call failed outright
     * @param unreadable     the model answered and the verdict could not be decoded
     *                       — the degradation this counter exists for
     */
    public record Snapshot(long providerSchema, long promptJson, long cached, long unavailable,
            long unreadable) {

        /** Verdicts that reached an LLM at all (cache excluded). */
        public long llmAnswered() {
            return providerSchema + promptJson + unreadable;
        }

        /**
         * Share of LLM replies that could not be decoded, {@code 0} when none were
         * attempted. The number to watch after changing model or prompt.
         */
        public double unreadableRate() {
            long answered = llmAnswered();
            return answered == 0 ? 0 : Math.round((unreadable * 10_000.0) / answered) / 100.0;
        }
    }
}
