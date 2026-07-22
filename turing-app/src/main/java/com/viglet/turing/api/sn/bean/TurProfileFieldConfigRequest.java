/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package com.viglet.turing.api.sn.bean;

import com.viglet.turing.persistence.model.sn.TurSNSiteFacetRangeEnum;
import com.viglet.turing.persistence.model.sn.field.TurSNSiteFacetFieldEnum;
import com.viglet.turing.persistence.model.sn.field.TurSNSiteFacetFieldSortEnum;

/**
 * Request body for a profile's per-field facet override (spec 003). Every
 * component is <b>nullable</b>: {@code null} means "inherit the field's global
 * value". Only facet settings are overridable per profile (schema/index settings
 * stay global).
 * <p>
 * All components are object types (never primitives) on purpose: Jackson 3
 * (Spring Boot 4) defaults {@code FAIL_ON_NULL_FOR_PRIMITIVES=true}, so a record
 * with a primitive component would reject a body that omits it.
 *
 * @author Arthur Nascimento
 * @since 2026.2.7
 */
public record TurProfileFieldConfigRequest(
        Integer facet,
        String facetName,
        TurSNSiteFacetFieldSortEnum facetSort,
        TurSNSiteFacetFieldEnum facetType,
        TurSNSiteFacetFieldEnum facetItemType,
        TurSNSiteFacetRangeEnum facetRange,
        Boolean secondaryFacet,
        Boolean showAllFacetItems) {

    /** True when at least one facet setting is overridden (non-null). */
    public boolean hasAnyOverride() {
        return facet != null || facetName != null || facetSort != null || facetType != null
                || facetItemType != null || facetRange != null || secondaryFacet != null
                || showAllFacetItems != null;
    }
}
