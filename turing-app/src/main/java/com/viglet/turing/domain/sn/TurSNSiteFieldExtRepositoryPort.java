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
 * Domain-side port for retrieving {@link TurSNSiteFieldExtDomain}
 * aggregates. Read-only; the JPA repository remains the source for the
 * cache-evicting save / delete paths.
 *
 * @author Alexandre Oliveira
 * @since 2026.2.6
 */
public interface TurSNSiteFieldExtRepositoryPort {

    Optional<TurSNSiteFieldExtDomain> findById(String id);

    /** All field extensions for the given site, ordered by name (case-insensitive ascending). */
    List<TurSNSiteFieldExtDomain> findBySnSiteId(String snSiteId);

    /** All enabled field extensions for the given site — the canonical search-time read. */
    List<TurSNSiteFieldExtDomain> findBySnSiteIdAndEnabled(String snSiteId, int enabled);

    /** All facet-enabled fields for a site, in {@code facetPosition} order. */
    List<TurSNSiteFieldExtDomain> findBySnSiteIdAndFacetAndEnabledOrderByFacetPosition(
            String snSiteId, int facet, int enabled);

    /** True when a field with the given case-sensitive name already exists on the site. */
    boolean existsBySnSiteIdAndName(String snSiteId, String name);

    /**
     * True when the site has at least one field flagged for highlighting and
     * enabled. Single-call denormalised gate that spares callers from
     * materialising a list of fields just to read its emptiness — used by
     * search services to decide whether to enable highlighting at request
     * time.
     */
    boolean existsBySnSiteIdAndHlAndEnabled(String snSiteId, int hl, int enabled);
}
