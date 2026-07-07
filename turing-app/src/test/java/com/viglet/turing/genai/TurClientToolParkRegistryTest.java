/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.viglet.turing.genai.TurClientToolParkRegistry.ParkedClientTurn;

class TurClientToolParkRegistryTest {

    private static ParkedClientTurn park(String conv, String callId, String tool, long expiresAt) {
        // Heavy resume fields (turn/chatModel/options/...) aren't exercised by the
        // registry's park/take/sweep logic — null is fine for these unit tests.
        return new ParkedClientTurn(conv, callId, tool, "{}",
                null, null, null, null, null, null, null, expiresAt);
    }

    private static long future() {
        return System.currentTimeMillis() + 60_000L;
    }

    @Test
    void parkThenTakeReturnsItOnce() {
        TurClientToolParkRegistry reg = new TurClientToolParkRegistry();
        reg.park(park("c1", "call1", "loc", future()));

        ParkedClientTurn taken = reg.take("c1", "call1");
        assertThat(taken).isNotNull();
        assertThat(taken.toolName()).isEqualTo("loc");
        // take() removes — a second take is null (no double-resume).
        assertThat(reg.take("c1", "call1")).isNull();
    }

    @Test
    void takeUnknownIsNull() {
        TurClientToolParkRegistry reg = new TurClientToolParkRegistry();
        assertThat(reg.take("c1", "nope")).isNull();
        assertThat(reg.take(null, "x")).isNull();
    }

    @Test
    void expiredParkIsNotReturnedAndSweepEvictsIt() {
        TurClientToolParkRegistry reg = new TurClientToolParkRegistry();
        reg.park(park("c1", "old", "loc", System.currentTimeMillis() - 1));
        // take() refuses an already-expired park
        assertThat(reg.take("c1", "old")).isNull();

        reg.park(park("c2", "old2", "loc", System.currentTimeMillis() - 1));
        reg.sweepExpired();
        assertThat(reg.size()).isZero();
    }

    @Test
    void sweepKeepsLiveParks() {
        TurClientToolParkRegistry reg = new TurClientToolParkRegistry();
        reg.park(park("c1", "live", "loc", future()));
        reg.sweepExpired();
        assertThat(reg.size()).isEqualTo(1);
        assertThat(reg.take("c1", "live")).isNotNull();
    }
}
