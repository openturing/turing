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

import java.util.List;
import java.util.Set;

import com.viglet.turing.persistence.model.sn.synonym.TurSNSynonymType;

/**
 * T663 / §XXXIX (Block AP) — the outcome of pushing synonym rules into an
 * engine. Reports what was applied and — crucially — which rule types the
 * engine could <em>not</em> honour ({@code unsupportedTypes}) plus any human
 * warnings, so callers surface an honest capability banner ("placeholders
 * unsupported on this engine") instead of silently dropping rules.
 *
 * @param supported        whether the engine supports synonyms at all
 * @param applied          number of rules successfully pushed
 * @param unsupportedTypes rule types the engine cannot represent (skipped)
 * @param warnings         human-readable notes (e.g. degraded a correction to
 *                         one-way, or the engine call failed and was skipped)
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
public record TurSESynonymApplyResult(
        boolean supported,
        int applied,
        Set<TurSNSynonymType> unsupportedTypes,
        List<String> warnings) {

    /** The engine has no synonym capability (default seam behaviour). */
    public static TurSESynonymApplyResult unsupported(String engine) {
        return new TurSESynonymApplyResult(false, 0, Set.of(),
                List.of("Engine '" + engine + "' does not support synonyms."));
    }
}
