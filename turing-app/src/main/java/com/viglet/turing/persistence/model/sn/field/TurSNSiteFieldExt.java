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

import com.fasterxml.jackson.annotation.JsonBackReference;
import com.viglet.turing.commons.se.field.TurSEFieldType;
import com.viglet.turing.persistence.model.sn.TurSNSite;
import com.viglet.turing.sn.TurSNFieldType;
import jakarta.persistence.*;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Tolerate;
import org.hibernate.annotations.Cascade;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;
import org.hibernate.annotations.UuidGenerator;

import java.io.Serial;
import java.io.Serializable;
import java.util.HashSet;
import java.util.Set;

/**
 * The persistent class for the turSNSiteFieldExt database table.
 */

@Builder
@Setter
@Getter
@Entity
@Table(name = "sn_site_field_ext")
public class TurSNSiteFieldExt implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @UuidGenerator
    @Column(name = "id", updatable = false, nullable = false)
    private String id;

    @Column(nullable = false)
    private String externalId;

    @Column(nullable = false, length = 50)
    private String name;

    private String description;

    @Column(length = 50)
    private String facetName;


    @OneToMany(mappedBy = "turSNSiteFieldExt", orphanRemoval = true, fetch = FetchType.LAZY)
    @Cascade({org.hibernate.annotations.CascadeType.ALL, org.hibernate.annotations.CascadeType.DELETE_ORPHAN})
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Set<TurSNSiteFieldExtFacet> facetLocales = new HashSet<>();

    @Column(nullable = false)
    private TurSNFieldType snType;

    @Column(nullable = false)
    private TurSEFieldType type;

    private int multiValued;

    private int facet;

    private int hl;

    private int mlt;

    private int enabled;

    private int required;

    @Column(length = 50)
    private String defaultValue;

    private int nlp;

    // bi-directional many-to-one association to TurSNSite
    @ManyToOne
    @JoinColumn(name = "sn_site_id", nullable = false)
    @JsonBackReference(value = "turSNSiteFieldExt-turSNSite")
    private TurSNSite turSNSite;

    @Tolerate
    public TurSNSiteFieldExt() {

    }
    public void setFacetLocales(Set<TurSNSiteFieldExtFacet> facetLocales) {
        this.facetLocales.clear();
        if (facetLocales != null) {
            this.facetLocales.addAll(facetLocales);
        }
    }
}