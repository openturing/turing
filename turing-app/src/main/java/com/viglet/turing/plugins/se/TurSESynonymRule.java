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

import com.viglet.turing.persistence.model.sn.synonym.TurSNSynonymType;

/**
 * T663 / §XXXIX (Block AP) — the engine-neutral value object a search-engine
 * plugin consumes in {@link TurSearchEnginePlugin#applySynonyms}. It is a flat,
 * lazy-free projection of a {@code TurSNSynonym} row (the service maps entities
 * to these inside its transaction) so the plugin layer never touches JPA or
 * trips a lazy-load outside a persistence context.
 *
 * @param type  the rule kind (mirrors Algolia's five types)
 * @param input the left-hand side for the directional types; {@code null} for
 *              {@link TurSNSynonymType#REGULAR}
 * @param terms the equivalent set / expansions / correction alternatives /
 *              placeholder replacements (already trimmed + de-duplicated)
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
public record TurSESynonymRule(TurSNSynonymType type, String input, List<String> terms) {
}
