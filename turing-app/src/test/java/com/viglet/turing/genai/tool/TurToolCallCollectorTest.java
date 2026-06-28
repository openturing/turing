/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.tool;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

class TurToolCallCollectorTest {

    @Test
    void recordsTerminalRowOnlyOnEnd() {
        TurToolCallCollector collector = new TurToolCallCollector();
        collector.onStart("c1", "search", "{q}");
        assertThat(collector.isEmpty()).isTrue(); // start alone is not a trace row

        collector.onEnd("c1", "search", "{q}", true, 42L);
        List<TurChatToolCall> snapshot = collector.snapshot();
        assertThat(snapshot).hasSize(1);
        TurChatToolCall row = snapshot.get(0);
        assertThat(row.callId()).isEqualTo("c1");
        assertThat(row.name()).isEqualTo("search");
        assertThat(row.phase()).isEqualTo("end");
        assertThat(row.argsSummary()).isEqualTo("{q}");
        assertThat(row.status()).isEqualTo("ok");
        assertThat(row.durationMs()).isEqualTo(42L);
    }

    @Test
    void failedCallRecordsErrorStatus() {
        TurToolCallCollector collector = new TurToolCallCollector();
        collector.onEnd("c2", "boom", "{}", false, 10L);
        assertThat(collector.snapshot().get(0).status()).isEqualTo("error");
    }

    @Test
    void liveListenerReceivesStartThenEnd() {
        TurToolCallCollector collector = new TurToolCallCollector();
        List<TurChatToolCall> live = new ArrayList<>();
        collector.attachListener(live::add);

        collector.onStart("c1", "search", "{q}");
        collector.onEnd("c1", "search", "{q}", true, 5L);

        assertThat(live).extracting(TurChatToolCall::phase).containsExactly("start", "end");
        assertThat(live.get(0).argsSummary()).isEqualTo("{q}");
        assertThat(live.get(1).durationMs()).isEqualTo(5L);
        assertThat(live.get(1).argsSummary()).isNull(); // args only on the start event
    }

    @Test
    void withoutListenerNothingIsEmittedButTraceStillRecorded() {
        TurToolCallCollector collector = new TurToolCallCollector();
        collector.onStart("c1", "t", "a");
        collector.onEnd("c1", "t", "a", true, 1L);
        assertThat(collector.snapshot()).hasSize(1);
    }

    @Test
    void listenerThrowDoesNotBreakRecording() {
        TurToolCallCollector collector = new TurToolCallCollector();
        collector.attachListener(call -> {
            throw new IllegalStateException("sink closed");
        });
        collector.onStart("c1", "t", "a");
        collector.onEnd("c1", "t", "a", true, 1L);
        assertThat(collector.snapshot()).hasSize(1); // trace row survived the throwing sink
    }
}
