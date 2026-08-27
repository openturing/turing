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
package com.viglet.turing.plugins.se;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * T1029 / §XX.16 — the cursor and the {@code hasMore} flag are derived from the
 * rows, so no engine implementation has to remember to set them, and an empty
 * page from an engine that cannot answer must not read as an empty index.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
class TurSEInventoryPageTest {

    private static TurSEInventoryEntry entry(String id) {
        return new TurSEInventoryEntry(id, "Page", 1L, "modification_date");
    }

    @Test
    @DisplayName("The cursor is the last row's id")
    void cursorIsTheLastId() {
        var page = TurSEInventoryPage.of(List.of(entry("a"), entry("b"), entry("c")), 3);

        assertThat(page.nextCursor()).isEqualTo("c");
        assertThat(page.supported()).isTrue();
    }

    @Test
    @DisplayName("hasMore is true only when the engine counted rows beyond the page")
    void hasMoreFollowsTheRemainingCount() {
        assertThat(TurSEInventoryPage.of(List.of(entry("a"), entry("b")), 10).hasMore()).isTrue();
        assertThat(TurSEInventoryPage.of(List.of(entry("a"), entry("b")), 2).hasMore()).isFalse();
    }

    @Test
    @DisplayName("A page with no rows has no cursor and nothing more to read")
    void emptyPageEndsTheSweep() {
        var page = TurSEInventoryPage.of(List.of(), 0);

        assertThat(page.entries()).isEmpty();
        assertThat(page.nextCursor()).isNull();
        assertThat(page.hasMore()).isFalse();
        assertThat(page.supported()).isTrue();
    }

    @Test
    @DisplayName("An unsupported engine is distinguishable from an empty index")
    void unsupportedIsNotEmpty() {
        var unsupported = TurSEInventoryPage.unsupported();
        var empty = TurSEInventoryPage.empty();

        assertThat(unsupported.supported()).isFalse();
        assertThat(unsupported.remaining()).isEqualTo(-1L);
        assertThat(empty.supported()).isTrue();
        assertThat(empty.remaining()).isZero();
    }

    @Test
    @DisplayName("A negative engine count on an empty page is normalised to zero")
    void negativeRemainingOnEmptyPageIsZero() {
        assertThat(TurSEInventoryPage.of(List.of(), -1).remaining()).isZero();
    }

    @Test
    @DisplayName("The rows are copied, so a caller's list cannot mutate the page")
    void entriesAreCopied() {
        var mutable = new java.util.ArrayList<>(List.of(entry("a")));
        var page = TurSEInventoryPage.of(mutable, 1);
        mutable.add(entry("b"));

        assertThat(page.entries()).hasSize(1);
    }
}
