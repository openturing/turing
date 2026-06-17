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

import java.util.List;
import java.util.Set;

import com.viglet.turing.client.sn.job.TurSNJobAttributeSpec;
import com.viglet.turing.commons.se.field.TurSEFieldType;

/**
 * Pure, deterministic mapping from per-field {@link TurSNManifestFieldObservation}s
 * to a draft field schema — the no-LLM core of manifest derivation (T387 / §XX.7).
 *
 * <p>It encodes the obvious, defensible rules a human would apply when eyeballing
 * a sample:</p>
 * <ul>
 *   <li><b>type</b> — widen the scalar types observed across the sample into one
 *       ({@code DOUBLE} dominates {@code LONG}/{@code INT}; a long string reads as
 *       {@code TEXT}; mixed string+number falls back to {@code STRING}).</li>
 *   <li><b>mandatory</b> — present (non-null) in (nearly) every sampled document.</li>
 *   <li><b>multiValued</b> — observed as an array in at least one document.</li>
 *   <li><b>facet</b> — numeric/date/bool fields read as facets (range/term
 *       navigation); a string field is a facet only when low-cardinality
 *       (a controlled vocabulary, not free text); {@code TEXT} is never a facet.</li>
 * </ul>
 *
 * <p>This is used directly when no LLM is configured, and as the per-field
 * <em>fallback</em> inside {@link TurLlmManifestDeriver} (e.g. when the model
 * returns an unknown type for an observed field). Either way the output is a
 * <em>draft for human review</em>, never auto-applied — the grounding/ranking
 * invariants make a silently-applied guessed schema unacceptable.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
public final class TurSNManifestHeuristics {

    /** A field present in at least this fraction of documents reads as mandatory. */
    static final double MANDATORY_COVERAGE = 0.99d;

    /** A string field with at most this many distinct values reads as a facet. */
    static final int FACET_MAX_CARDINALITY = 50;

    private TurSNManifestHeuristics() {
    }

    /** Maps every observation to a draft field spec, preserving order. */
    public static List<TurSNJobAttributeSpec> toFieldSpecs(
            List<TurSNManifestFieldObservation> observations) {
        return (observations == null ? List.<TurSNManifestFieldObservation>of() : observations).stream()
                .filter(o -> o != null && o.name() != null && !o.name().isBlank())
                .map(TurSNManifestHeuristics::toFieldSpec)
                .toList();
    }

    /** Maps a single observation to a draft field spec. */
    public static TurSNJobAttributeSpec toFieldSpec(TurSNManifestFieldObservation o) {
        TurSEFieldType type = resolveType(o);
        return TurSNJobAttributeSpec.builder()
                .name(o.name())
                .type(type)
                .mandatory(o.coverage() >= MANDATORY_COVERAGE)
                .multiValued(o.multiValued())
                .facet(isFacet(type, o))
                .build();
    }

    /** Widens the scalar types observed across the sample into a single column type. */
    public static TurSEFieldType resolveType(TurSNManifestFieldObservation o) {
        Set<TurSEFieldType> types = o.observedTypes();
        if (types == null || types.isEmpty()) {
            return TurSEFieldType.STRING;
        }
        if (types.size() == 1) {
            return types.iterator().next();
        }
        // Mixed observations: pick the safest type that can hold all values.
        boolean allNumeric = types.stream().allMatch(TurSNManifestHeuristics::isNumeric);
        if (allNumeric) {
            if (types.contains(TurSEFieldType.DOUBLE)) {
                return TurSEFieldType.DOUBLE;
            }
            return types.contains(TurSEFieldType.LONG) ? TurSEFieldType.LONG : TurSEFieldType.INT;
        }
        // Any non-numeric mix (string/date/bool/number together): TEXT when a long
        // value was seen, otherwise the safe scalar fallback STRING.
        if (types.contains(TurSEFieldType.TEXT)
                || o.maxLength() > TurSNManifestSampleAnalyzer.TEXT_LENGTH_THRESHOLD) {
            return TurSEFieldType.TEXT;
        }
        return TurSEFieldType.STRING;
    }

    private static boolean isFacet(TurSEFieldType type, TurSNManifestFieldObservation o) {
        if (type == TurSEFieldType.TEXT) {
            return false;
        }
        if (isNumeric(type) || type == TurSEFieldType.DATE || type == TurSEFieldType.BOOL) {
            return true;
        }
        // STRING: a facet only when it reads as a controlled vocabulary, not free text.
        return o.distinctValueCount() > 0 && o.distinctValueCount() <= FACET_MAX_CARDINALITY;
    }

    private static boolean isNumeric(TurSEFieldType type) {
        return type == TurSEFieldType.INT
                || type == TurSEFieldType.LONG
                || type == TurSEFieldType.FLOAT
                || type == TurSEFieldType.DOUBLE
                || type == TurSEFieldType.CURRENCY;
    }
}
