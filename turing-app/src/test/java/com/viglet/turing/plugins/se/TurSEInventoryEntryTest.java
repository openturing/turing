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

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.viglet.turing.commons.sn.field.TurSNFieldName;

/**
 * T1029 / §XX.16 — the version each engine hands over is a {@code Date} (Solr),
 * a {@code long} (Lucene) or an ISO-8601 string (Elasticsearch); an audit
 * comparing versions across sites can only do so if all three normalise to the
 * same number.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
class TurSEInventoryEntryTest {

    private static final long EPOCH_MS = 1_594_310_196_000L; // 2020-07-09T15:56:36Z

    @Test
    @DisplayName("Solr's java.util.Date normalises to epoch millis")
    void datesNormalise() {
        var entry = TurSEInventoryEntry.from("doc-1",
                Map.of(TurSNFieldName.MODIFICATION_DATE, new Date(EPOCH_MS)));

        assertThat(entry.version()).isEqualTo(EPOCH_MS);
        assertThat(entry.versionField()).isEqualTo(TurSNFieldName.MODIFICATION_DATE);
    }

    @Test
    @DisplayName("Lucene's stored long normalises to the same epoch millis")
    void numbersNormalise() {
        var entry = TurSEInventoryEntry.from("doc-1",
                Map.of(TurSNFieldName.MODIFICATION_DATE, EPOCH_MS));

        assertThat(entry.version()).isEqualTo(EPOCH_MS);
    }

    @Test
    @DisplayName("Elasticsearch's ISO-8601 string normalises to the same epoch millis")
    void isoStringsNormalise() {
        var entry = TurSEInventoryEntry.from("doc-1",
                Map.of(TurSNFieldName.MODIFICATION_DATE, "2020-07-09T15:56:36Z"));

        assertThat(entry.version()).isEqualTo(EPOCH_MS);
    }

    @Test
    @DisplayName("An ISO-8601 string with fractional seconds still parses")
    void fractionalSecondsParse() {
        var entry = TurSEInventoryEntry.from("doc-1",
                Map.of(TurSNFieldName.MODIFICATION_DATE, "2020-07-09T15:56:36.000Z"));

        assertThat(entry.version()).isEqualTo(EPOCH_MS);
    }

    @Test
    @DisplayName("A multi-valued date takes its first value")
    void multiValuedDatesTakeTheFirst() {
        var entry = TurSEInventoryEntry.from("doc-1",
                Map.of(TurSNFieldName.MODIFICATION_DATE, List.of(EPOCH_MS, 0L)));

        assertThat(entry.version()).isEqualTo(EPOCH_MS);
    }

    @Test
    @DisplayName("modification_date wins over publication_date")
    void modificationDateWins() {
        Map<String, Object> fields = Map.of(
                TurSNFieldName.PUBLICATION_DATE, 1L,
                TurSNFieldName.MODIFICATION_DATE, EPOCH_MS);

        var entry = TurSEInventoryEntry.from("doc-1", fields);

        assertThat(entry.version()).isEqualTo(EPOCH_MS);
        assertThat(entry.versionField()).isEqualTo(TurSNFieldName.MODIFICATION_DATE);
    }

    @Test
    @DisplayName("publication_date is the fallback for sources that stamp only one date")
    void publicationDateIsTheFallback() {
        var entry = TurSEInventoryEntry.from("doc-1",
                Map.of(TurSNFieldName.PUBLICATION_DATE, EPOCH_MS));

        assertThat(entry.version()).isEqualTo(EPOCH_MS);
        assertThat(entry.versionField()).isEqualTo(TurSNFieldName.PUBLICATION_DATE);
    }

    @Test
    @DisplayName("A document with no date reports no version rather than epoch 0")
    void noDateMeansNoVersion() {
        var entry = TurSEInventoryEntry.from("doc-1", Map.of(TurSNFieldName.TYPE, "Page"));

        assertThat(entry.version()).isNull();
        assertThat(entry.versionField()).isNull();
        assertThat(entry.type()).isEqualTo("Page");
    }

    @Test
    @DisplayName("An unparsable date reports no version — never 0, which reads as 1970")
    void unparsableDatesReportNoVersion() {
        var entry = TurSEInventoryEntry.from("doc-1",
                Map.of(TurSNFieldName.MODIFICATION_DATE, "last Tuesday"));

        assertThat(entry.version()).isNull();
        assertThat(entry.versionField()).isNull();
    }

    @Test
    @DisplayName("A blank or absent date value is skipped, and the next field is tried")
    void blankValuesFallThrough() {
        Map<String, Object> fields = new HashMap<>();
        fields.put(TurSNFieldName.MODIFICATION_DATE, "   ");
        fields.put(TurSNFieldName.PUBLICATION_DATE, EPOCH_MS);

        var entry = TurSEInventoryEntry.from("doc-1", fields);

        assertThat(entry.version()).isEqualTo(EPOCH_MS);
        assertThat(entry.versionField()).isEqualTo(TurSNFieldName.PUBLICATION_DATE);
    }

    @Test
    @DisplayName("An empty multi-valued date is not a version")
    void emptyCollectionIsNoVersion() {
        assertThat(TurSEInventoryEntry.toEpochMs(List.of())).isNull();
        assertThat(TurSEInventoryEntry.toEpochMs(null)).isNull();
    }
}
