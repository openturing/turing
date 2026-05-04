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

import java.util.Map;
import java.util.Set;

import com.viglet.turing.persistence.model.sn.field.TurSNSiteFacetFieldEnum;

/**
 * Domain entity for a custom (curated) facet definition attached to a field
 * extension. Free of JPA / Jackson annotations and immutable.
 *
 * <p>The localised label map is exposed as an immutable {@code Map<locale,
 * label>}. The cascade-deleted items are <em>aggregate parts</em> of this
 * facet (no standalone repository) and are projected as an immutable set of
 * {@link TurSNSiteCustomFacetItemDomain} records carrying their full payload
 * (label, position, ranges, operator) — consumers reading a custom facet
 * always need the item data, so projecting them as IDs would force an N+1
 * round-trip. The owning field extension is referenced by
 * {@code fieldExtId}; resolve through
 * {@link TurSNSiteFieldExtRepositoryPort} when the full field is needed.
 *
 * @author Alexandre Oliveira
 * @since 2026.2.6
 */
public record TurSNSiteCustomFacetDomain(
        String id,
        String name,
        String defaultLabel,
        Integer facetPosition,
        TurSNSiteFacetFieldEnum facetType,
        TurSNSiteFacetFieldEnum facetItemType,
        Map<String, String> label,
        Set<TurSNSiteCustomFacetItemDomain> items,
        String fieldExtId) {
}
