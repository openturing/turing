/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 */
package com.viglet.turing.observability;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.observation.ObservationRegistry;

class TurLlmObservationTest {

    private SimpleMeterRegistry meterRegistry;
    private TurLlmObservation observation;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        ObservationRegistry observationRegistry = ObservationRegistry.create();
        // Bind the observation registry to the meter registry so observations
        // turn into timers (the production wiring is done by Spring Boot).
        observationRegistry.observationConfig().observationHandler(
                new io.micrometer.core.instrument.observation.DefaultMeterObservationHandler(meterRegistry));
        observation = new TurLlmObservation(observationRegistry, meterRegistry);
    }

    @Test
    void observeCall_emitsTimerOnSuccess() {
        Integer result = observation.observeCall("openai", TurMeterNames.OP_CHAT, () -> 42);

        assertEquals(42, result);
        Timer timer = meterRegistry.find(TurMeterNames.LLM_CALLS)
                .tags(Tags.of(
                        TurMeterNames.TAG_PROVIDER, "openai",
                        TurMeterNames.TAG_OPERATION, TurMeterNames.OP_CHAT,
                        TurMeterNames.TAG_STATUS, TurMeterNames.STATUS_SUCCESS))
                .timer();
        assertNotNull(timer, "Timer should be registered with provider/operation/status tags");
        assertEquals(1, timer.count());
    }

    @Test
    void observeCall_emitsErrorStatusOnException() {
        assertThrows(RuntimeException.class, () ->
                observation.observeCall("openai", TurMeterNames.OP_CHAT, () -> {
                    throw new RuntimeException(new IOException("boom"));
                }));

        Timer timer = meterRegistry.find(TurMeterNames.LLM_CALLS)
                .tags(Tags.of(TurMeterNames.TAG_STATUS, TurMeterNames.STATUS_ERROR))
                .timer();
        assertNotNull(timer);
        assertEquals(1, timer.count());
    }

    @Test
    void recordTokens_incrementsBothDirections() {
        observation.recordTokens("openai", "gpt-4o-mini", 100L, 50L);

        Counter inCounter = meterRegistry.find(TurMeterNames.LLM_TOKENS)
                .tags(Tags.of(
                        TurMeterNames.TAG_PROVIDER, "openai",
                        TurMeterNames.TAG_MODEL, "gpt-4o-mini",
                        TurMeterNames.TAG_DIRECTION, TurMeterNames.DIRECTION_IN))
                .counter();
        Counter outCounter = meterRegistry.find(TurMeterNames.LLM_TOKENS)
                .tags(Tags.of(TurMeterNames.TAG_DIRECTION, TurMeterNames.DIRECTION_OUT))
                .counter();
        assertNotNull(inCounter);
        assertNotNull(outCounter);
        assertEquals(100.0, inCounter.count());
        assertEquals(50.0, outCounter.count());
    }

    @Test
    void recordTokens_skipsZeroCounts() {
        observation.recordTokens("openai", "gpt-4o", 0L, 0L);

        Counter counter = meterRegistry.find(TurMeterNames.LLM_TOKENS).counter();
        // Counter should not be registered when both values are zero.
        org.junit.jupiter.api.Assertions.assertNull(counter);
    }

    @Test
    void recordTokens_replacesBlankProviderWithUnknown() {
        observation.recordTokens(null, null, 10L, 0L);

        Counter counter = meterRegistry.find(TurMeterNames.LLM_TOKENS)
                .tags(Tags.of(
                        TurMeterNames.TAG_PROVIDER, "unknown",
                        TurMeterNames.TAG_MODEL, "unknown"))
                .counter();
        assertNotNull(counter);
        assertEquals(10.0, counter.count());
    }
}
