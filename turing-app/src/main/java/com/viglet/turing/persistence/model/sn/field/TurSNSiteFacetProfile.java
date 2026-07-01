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
import java.util.HashSet;
import java.util.Set;

import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.viglet.turing.persistence.model.sn.TurSNSite;
import com.viglet.turing.persistence.utils.TurAssignableUuidGenerator;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
 * A named facet ordering profile that belongs to a Semantic Navigation site.
 * <p>
 * Each site has one or more profiles; exactly one is the default profile. A
 * profile holds the per-facet positions through its {@link TurSNSiteFacetProfileItem}
 * children, allowing a site to keep several independent facet orderings selectable
 * at search time via the {@code facetProfile} parameter.
 * <p>
 * This entity intentionally does NOT register the
 * {@code TurSNSiteSnapshotEvictionListener}: facet order is resolved outside the
 * search snapshot, so profile changes must not evict it.
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
@Table(name = "sn_site_facet_profile")
@JsonIgnoreProperties({ "turSNSite" })
public class TurSNSiteFacetProfile implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @TurAssignableUuidGenerator
    @Column(name = "id", updatable = false, nullable = false)
    private String id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(length = 500)
    private String description;

    @Builder.Default
    @Column(name = "default_profile", nullable = false)
    private boolean defaultProfile = false;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sn_site_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private TurSNSite turSNSite;

    @Builder.Default
    @OneToMany(mappedBy = "turSNSiteFacetProfile", orphanRemoval = true, fetch = FetchType.LAZY, cascade = CascadeType.ALL)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Set<TurSNSiteFacetProfileItem> items = new HashSet<>();

    public void setItems(Set<TurSNSiteFacetProfileItem> items) {
        this.items.clear();
        if (items != null) {
            for (TurSNSiteFacetProfileItem item : items) {
                item.setTurSNSiteFacetProfile(this);
                this.items.add(item);
            }
        }
    }
}
