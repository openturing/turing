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

package com.viglet.turing.persistence.model.profile;

import java.io.Serial;
import java.io.Serializable;

import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.viglet.turing.persistence.model.sn.TurSNSiteFacetRangeEnum;
import com.viglet.turing.persistence.model.sn.field.TurSNSiteFacetFieldEnum;
import com.viglet.turing.persistence.model.sn.field.TurSNSiteFacetFieldSortEnum;
import com.viglet.turing.persistence.model.sn.field.TurSNSiteFieldExt;
import com.viglet.turing.persistence.utils.TurAssignableUuidGenerator;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Per-profile facet configuration override for a single field — one row per
 * (profile × field). This is the second "configuration type" attached to a
 * generic {@link TurProfile} (spec 002, "Campos"): it lets a profile change how a
 * field behaves as a facet, on top of the facet <i>ordering</i> already handled by
 * {@code TurProfileFacetOrdering}.
 * <p>
 * Every override column is <b>nullable</b>: {@code null} means "inherit the field's
 * global value" ({@link TurSNSiteFieldExt}). Only facet display/query settings are
 * overridable here — index/schema settings (type, enabled, multiValued, …) stay
 * global. The field is already site-scoped, so (profile, field) is implicitly per site.
 *
 * @author Arthur Nascimento
 * @since 2026.2.7
 */
@Entity
@Builder(toBuilder = true)
@AllArgsConstructor
@NoArgsConstructor
@Setter
@Getter
@Table(name = "tur_profile_field_config", uniqueConstraints = @UniqueConstraint(name = "uq_tur_profile_field_config_profile_field", columnNames = {
        "profile_id", "field_ext_id" }))
@JsonIgnoreProperties({ "turProfile", "turSNSiteFieldExt" })
public class TurProfileFieldConfig implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @TurAssignableUuidGenerator
    @Column(name = "id", updatable = false, nullable = false)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "profile_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private TurProfile turProfile;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "field_ext_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private TurSNSiteFieldExt turSNSiteFieldExt;

    // --- Overridable facet settings (null = inherit the field's global value) ---

    /** Whether the field is a facet under this profile (1/0); null inherits. */
    @Column(name = "facet")
    private Integer facet;

    @Column(name = "facet_name", length = 50)
    private String facetName;

    @Enumerated(EnumType.STRING)
    @Column(name = "facet_sort", length = 50)
    private TurSNSiteFacetFieldSortEnum facetSort;

    @Enumerated(EnumType.STRING)
    @Column(name = "facet_type", length = 50)
    private TurSNSiteFacetFieldEnum facetType;

    @Enumerated(EnumType.STRING)
    @Column(name = "facet_item_type", length = 50)
    private TurSNSiteFacetFieldEnum facetItemType;

    @Enumerated(EnumType.STRING)
    @Column(name = "facet_range", length = 50)
    private TurSNSiteFacetRangeEnum facetRange;

    @Column(name = "secondary_facet")
    private Boolean secondaryFacet;

    @Column(name = "show_all_facet_items")
    private Boolean showAllFacetItems;
}
