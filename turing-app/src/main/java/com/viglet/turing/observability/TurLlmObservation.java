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

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;

/**
 * Records LLM call latency (timer) and token usage (counter) using the unified
 * Micrometer Observation API. The same observation produces a span (via the
 * OTel bridge) and a timer metric, sharing tags for trace ↔ metric correlation.
 *
 * <p>Both registries are optional — tests run without the full observability
 * stack and fall back to no-op behaviour, keeping unit tests fast.
 *
 * @author Alexandre Oliveira
 * @since 2026.2.6
 */
@Component
public class TurLlmObservation {

    private final ObservationRegistry observationRegistry;
    private final MeterRegistry meterRegistry;

    public TurLlmObservation(@Autowired(required = false) ObservationRegistry observationRegistry,
            @Autowired(required = false) MeterRegistry meterRegistry) {
        this.observationRegistry = observationRegistry != null
                ? observationRegistry
                : ObservationRegistry.NOOP;
        this.meterRegistry = meterRegistry;
    }

    /**
     * Wraps an LLM call (chat or embed) in a single Observation. Records
     * latency, attaches {@code provider} and {@code operation} as low-cardinality
     * tags, and stamps {@code status=success|error} once the call completes.
     */
    public <T> T observeCall(String provider, String operation, Supplier<T> supplier) {
        Observation observation = Observation.createNotStarted(TurMeterNames.LLM_CALLS, observationRegistry)
                .lowCardinalityKeyValue(TurMeterNames.TAG_PROVIDER, provider)
                .lowCardinalityKeyValue(TurMeterNames.TAG_OPERATION, operation);
        observation.start();
        try {
            T result = supplier.get();
            observation.lowCardinalityKeyValue(TurMeterNames.TAG_STATUS, TurMeterNames.STATUS_SUCCESS);
            return result;
        } catch (RuntimeException e) {
            observation.lowCardinalityKeyValue(TurMeterNames.TAG_STATUS, TurMeterNames.STATUS_ERROR);
            observation.error(e);
            throw e;
        } finally {
            observation.stop();
        }
    }

    /**
     * Increments the token counter. Called once per chat response with the
     * usage parsed by the existing token-usage service so this layer doesn't
     * duplicate provider-specific extraction logic.
     */
    public void recordTokens(String provider, String model, long inputTokens, long outputTokens) {
        if (meterRegistry == null) {
            return;
        }
        if (inputTokens > 0) {
            Counter.builder(TurMeterNames.LLM_TOKENS)
                    .tag(TurMeterNames.TAG_PROVIDER, nullSafe(provider))
                    .tag(TurMeterNames.TAG_MODEL, nullSafe(model))
                    .tag(TurMeterNames.TAG_DIRECTION, TurMeterNames.DIRECTION_IN)
                    .register(meterRegistry)
                    .increment(inputTokens);
        }
        if (outputTokens > 0) {
            Counter.builder(TurMeterNames.LLM_TOKENS)
                    .tag(TurMeterNames.TAG_PROVIDER, nullSafe(provider))
                    .tag(TurMeterNames.TAG_MODEL, nullSafe(model))
                    .tag(TurMeterNames.TAG_DIRECTION, TurMeterNames.DIRECTION_OUT)
                    .register(meterRegistry)
                    .increment(outputTokens);
        }
    }

    private static String nullSafe(String value) {
        return value == null || value.isBlank() ? "unknown" : value;
    }
}
