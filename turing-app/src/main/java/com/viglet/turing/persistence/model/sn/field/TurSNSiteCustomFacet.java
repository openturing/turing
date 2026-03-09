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

package com.viglet.turing.persistence.model.sn.field;

import java.io.Serial;
import java.io.Serializable;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import com.fasterxml.jackson.annotation.JsonBackReference;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.viglet.turing.persistence.utils.TurAssignableUuidGenerator;

import jakarta.persistence.CascadeType;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapKeyColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Builder(toBuilder = true)
@AllArgsConstructor
@NoArgsConstructor
@Setter
@Getter
@Table(name = "sn_site_custom_facet")
@JsonIgnoreProperties({ "turSNSiteFieldExt" })
public class TurSNSiteCustomFacet implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @TurAssignableUuidGenerator
    @Column(name = "id", updatable = false, nullable = false)
    private String id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "default_label", length = 255)
    private String defaultLabel;

    @Column(name = "facet_position")
    private Integer facetPosition;

    @Builder.Default
    @Column(name = "facet_type")
    private TurSNSiteFacetFieldEnum facetType = TurSNSiteFacetFieldEnum.DEFAULT;

    @Builder.Default
    @Column(name = "facet_item_type")
    private TurSNSiteFacetFieldEnum facetItemType = TurSNSiteFacetFieldEnum.DEFAULT;

    @Builder.Default
    @ElementCollection
    @CollectionTable(name = "sn_site_custom_facet_label", joinColumns = @JoinColumn(name = "custom_facet_id"))
    @MapKeyColumn(name = "locale", length = 20)
    @Column(name = "label", nullable = false, length = 255)
    private Map<String, String> label = new HashMap<>();

    @Builder.Default
    @OneToMany(mappedBy = "turSNSiteCustomFacet", orphanRemoval = true, fetch = FetchType.LAZY, cascade = CascadeType.ALL)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Set<TurSNSiteCustomFacetItem> items = new HashSet<>();

    @ManyToOne
    @JoinColumn(name = "field_ext_id", nullable = false)
    @JsonBackReference(value = "turSNSiteCustomFacet-turSNSiteFieldExt")
    private TurSNSiteFieldExt turSNSiteFieldExt;

    public void setItems(Set<TurSNSiteCustomFacetItem> items) {
        this.items.clear();
        if (items != null) {
            for (TurSNSiteCustomFacetItem item : items) {
                item.setTurSNSiteCustomFacet(this);
                this.items.add(item);
            }
        }
    }
}
