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

import java.util.concurrent.ConcurrentHashMap;

import org.apache.lucene.analysis.synonym.SynonymMap;
import org.springframework.stereotype.Component;

/**
 * T665 / §XXXIX (Block AP) — in-JVM registry of the query-time
 * {@link SynonymMap} per embedded Lucene core. Because the Lucene store is a
 * single-JVM engine, applying synonyms means swapping the map the query analyzer
 * consults — no reindex. {@code TurLuceneQueryBuilder} reads it at query time
 * (short-circuiting on {@link #isEmpty()} so sites without synonyms pay nothing);
 * the Lucene plugin's {@code applySynonyms} writes it.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Component
public class TurLuceneSynonymRegistry {

    private final ConcurrentHashMap<String, SynonymMap> byCore = new ConcurrentHashMap<>();

    /** Registers (or, when {@code map} is null, clears) the map for a core. */
    public void put(String coreName, SynonymMap map) {
        if (coreName == null) {
            return;
        }
        if (map == null) {
            byCore.remove(coreName);
        } else {
            byCore.put(coreName, map);
        }
    }

    /** The map for a core, or {@code null} if none is registered. */
    public SynonymMap get(String coreName) {
        return coreName == null ? null : byCore.get(coreName);
    }

    /** True when no core has synonyms — lets the query path skip all lookup. */
    public boolean isEmpty() {
        return byCore.isEmpty();
    }
}
