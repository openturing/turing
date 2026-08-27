/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *          http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.viglet.turing.client.sn.inventory;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * T1035 / §XX.20 — the two comparisons an audit makes. Both answer "no" when
 * either side is unknown, which is the whole discipline: reporting drift the
 * files do not prove is how an audit republishes what was already correct.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
class TurSNInventoryEntryTest {

    private static TurSNInventoryEntry entry(Long version, String checksum) {
        return new TurSNInventoryEntry("doc-1", "Page", version, "modification_date", checksum);
    }

    @Test
    @DisplayName("Two different checksums are a difference")
    void differentChecksumsDiffer() {
        assertThat(entry(1L, "sum-a").checksumDiffers("sum-b")).isTrue();
    }

    @Test
    @DisplayName("The same checksum is not")
    void identicalChecksumsMatch() {
        assertThat(entry(1L, "sum-a").checksumDiffers("sum-a")).isFalse();
    }

    @Test
    @DisplayName("A missing checksum on either side is unknown, never different")
    void unknownChecksumIsNotADifference() {
        assertThat(entry(1L, null).checksumDiffers("sum-b")).isFalse();
        assertThat(entry(1L, "sum-a").checksumDiffers(null)).isFalse();
        assertThat(entry(1L, null).checksumDiffers(null)).isFalse();
    }

    @Test
    @DisplayName("An index older than the source is stale")
    void olderIndexIsStale() {
        assertThat(entry(1_000L, null).staleAgainst(2_000L)).isTrue();
    }

    @Test
    @DisplayName("An index at or ahead of the source is not stale")
    void sameOrNewerIndexIsNotStale() {
        assertThat(entry(2_000L, null).staleAgainst(2_000L)).isFalse();
        assertThat(entry(3_000L, null).staleAgainst(2_000L)).isFalse();
    }

    @Test
    @DisplayName("A missing version on either side is unknown, never stale")
    void unknownVersionIsNotStale() {
        assertThat(entry(null, null).staleAgainst(2_000L)).isFalse();
        assertThat(entry(1_000L, null).staleAgainst(null)).isFalse();
    }

    @Test
    @DisplayName("An empty page from a supported engine is an empty index; unsupported is not")
    void emptyIndexIsDistinguishable() {
        var supported = new TurSNInventoryPage("s1", "Catalog", "en-US", java.util.List.of("en-US"),
                "aem", java.util.List.of(), null, false, 0, true);
        var unsupported = new TurSNInventoryPage("s1", "Catalog", "en-US",
                java.util.List.of("en-US"), "aem", java.util.List.of(), null, false, -1, false);

        assertThat(supported.isEmptyIndex()).isTrue();
        assertThat(unsupported.isEmptyIndex()).isFalse();
    }
}
