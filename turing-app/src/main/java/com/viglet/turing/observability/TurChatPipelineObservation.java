/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed to the Apache Software Foundation (ASF) under one or more contributor license
 * agreements. See the NOTICE file distributed with this work for additional information regarding
 * copyright ownership. The ASF licenses this file to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package com.viglet.turing.observability;

import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

/**
 * Records latency of the agent chat pipeline stages around the LLM call
 * (entity load, persona resolution, tool callback build, flow context, prompt
 * composition, post-processing). Output lands in
 * {@link TurMeterNames#CHAT_PIPELINE} with a {@code stage} tag so dashboards
 * and the {@code TurAgentChatLatencyIT} diagnostic can split a chat turn
 * into the parts that drive different optimizations (DB/cache wins vs.
 * provider round-trip vs. tool-resolution loops).
 *
 * <p>Companion to {@link TurSearchPipelineObservation} — same pattern, same
 * no-op-registry fallback for unit tests that bypass the full observability
 * stack.
 *
 * @author Alexandre Oliveira
 * @since 2026.2.8
 */
@Component
public class TurChatPipelineObservation {

    private final MeterRegistry meterRegistry;

    public TurChatPipelineObservation(@Autowired(required = false) MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry != null ? meterRegistry : new SimpleMeterRegistry();
    }

    /**
     * Times {@code supplier.get()} as a {@link TurMeterNames#CHAT_PIPELINE}
     * sample tagged with the given {@code stage} and a status indicating
     * whether the supplier completed normally.
     */
    public <T> T record(String stage, Supplier<T> supplier) {
        Timer.Sample sample = Timer.start(meterRegistry);
        String status = TurMeterNames.STATUS_SUCCESS;
        try {
            return supplier.get();
        } catch (RuntimeException e) {
            status = TurMeterNames.STATUS_ERROR;
            throw e;
        } finally {
            sample.stop(meterRegistry.timer(TurMeterNames.CHAT_PIPELINE,
                    Tags.of(TurMeterNames.TAG_STAGE, stage,
                            TurMeterNames.TAG_STATUS, status)));
        }
    }

    /**
     * Records a pre-measured duration as a {@link TurMeterNames#CHAT_PIPELINE}
     * sample. Use this when the stage spans across thread boundaries (e.g.
     * setup runs on the request thread but the LLM call runs inside a
     * {@code Mono.fromCallable} on {@code Schedulers.boundedElastic()}) and a
     * single {@code Supplier} can't naturally enclose it.
     */
    public void recordMillis(String stage, long millis) {
        meterRegistry.timer(TurMeterNames.CHAT_PIPELINE,
                Tags.of(TurMeterNames.TAG_STAGE, stage,
                        TurMeterNames.TAG_STATUS, TurMeterNames.STATUS_SUCCESS))
                .record(millis, TimeUnit.MILLISECONDS);
    }

    /**
     * Returns the underlying {@link MeterRegistry} so diagnostic tests can
     * read recorded timer values without re-resolving the bean themselves.
     * Production callers should use {@link #record(String, Supplier)} or
     * {@link #recordMillis(String, long)} instead.
     */
    public MeterRegistry getMeterRegistry() {
        return meterRegistry;
    }

    /**
     * T32 / §IV.7 — records one sample of the T30 conversation-relevance
     * retrieval: pool size + retrieved hits + duration, tagged by outcome
     * ({@code hits} / {@code no-hits} / {@code bypass}).
     *
     * <p>On {@code bypass} the pool/hits distribution samples are skipped
     * (we don't want zero-population samples dragging the histogram down);
     * the timer sample is still recorded so dashboards can see how often
     * the bypass path fires and how cheap it is.
     *
     * @param poolSize  size of the older-turn candidate pool the retriever
     *                  scored — pass {@code -1} for the bypass path.
     * @param hits      number of older turns prepended — pass {@code -1}
     *                  for the bypass path.
     * @param millis    wall-clock duration of the retrieval call.
     * @param outcome   one of {@link TurMeterNames#OUTCOME_HITS},
     *                  {@link TurMeterNames#OUTCOME_NO_HITS},
     *                  {@link TurMeterNames#OUTCOME_BYPASS}.
     * @since 2026.3.1
     */
    public void recordMemoryRetrieval(int poolSize, int hits, long millis, String outcome) {
        Tags timerTags = Tags.of(TurMeterNames.TAG_OUTCOME, outcome);
        meterRegistry.timer(TurMeterNames.CHAT_MEMORY_RETRIEVAL, timerTags)
                .record(millis, TimeUnit.MILLISECONDS);
        if (poolSize >= 0) {
            // No base unit on the DistributionSummary so the Prometheus
            // renderer keeps the metric name verbatim (with .baseUnit set,
            // Micrometer appends the unit token, producing awkward names
            // like `..._pool_turns_count` that drift from
            // {@link TurMeterNames#CHAT_MEMORY_RETRIEVAL_POOL}).
            DistributionSummary.builder(TurMeterNames.CHAT_MEMORY_RETRIEVAL_POOL)
                    .register(meterRegistry)
                    .record(poolSize);
        }
        if (hits >= 0) {
            DistributionSummary.builder(TurMeterNames.CHAT_MEMORY_RETRIEVAL_HITS)
                    .register(meterRegistry)
                    .record(hits);
        }
    }

    /**
     * T32 / §IV.7 — records one sample of the T29 tool pre-filter pass:
     * input / output tool counts + duration + bypass reason. The reason
     * value is also pushed to the {@link TurMeterNames#CHAT_TOOL_PREFILTER_BYPASS}
     * counter so dashboards can see the engagement breakdown.
     *
     * @param inputCount   tool callbacks fed into the filter (full pool).
     * @param outputCount  tool callbacks remaining after pruning. When the
     *                     filter is a no-op this equals {@code inputCount}.
     * @param millis       wall-clock duration of the {@code filter(...)}
     *                     call (including index build + BM25 scoring).
     * @param reason       one of {@link TurMeterNames#REASON_DISABLED},
     *                     {@link TurMeterNames#REASON_BELOW_THRESHOLD},
     *                     {@link TurMeterNames#REASON_BLANK_QUERY},
     *                     {@link TurMeterNames#REASON_ALREADY_COVERED},
     *                     {@link TurMeterNames#REASON_CAP_COVERS_POOL}, or
     *                     {@link TurMeterNames#REASON_ENGAGED}.
     * @since 2026.3.1
     */
    public void recordToolPrefilter(int inputCount, int outputCount, long millis, String reason) {
        String outcome = TurMeterNames.REASON_ENGAGED.equals(reason)
                ? TurMeterNames.OUTCOME_FILTERED
                : TurMeterNames.OUTCOME_PASSTHROUGH;
        meterRegistry.timer(TurMeterNames.CHAT_TOOL_PREFILTER,
                Tags.of(TurMeterNames.TAG_OUTCOME, outcome))
                .record(millis, TimeUnit.MILLISECONDS);
        meterRegistry.counter(TurMeterNames.CHAT_TOOL_PREFILTER_BYPASS,
                Tags.of(TurMeterNames.TAG_REASON, reason))
                .increment();
        DistributionSummary.builder(TurMeterNames.CHAT_TOOL_PREFILTER_INPUT)
                .baseUnit("tools")
                .register(meterRegistry)
                .record(inputCount);
        DistributionSummary.builder(TurMeterNames.CHAT_TOOL_PREFILTER_OUTPUT)
                .baseUnit("tools")
                .register(meterRegistry)
                .record(outputCount);
    }
}
