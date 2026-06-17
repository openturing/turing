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

import com.viglet.turing.commons.se.field.TurSEFieldType;

/**
 * Pure, deterministic per-field statistics gathered from a sample of a source's
 * documents by {@link TurSNManifestSampleAnalyzer} — the grounding for
 * LLM-assisted manifest derivation (T387 / §XX.7).
 *
 * <p>It is the evidence a draft schema is inferred from: how often the field
 * appears (coverage → {@code mandatory}), the scalar types observed across the
 * sample ({@code type}), whether any document carried it as an array
 * ({@code multiValued}), and how many distinct values were seen ({@code facet}
 * candidacy). It carries no judgement — the mapping to a
 * {@link com.viglet.turing.client.sn.job.TurSNJobAttributeSpec} lives in
 * {@link TurSNManifestHeuristics} (and, when an LLM is configured, in
 * {@link TurLlmManifestDeriver}).</p>
 *
 * <p>The grounding invariant is honoured here: a {@code null} value is treated
 * as <em>absent</em>, never as an empty present value — so a field that is null
 * in some documents lowers its coverage rather than inflating it.</p>
 *
 * @param name              the field name as it appears in the sample documents.
 * @param presentCount      number of sampled documents carrying a non-null value.
 * @param totalDocuments    total number of documents in the sample.
 * @param observedTypes     the scalar {@link TurSEFieldType}s seen across the
 *                          sample (a single field may show more than one when the
 *                          source is inconsistent).
 * @param multiValued       {@code true} when at least one document carried the
 *                          field as an array/collection.
 * @param distinctValueCount number of distinct scalar values observed (drives the
 *                          facet/low-cardinality heuristic).
 * @param maxLength         the longest string length observed (drives STRING vs
 *                          TEXT).
 * @param sampleValues      a few example values, for human review and as LLM
 *                          grounding.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
public record TurSNManifestFieldObservation(
        String name,
        int presentCount,
        int totalDocuments,
        Set<TurSEFieldType> observedTypes,
        boolean multiValued,
        int distinctValueCount,
        int maxLength,
        List<String> sampleValues) {

    /** Fraction of sampled documents that carry a non-null value (0..1). */
    public double coverage() {
        return totalDocuments == 0 ? 0d : (double) presentCount / totalDocuments;
    }
}
