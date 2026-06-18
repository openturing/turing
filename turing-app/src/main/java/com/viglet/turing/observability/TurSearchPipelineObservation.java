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

import java.util.function.Supplier;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

/**
 * Records latency of the SN search pipeline stages around the search engine
 * call (snapshot lookup, response assembly, facet rendering, etc.). Output
 * lands in {@link TurMeterNames#SEARCH_PIPELINE} with a {@code stage} tag, so
 * dashboards can split a request into its Java sub-stages without parsing
 * application logs.
 *
 * <p>The {@link MeterRegistry} dependency is optional — tests that bypass the
 * full observability stack get a no-op registry, keeping unit tests fast.
 *
 * @author Alexandre Oliveira
 * @since 2026.2.7
 */
@Component
public class TurSearchPipelineObservation {

    private final MeterRegistry meterRegistry;

    public TurSearchPipelineObservation(@Autowired(required = false) MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry != null ? meterRegistry : new SimpleMeterRegistry();
    }

    /**
     * Times {@code supplier.get()} as a {@link TurMeterNames#SEARCH_PIPELINE}
     * sample tagged with the given {@code stage} and a status indicating
     * whether the supplier completed normally.
     */
    // S6213: record is the natural verb for this timing helper and is a valid
    // identifier (only a contextual keyword). Renaming this public method would
    // ripple through every search call site for a naming nit, so it is kept.
    @SuppressWarnings("java:S6213")
    public <T> T record(String stage, Supplier<T> supplier) {
        Timer.Sample sample = Timer.start(meterRegistry);
        String status = TurMeterNames.STATUS_SUCCESS;
        try {
            return supplier.get();
        } catch (RuntimeException e) {
            status = TurMeterNames.STATUS_ERROR;
            throw e;
        } finally {
            sample.stop(meterRegistry.timer(TurMeterNames.SEARCH_PIPELINE,
                    Tags.of(TurMeterNames.TAG_STAGE, stage,
                            TurMeterNames.TAG_STATUS, status)));
        }
    }

    /**
     * Increments the snapshot cache outcome counter
     * ({@link TurMeterNames#SEARCH_SNAPSHOT_CACHE}) — used by the snapshot
     * service to record hits, misses and listener evictions.
     */
    public void recordSnapshotOutcome(String outcome) {
        meterRegistry.counter(TurMeterNames.SEARCH_SNAPSHOT_CACHE,
                TurMeterNames.TAG_OUTCOME, outcome).increment();
    }
}
