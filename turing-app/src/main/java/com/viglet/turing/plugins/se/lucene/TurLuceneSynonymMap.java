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
package com.viglet.turing.plugins.se.lucene;

import java.util.List;
import java.util.Set;

import org.apache.lucene.analysis.synonym.SynonymMap;

import com.viglet.turing.persistence.model.sn.synonym.TurSNSynonymType;

/**
 * T665 / §XXXIX (Block AP) — the result of mapping Turing synonym rules onto a
 * Lucene {@link SynonymMap} for a query-time {@code SynonymGraphFilter}.
 *
 * @param map              the built map, or {@code null} when no rule produced an
 *                         entry (nothing to register)
 * @param appliedRules     how many source rules contributed at least one entry
 * @param unsupportedTypes rule types Lucene's filter cannot represent
 *                         (PLACEHOLDER — the honest "Lucene limits" caveat)
 * @param warnings         human notes (e.g. a correction degraded to one-way)
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
public record TurLuceneSynonymMap(
        SynonymMap map,
        int appliedRules,
        Set<TurSNSynonymType> unsupportedTypes,
        List<String> warnings) {
}
