/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.api.page;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;

import org.junit.jupiter.api.Test;

/**
 * T645 / §XXXVII.7 + T648 / §XXXVII.10 — the page ZIP-import guards: Zip-Slip
 * traversal detection and the bounded per-entry read (zip-bomb defense).
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
class TurPageAPITest {

    @Test
    void hasTraversalSegmentDetectsEscapes() {
        assertThat(TurPageAPI.hasTraversalSegment("../evil.js")).isTrue();
        assertThat(TurPageAPI.hasTraversalSegment("a/../../b")).isTrue();
        assertThat(TurPageAPI.hasTraversalSegment("..\\evil")).isTrue();
    }

    @Test
    void hasTraversalSegmentAllowsNormalNames() {
        assertThat(TurPageAPI.hasTraversalSegment("index.html")).isFalse();
        assertThat(TurPageAPI.hasTraversalSegment("assets/app.js")).isFalse();
    }

    @Test
    void readEntryBoundedReturnsContentUnderCap() throws Exception {
        byte[] data = "hello".getBytes();
        byte[] read = TurPageAPI.readEntryBounded(new ByteArrayInputStream(data), 1024, "x.txt");
        assertThat(read).isEqualTo(data);
    }

    @Test
    void readEntryBoundedAbortsOverCap() {
        byte[] data = new byte[2048];
        assertThatThrownBy(() ->
                TurPageAPI.readEntryBounded(new ByteArrayInputStream(data), 1024, "bomb.bin"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("maximum size");
    }

    @Test
    void readEntryBoundedUnlimitedWhenCapNonPositive() throws Exception {
        byte[] data = new byte[4096];
        byte[] read = TurPageAPI.readEntryBounded(new ByteArrayInputStream(data), 0, "big.bin");
        assertThat(read).hasSize(4096);
    }
}
