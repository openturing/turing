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
package com.viglet.turing.persistence.model.sn.synonym;

/**
 * T662 / §XXXIX (Block AP) — the engine-agnostic synonym rule types, mirroring
 * the five kinds Algolia offers so an imported index maps 1:1 and Turing can
 * push each into whichever engine (Solr/Elasticsearch/Lucene) backs the site.
 *
 * <p>The {@code input}/{@code terms} shape a rule uses depends on its type:
 * <ul>
 *   <li>{@link #REGULAR} — {@code terms} is a set of fully-equivalent tokens
 *       (a query for any one matches records containing any other);
 *       {@code input} is unused.</li>
 *   <li>{@link #ONE_WAY} — {@code input} expands to {@code terms} but not the
 *       reverse (searching {@code input} broadens; searching an expansion does
 *       not).</li>
 *   <li>{@link #ALTERNATIVE_CORRECTION_1} / {@link #ALTERNATIVE_CORRECTION_2} —
 *       {@code input} is treated as if it were a 1- (resp. 2-) typo of each
 *       token in {@code terms}, counting against the engine's typo budget.</li>
 *   <li>{@link #PLACEHOLDER} — {@code input} is a tokenised slot found in record
 *       text (e.g. {@code <streetnumber>}) that matches any value in
 *       {@code terms}. The most advanced/niche; unsupported on some engines.</li>
 * </ul>
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
public enum TurSNSynonymType {
    REGULAR,
    ONE_WAY,
    ALTERNATIVE_CORRECTION_1,
    ALTERNATIVE_CORRECTION_2,
    PLACEHOLDER
}
