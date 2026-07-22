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
 * A field's facet configuration for a given profile (spec 003), as the console
 * "Campos" page consumes it: the {@code effective} config (override ∪ global) the
 * runtime would apply, plus {@code overridden} flags (which attributes came from
 * the profile override vs the global field) and the {@code global} values (so the
 * UI can show "inherited: &lt;value&gt;"). Schema/index attributes ({@code type},
 * {@code enabled}) are global (read-only under a profile).
 *
 * @author Arthur Nascimento
 * @since 2026.2.7
 */
public record TurSNSiteFieldProfileConfigDto(
        String fieldExtId,
        String name,
        String type,
        Integer enabled,
        FacetConfig effective,
        OverrideFlags overridden,
        FacetConfig global) {

    /** The overridable facet settings (the shape shared by effective and global). */
    public record FacetConfig(
            Integer facet,
            String facetName,
            TurSNSiteFacetFieldSortEnum facetSort,
            TurSNSiteFacetFieldEnum facetType,
            TurSNSiteFacetFieldEnum facetItemType,
            TurSNSiteFacetRangeEnum facetRange,
            Boolean secondaryFacet,
            Boolean showAllFacetItems) {
    }

    /** Per-attribute: true when the effective value came from the profile override. */
    public record OverrideFlags(
            boolean facet,
            boolean facetName,
            boolean facetSort,
            boolean facetType,
            boolean facetItemType,
            boolean facetRange,
            boolean secondaryFacet,
            boolean showAllFacetItems) {
    }
}
