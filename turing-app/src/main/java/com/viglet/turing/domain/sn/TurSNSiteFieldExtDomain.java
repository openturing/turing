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

import java.util.Set;

import com.viglet.turing.commons.se.field.TurSEFieldType;
import com.viglet.turing.persistence.model.sn.TurSNSiteFacetRangeEnum;
import com.viglet.turing.persistence.model.sn.field.TurSNSiteFacetFieldEnum;
import com.viglet.turing.persistence.model.sn.field.TurSNSiteFacetFieldSortEnum;
import com.viglet.turing.sn.TurSNFieldType;

/**
 * Domain entity for a Semantic Navigation site field extension — the
 * indexing / faceting / highlighting / MLT configuration for one Solr
 * field on one site. Free of JPA / Jackson annotations and immutable.
 *
 * <p>The parent site is referenced by {@code snSiteId}. Cascade-deleted
 * children are projected to immutable sets of IDs ({@code facetLocaleIds},
 * {@code customFacetIds}); resolve through their dedicated ports when the
 * full child aggregates are needed.
 *
 * @author Alexandre Oliveira
 * @since 2026.2.6
 */
public record TurSNSiteFieldExtDomain(
        String id,
        String externalId,
        String name,
        String description,
        String facetName,
        TurSNSiteFacetRangeEnum facetRange,
        TurSNSiteFacetFieldEnum facetType,
        TurSNSiteFacetFieldEnum facetItemType,
        TurSNSiteFacetFieldSortEnum facetSort,
        Integer facetPosition,
        Boolean secondaryFacet,
        Boolean showAllFacetItems,
        TurSNFieldType snType,
        TurSEFieldType type,
        int multiValued,
        int facet,
        int hl,
        int mlt,
        int enabled,
        int required,
        String defaultValue,
        String snSiteId,
        Set<String> facetLocaleIds,
        Set<String> customFacetIds) {

    /** True when the field is enabled for indexing / search (admin toggle). */
    public boolean isEnabled() {
        return enabled == 1;
    }

    /** True when the field acts as a facet on the search results page. */
    public boolean isFacet() {
        return facet == 1;
    }

    /** True when the field is highlighted on result snippets. */
    public boolean isHighlighted() {
        return hl == 1;
    }

    /** True when the field participates in the More-Like-This (MLT) similarity feature. */
    public boolean isMltCandidate() {
        return mlt == 1;
    }

    /** True when the field accepts multiple values per document (Solr multi-valued). */
    public boolean isMultiValued() {
        return multiValued == 1;
    }

    /** True when the field is required at indexing time. */
    public boolean isRequired() {
        return required == 1;
    }
}
