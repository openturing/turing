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
package com.viglet.turing.domain.sn;

import java.time.Instant;
import java.util.Locale;
import java.util.Set;

/**
 * Domain entity for one search-analytics access record — captures who
 * searched what, when, in which language, on which site, and how many
 * results came back. Free of JPA / Jackson annotations and immutable.
 *
 * <p>The {@code targetingRules} ElementCollection is projected as an
 * immutable set of strings; the parent site is referenced by ID and
 * resolves through {@link TurSNSiteRepositoryPort}.
 *
 * @author Alexandre Oliveira
 * @since 2026.2.6
 */
public record TurSNSiteMetricAccessDomain(
        String id,
        String userId,
        Instant accessDate,
        String term,
        String sanatizedTerm,
        Set<String> targetingRules,
        Locale language,
        long numFound,
        String snSiteId) {
}
