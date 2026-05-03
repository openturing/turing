/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 */
package com.viglet.turing.observability;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.observation.ObservationRegistry;

class TurSearchObservationTest {

    private SimpleMeterRegistry meterRegistry;
    private TurSearchObservation observation;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        ObservationRegistry observationRegistry = ObservationRegistry.create();
        observationRegistry.observationConfig().observationHandler(
                new io.micrometer.core.instrument.observation.DefaultMeterObservationHandler(meterRegistry));
        observation = new TurSearchObservation(observationRegistry);
    }

    @Test
    void observeCall_recordsSuccessfulSolrQuery() {
        String result = observation.observeCall("solr", "retrieveSearchResults", () -> "ok");

        assertEquals("ok", result);
        Timer timer = meterRegistry.find(TurMeterNames.SEARCH_CALLS)
                .tags(Tags.of(
                        TurMeterNames.TAG_ENGINE, "solr",
                        TurMeterNames.TAG_OPERATION, "retrieveSearchResults",
                        TurMeterNames.TAG_STATUS, TurMeterNames.STATUS_SUCCESS))
                .timer();
        assertNotNull(timer);
        assertEquals(1, timer.count());
    }

    @Test
    void observeCall_recordsErrorStatusWhenPluginThrows() {
        assertThrows(IllegalStateException.class, () ->
                observation.observeCall("elasticsearch", "indexDocument", () -> {
                    throw new IllegalStateException("connection refused");
                }));

        Timer timer = meterRegistry.find(TurMeterNames.SEARCH_CALLS)
                .tags(Tags.of(
                        TurMeterNames.TAG_ENGINE, "elasticsearch",
                        TurMeterNames.TAG_STATUS, TurMeterNames.STATUS_ERROR))
                .timer();
        assertNotNull(timer);
        assertEquals(1, timer.count());
    }
}
