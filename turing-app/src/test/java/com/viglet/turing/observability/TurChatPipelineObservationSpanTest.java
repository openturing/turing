/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.observability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.core.instrument.observation.DefaultMeterObservationHandler;
import io.micrometer.observation.ObservationRegistry;

/**
 * T129 / §IX.9.b — verifies the span-emitting variant of
 * {@link TurChatPipelineObservation#record(String, Supplier)} still records a
 * timer identical to the metrics-only path (the OTel span itself is produced by
 * the framework bridge, which isn't wired in a unit test) and that the feature
 * is gated by {@code turing.observability.spans.enabled}.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
class TurChatPipelineObservationSpanTest {

    private TurChatPipelineObservation withSpans(SimpleMeterRegistry registry, boolean enabled) {
        ObservationRegistry observationRegistry = ObservationRegistry.create();
        observationRegistry.observationConfig()
                .observationHandler(new DefaultMeterObservationHandler(registry));
        return new TurChatPipelineObservation(registry, observationRegistry, enabled);
    }

    @Test
    void record_withSpansEnabled_recordsTimerWithStageAndStatusTags() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        TurChatPipelineObservation obs = withSpans(registry, true);

        String result = obs.record(TurMeterNames.STAGE_CHAT_POST, () -> "ok");

        assertThat(result).isEqualTo("ok");
        Timer timer = registry.find(TurMeterNames.CHAT_PIPELINE)
                .tags(Tags.of(TurMeterNames.TAG_STAGE, TurMeterNames.STAGE_CHAT_POST,
                        TurMeterNames.TAG_STATUS, TurMeterNames.STATUS_SUCCESS))
                .timer();
        assertThat(timer).isNotNull();
        assertThat(timer.count()).isEqualTo(1);
    }

    @Test
    void record_withSpansEnabled_tagsErrorStatusAndRethrows() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        TurChatPipelineObservation obs = withSpans(registry, true);

        assertThatThrownBy(() -> obs.record(TurMeterNames.STAGE_CHAT_ADVANCE, () -> {
            throw new IllegalStateException("boom");
        })).isInstanceOf(IllegalStateException.class);

        Timer timer = registry.find(TurMeterNames.CHAT_PIPELINE)
                .tags(Tags.of(TurMeterNames.TAG_STAGE, TurMeterNames.STAGE_CHAT_ADVANCE,
                        TurMeterNames.TAG_STATUS, TurMeterNames.STATUS_ERROR))
                .timer();
        assertThat(timer).isNotNull();
        assertThat(timer.count()).isEqualTo(1);
    }

    @Test
    void record_withSpansDisabled_stillRecordsTimer() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        TurChatPipelineObservation obs = withSpans(registry, false);

        obs.record(TurMeterNames.STAGE_CHAT_SETUP, () -> 1);

        Timer timer = registry.find(TurMeterNames.CHAT_PIPELINE)
                .tags(Tags.of(TurMeterNames.TAG_STAGE, TurMeterNames.STAGE_CHAT_SETUP,
                        TurMeterNames.TAG_STATUS, TurMeterNames.STATUS_SUCCESS))
                .timer();
        assertThat(timer).isNotNull();
        assertThat(timer.count()).isEqualTo(1);
    }
}
