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

/**
 * The manifest-derivation seam (T387 / §XX.7): turns a sample of a source's
 * documents into a <b>draft</b> {@link TurSNSiteManifest} (per-field
 * {@code TurSEFieldType}, facet/multi-valued/mandatory) for human review, so
 * onboarding a structured source starts from a generated schema instead of
 * hand-declaring every field.
 *
 * <p>The production implementation ({@link TurLlmManifestDeriver}) grounds the
 * configured default LLM on the deterministic sample analysis
 * ({@link TurSNManifestSampleAnalyzer}); when no LLM is configured it degrades to
 * the pure {@link TurSNManifestHeuristics}. Either way the output is a draft and
 * is <em>never</em> auto-applied — the conservative-grounding and
 * objective-ranking invariants make a silently-applied guessed schema
 * unacceptable.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
public interface TurSNManifestDeriver {

    /**
     * Derive a draft manifest from the sample in {@code request}.
     *
     * @throws IllegalArgumentException when the request carries no sample documents.
     */
    TurSNSiteManifest derive(TurSNManifestDeriveRequest request);

    /**
     * True when an LLM-grounded derivation is available. {@code false} means the
     * deriver still works, but falls back to the deterministic heuristics only.
     */
    boolean isLlmAvailable();
}
