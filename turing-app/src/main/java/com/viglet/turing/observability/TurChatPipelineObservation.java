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
}
