/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package com.viglet.turing.sn.manifest;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import com.viglet.turing.commons.se.field.TurSEFieldType;

/**
 * Pure, deterministic analysis of a sample of a source's documents into
 * per-field {@link TurSNManifestFieldObservation}s — the evidence half of
 * LLM-assisted manifest derivation (T387 / §XX.7).
 *
 * <p>It walks every document (a parsed JSON object as a {@code Map}), and for
 * each field records: how many documents carry a non-null value, the scalar
 * {@link TurSEFieldType}s observed, whether any value arrived as an array, the
 * distinct-value count, the longest string length, and a few example values.</p>
 *
 * <p>No LLM is involved and no judgement is made here — the mapping of these
 * observations to a draft field schema lives in {@link TurSNManifestHeuristics}.
 * Keeping the analysis pure makes it fully unit-testable and lets the derivation
 * run (degraded) even when no LLM is configured.</p>
 *
 * <p><b>Grounding invariant.</b> A {@code null} value is treated as
 * <em>absent</em> — it does not count toward {@code presentCount} — so a field
 * that some documents leave null reads as lower-coverage (optional) rather than
 * mandatory. This is the same absent ≠ empty contract the indexing path pins.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Component
public class TurSNManifestSampleAnalyzer {

    /** Above this string length a field reads as full-text (TEXT) rather than STRING. */
    static final int TEXT_LENGTH_THRESHOLD = 200;

    /** How many example values to retain per field. */
    private static final int MAX_SAMPLE_VALUES = 5;

    private static final Pattern ISO_DATE = Pattern.compile(
            "\\d{4}-\\d{2}-\\d{2}([T ]\\d{2}:\\d{2}(:\\d{2})?(\\.\\d+)?(Z|[+-]\\d{2}:?\\d{2})?)?");

    /**
     * Analyze the sample, preserving first-seen field order.
     *
     * @param documents parsed JSON objects; {@code null}/non-map entries are skipped.
     * @return one observation per distinct field name seen across the sample.
     */
    public List<TurSNManifestFieldObservation> analyze(List<Map<String, Object>> documents) {
        List<Map<String, Object>> docs = documents == null ? List.of() : documents;
        int totalDocuments = (int) docs.stream().filter(d -> d != null).count();

        // Preserve first-seen order so the draft reads in the source's own field order.
        Map<String, Accumulator> byField = new LinkedHashMap<>();
        for (Map<String, Object> doc : docs) {
            if (doc == null) {
                continue;
            }
            for (Map.Entry<String, Object> entry : doc.entrySet()) {
                byField.computeIfAbsent(entry.getKey(), Accumulator::new).observe(entry.getValue());
            }
        }

        List<TurSNManifestFieldObservation> observations = new ArrayList<>(byField.size());
        for (Accumulator acc : byField.values()) {
            observations.add(acc.toObservation(totalDocuments));
        }
        return observations;
    }

    // ─────────────────────────── Accumulation ───────────────────────────

    private static final class Accumulator {
        private final String name;
        private int presentCount;
        private boolean multiValued;
        private int maxLength;
        private final Set<TurSEFieldType> observedTypes = EnumSet.noneOf(TurSEFieldType.class);
        private final Set<String> distinctValues = new java.util.HashSet<>();
        private final List<String> sampleValues = new ArrayList<>();

        Accumulator(String name) {
            this.name = name;
        }

        /** Records one document's value for this field (null = absent). */
        void observe(Object value) {
            if (value == null) {
                return;
            }
            if (value instanceof Collection<?> collection) {
                multiValued = true;
                boolean any = false;
                for (Object element : collection) {
                    if (element != null) {
                        observeScalar(element);
                        any = true;
                    }
                }
                if (any) {
                    presentCount++;
                }
                return;
            }
            observeScalar(value);
            presentCount++;
        }

        private void observeScalar(Object value) {
            observedTypes.add(classify(value));
            String text = String.valueOf(value);
            maxLength = Math.max(maxLength, text.length());
            if (distinctValues.add(text) && sampleValues.size() < MAX_SAMPLE_VALUES) {
                sampleValues.add(text);
            }
        }

        TurSNManifestFieldObservation toObservation(int totalDocuments) {
            return new TurSNManifestFieldObservation(name, presentCount, totalDocuments,
                    EnumSet.copyOf(observedTypes.isEmpty()
                            ? EnumSet.of(TurSEFieldType.STRING) : observedTypes),
                    multiValued, distinctValues.size(), maxLength, List.copyOf(sampleValues));
        }
    }

    /** Maps a single scalar JSON value to a candidate {@link TurSEFieldType}. */
    private static TurSEFieldType classify(Object value) {
        return switch (value) {
            case Boolean ignored -> TurSEFieldType.BOOL;
            case Long ignored -> TurSEFieldType.LONG;
            case Integer ignored -> TurSEFieldType.INT;
            case Short ignored -> TurSEFieldType.INT;
            case Byte ignored -> TurSEFieldType.INT;
            case BigInteger ignored -> TurSEFieldType.LONG;
            case Double ignored -> TurSEFieldType.DOUBLE;
            case Float ignored -> TurSEFieldType.DOUBLE;
            case BigDecimal ignored -> TurSEFieldType.DOUBLE;
            case String s -> classifyString(s);
            default -> TurSEFieldType.STRING;
        };
    }

    private static TurSEFieldType classifyString(String s) {
        String trimmed = s.trim();
        if (ISO_DATE.matcher(trimmed).matches()) {
            return TurSEFieldType.DATE;
        }
        return trimmed.length() > TEXT_LENGTH_THRESHOLD ? TurSEFieldType.TEXT : TurSEFieldType.STRING;
    }
}
