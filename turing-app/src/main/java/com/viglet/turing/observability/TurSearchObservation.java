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

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;

/**
 * Records search engine plugin call latency and emits trace spans. Tags use
 * stable {@link TurMeterNames} constants — dashboards and alerts depend on
 * them, so do not rename without migrating consumers.
 *
 * @author Alexandre Oliveira
 * @since 2026.2.6
 */
@Component
public class TurSearchObservation {

    private final ObservationRegistry observationRegistry;

    public TurSearchObservation(@Autowired(required = false) ObservationRegistry observationRegistry) {
        this.observationRegistry = observationRegistry != null
                ? observationRegistry
                : ObservationRegistry.NOOP;
    }

    /**
     * Wraps a search engine plugin call (any IO-bound method on
     * {@link com.viglet.turing.plugins.se.TurSearchEnginePlugin}) in a single
     * Observation.
     */
    public <T> T observeCall(String engine, String operation, Supplier<T> supplier) {
        Observation observation = Observation.createNotStarted(TurMeterNames.SEARCH_CALLS, observationRegistry)
                .lowCardinalityKeyValue(TurMeterNames.TAG_ENGINE, engine)
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
}
