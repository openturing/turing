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

import java.util.List;
import java.util.Optional;

/**
 * Domain-side port for retrieving {@link TurSNSiteDomain} aggregates.
 * Implemented by an infrastructure adapter that delegates to the JPA
 * repository and maps via MapStruct. Application services depend on this
 * interface, not on the JPA repository, so the persistence technology stays
 * an implementation detail.
 *
 * <p>Read-only <b>by design, permanently</b> — write paths go through the JPA
 * repository directly ({@code @Transactional} + {@code LEFT JOIN FETCH}, per
 * T488). Ports will <b>not</b> grow {@code save} / {@code delete}: a write
 * gains nothing from an immutable read record and everything from a managed,
 * dirty-checked entity. See
 * {@code docs/adr/0001-domain-layer-bounded-completion.md}.
 *
 * @author Alexandre Oliveira
 * @since 2026.2.6
 */
public interface TurSNSiteRepositoryPort {

    /** Find a site by its case-sensitive name; empty when none matches. */
    Optional<TurSNSiteDomain> findByName(String name);

    /** Find a site by its name, case-insensitive; empty when none matches. */
    Optional<TurSNSiteDomain> findByNameIgnoreCase(String name);

    /** Find a site by its primary key. */
    Optional<TurSNSiteDomain> findById(String id);

    /**
     * True when the site has a GenAI configuration whose AI agent is both
     * enabled and has RAG turned on. Single-call denormalised gate that
     * spares callers from threading three separate port lookups
     * (site → genAi → agent) just to render a boolean.
     */
    boolean hasRagEnabledForSiteName(String siteName);

    /**
     * All non-blank site names ordered case-insensitively by name. Lean
     * projection for listings (admin name pickers, GraphQL site-name enum
     * derivation) that don't need any other site fields.
     */
    List<String> findAllNamesOrderedByNameIgnoreCase();
}
