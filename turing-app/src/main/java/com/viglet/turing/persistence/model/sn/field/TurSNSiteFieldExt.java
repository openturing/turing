/*
 * Copyright (C) 2016-2022 the original author or authors.
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

package com.viglet.turing.persistence.model.sn.field;

import java.io.Serial;
import java.io.Serializable;
import java.util.HashSet;
import java.util.Set;

import org.hibernate.annotations.NotFound;
import org.hibernate.annotations.NotFoundAction;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import com.fasterxml.jackson.annotation.JsonBackReference;
import com.viglet.turing.commons.se.field.TurSEFieldType;
import com.viglet.turing.persistence.model.sn.TurSNSite;
import com.viglet.turing.persistence.model.sn.TurSNSiteFacetRangeEnum;
import com.viglet.core.jpa.VigletAssignableUuidGenerator;
import com.viglet.turing.sn.TurSNFieldType;
import com.viglet.turing.sn.snapshot.TurSNSiteSnapshotEvictionListener;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * The persistent class for the turSNSiteFieldExt database table.
 */

@Entity
@Builder(toBuilder = true)
@AllArgsConstructor
@NoArgsConstructor
@Setter
@Getter
@Table(name = "sn_site_field_ext")
@EntityListeners(TurSNSiteSnapshotEvictionListener.class)
public class TurSNSiteFieldExt implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @VigletAssignableUuidGenerator
    @Column(name = "id", updatable = false, nullable = false)
    private String id;
    @Column(nullable = false)
    private String externalId;
    @Column(nullable = false, length = 50)
    private String name;
    @Column(length = 500)
    private String description;
    @Column(length = 50)
    private String facetName;

    @Builder.Default
    @OneToMany(mappedBy = "turSNSiteFieldExt", orphanRemoval = true, fetch = FetchType.LAZY, cascade = CascadeType.ALL)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Set<TurSNSiteFieldExtFacet> facetLocales = new HashSet<>();
    @Builder.Default
    @OneToMany(mappedBy = "turSNSiteFieldExt", orphanRemoval = true, fetch = FetchType.LAZY, cascade = CascadeType.ALL)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Set<TurSNSiteCustomFacet> customFacets = new HashSet<>();
    @Builder.Default
    @Column
    private TurSNSiteFacetRangeEnum facetRange = TurSNSiteFacetRangeEnum.DISABLED;
    @Builder.Default
    @Column
    private TurSNSiteFacetFieldEnum facetType = TurSNSiteFacetFieldEnum.DEFAULT;
    @Builder.Default
    @Column
    private TurSNSiteFacetFieldEnum facetItemType = TurSNSiteFacetFieldEnum.DEFAULT;
    @Builder.Default
    @Column
    private TurSNSiteFacetFieldSortEnum facetSort = TurSNSiteFacetFieldSortEnum.DEFAULT;
    @Column
    private Integer facetPosition;
    @Column
    private Boolean secondaryFacet;
    @Column
    private Boolean showAllFacetItems;
    @Column(nullable = false)
    private TurSNFieldType snType;
    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private TurSEFieldType type;
    @Column
    private int multiValued;
    @Column
    private int facet;
    @Column
    private int hl;
    @Column
    private int mlt;
    @Column
    private int enabled;
    @Column
    private int required;
    @Column(length = 50)
    private String defaultValue;

    // bi-directional many-to-one association to TurSNSite
    // @NotFound(IGNORE): tolerate orphaned rows whose sn_site_id points to a
    // TurSNSite that was deleted without cascading its fields. Without this a
    // single orphan makes findAll() throw ObjectNotFoundException, which crashes
    // GraphQLConfig's dynamic-schema build at startup and takes the whole context
    // down (the graphQlSource bean fails). The site is never read from here — the
    // GraphQL schema builder only uses getName() — so resolving to null is safe.
    @ManyToOne
    @JoinColumn(name = "sn_site_id", nullable = false)
    @NotFound(action = NotFoundAction.IGNORE)
    @JsonBackReference(value = "turSNSiteFieldExt-turSNSite")
    private TurSNSite turSNSite;

    public void setFacetLocales(Set<TurSNSiteFieldExtFacet> facetLocales) {
        this.facetLocales.clear();

        if (facetLocales != null) {
            for (TurSNSiteFieldExtFacet facetLocale : facetLocales) {
                facetLocale.setTurSNSiteFieldExt(this);
                this.facetLocales.add(facetLocale);
            }
        }
    }

    public void setCustomFacets(Set<TurSNSiteCustomFacet> customFacets) {
        this.customFacets.clear();

        if (customFacets != null) {
            for (TurSNSiteCustomFacet customFacet : customFacets) {
                customFacet.setTurSNSiteFieldExt(this);
                this.customFacets.add(customFacet);
            }
        }
    }
}
