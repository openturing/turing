/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.service.chatanalytics;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;

import com.viglet.turing.genai.tool.TurChatToolCall;

class TurToolCallTraceServiceTest {

    private static TurChatToolCall call(String id, String name) {
        return TurChatToolCall.completed(id, name, "{}", true, 1L);
    }

    @Test
    void recordsAndReturnsInOrder() {
        TurToolCallTraceService svc = new TurToolCallTraceService();
        svc.record("conv1", List.of(call("a", "t1"), call("b", "t2")));
        svc.record("conv1", List.of(call("c", "t3")));

        assertThat(svc.getTrace("conv1"))
                .extracting(TurChatToolCall::callId)
                .containsExactly("a", "b", "c");
    }

    @Test
    void unknownConversationIsEmpty() {
        TurToolCallTraceService svc = new TurToolCallTraceService();
        assertThat(svc.getTrace("nope")).isEmpty();
        assertThat(svc.getTrace(null)).isEmpty();
        assertThat(svc.getTrace("  ")).isEmpty();
    }

    @Test
    void blankIdAndEmptyBatchAreNoOps() {
        TurToolCallTraceService svc = new TurToolCallTraceService();
        svc.record("", List.of(call("a", "t")));
        svc.record("conv", List.of());
        svc.record("conv", null);
        assertThat(svc.getTrace("conv")).isEmpty();
    }

    @Test
    void perConversationRingDropsOldestPastCap() {
        TurToolCallTraceService svc = new TurToolCallTraceService();
        int over = TurToolCallTraceService.MAX_CALLS_PER_CONVERSATION + 5;
        IntStream.range(0, over)
                .forEach(i -> svc.record("conv", List.of(call("id" + i, "t"))));

        List<TurChatToolCall> trace = svc.getTrace("conv");
        assertThat(trace).hasSize(TurToolCallTraceService.MAX_CALLS_PER_CONVERSATION);
        // oldest ("id0".."id4") dropped; newest retained
        assertThat(trace.get(trace.size() - 1).callId()).isEqualTo("id" + (over - 1));
        assertThat(trace.get(0).callId()).isEqualTo("id5");
    }

    @Test
    void evictRemovesTrace() {
        TurToolCallTraceService svc = new TurToolCallTraceService();
        svc.record("conv", List.of(call("a", "t")));
        svc.evict("conv");
        assertThat(svc.getTrace("conv")).isEmpty();
    }

    @Test
    void lruEvictsLeastRecentlyTouchedConversation() {
        TurToolCallTraceService svc = new TurToolCallTraceService();
        int over = TurToolCallTraceService.MAX_CONVERSATIONS + 1;
        IntStream.range(0, over)
                .forEach(i -> svc.record("conv" + i, List.of(call("a", "t"))));
        // conv0 was the eldest and never touched again → evicted
        assertThat(svc.getTrace("conv0")).isEmpty();
        assertThat(svc.getTrace("conv" + (over - 1))).hasSize(1);
    }
}
