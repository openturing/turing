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

import com.fasterxml.jackson.annotation.JsonBackReference;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.viglet.turing.persistence.model.sn.field.TurSNSiteCustomFacet;
import com.viglet.turing.persistence.model.sn.field.TurSNSiteFieldExt;
import com.viglet.turing.persistence.utils.TurAssignableUuidGenerator;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * The position of a single facet within a {@link TurProfileFacetOrdering}.
 * <p>
 * Exactly one of {@link #turSNSiteFieldExt} (a field facet) or
 * {@link #turSNSiteCustomFacet} (a custom facet) is set; the other is null. The
 * row is removed by the database {@code ON DELETE CASCADE} when its ordering or
 * its referenced facet is deleted.
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
@Table(name = "tur_profile_facet_ordering_item")
@JsonIgnoreProperties({ "turProfileFacetOrdering", "turSNSiteFieldExt", "turSNSiteCustomFacet" })
public class TurProfileFacetOrderingItem implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @TurAssignableUuidGenerator
    @Column(name = "id", updatable = false, nullable = false)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ordering_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    @JsonBackReference
    private TurProfileFacetOrdering turProfileFacetOrdering;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "field_ext_id")
    @OnDelete(action = OnDeleteAction.CASCADE)
    private TurSNSiteFieldExt turSNSiteFieldExt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "custom_facet_id")
    @OnDelete(action = OnDeleteAction.CASCADE)
    private TurSNSiteCustomFacet turSNSiteCustomFacet;

    @Column(name = "facet_position")
    private Integer facetPosition;
}
