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
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * The facet-ordering configuration of a {@link TurProfile} for a single Semantic
 * Navigation site — i.e. one row per (profile × site).
 * <p>
 * This is the first "configuration type" attached to a generic profile: a global
 * profile can order facets differently on each site through one of these per site.
 * The per-facet positions live in its {@link TurProfileFacetOrderingItem} children.
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
@Table(name = "tur_profile_facet_ordering", uniqueConstraints = @UniqueConstraint(name = "uq_tur_profile_facet_ordering_profile_site", columnNames = {
        "profile_id", "sn_site_id" }))
@JsonIgnoreProperties({ "turProfile", "turSNSite" })
public class TurProfileFacetOrdering implements Serializable {
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
    @JoinColumn(name = "sn_site_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private TurSNSite turSNSite;

    @Builder.Default
    @OneToMany(mappedBy = "turProfileFacetOrdering", orphanRemoval = true, fetch = FetchType.LAZY, cascade = CascadeType.ALL)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Set<TurProfileFacetOrderingItem> items = new HashSet<>();

    public void setItems(Set<TurProfileFacetOrderingItem> items) {
        this.items.clear();
        if (items != null) {
            for (TurProfileFacetOrderingItem item : items) {
                item.setTurProfileFacetOrdering(this);
                this.items.add(item);
            }
        }
    }
}
