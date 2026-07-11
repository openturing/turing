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
package com.viglet.turing.plugins.se.elasticsearch;

import java.util.List;
import java.util.Set;

import com.viglet.turing.persistence.model.sn.synonym.TurSNSynonymType;

/**
 * T664 / §XXXIX (Block AP) — the result of mapping Turing synonym rules onto
 * Elasticsearch Synonyms API rule lines (Solr/WordNet line format). Each line in
 * {@code ruleLines} is one {@code synonyms} entry of the {@code synonyms_set}
 * body (e.g. {@code "tv, television, telly"} or {@code "tablet => ipad"}).
 *
 * @param ruleLines        the synonym rule strings for the ES synonyms set
 * @param appliedRules     how many source rules produced a line
 * @param unsupportedTypes rule types ES cannot represent (e.g. PLACEHOLDER)
 * @param warnings         human notes (e.g. a correction degraded to one-way)
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
public record TurElasticsearchSynonymRules(
        List<String> ruleLines,
        int appliedRules,
        Set<TurSNSynonymType> unsupportedTypes,
        List<String> warnings) {
}
