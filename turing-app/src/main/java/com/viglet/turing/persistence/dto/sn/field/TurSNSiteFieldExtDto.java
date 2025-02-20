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

package com.viglet.turing.persistence.dto.sn.field;

import com.viglet.turing.commons.se.field.TurSEFieldType;
import com.viglet.turing.persistence.model.sn.TurSNSite;
import com.viglet.turing.persistence.model.sn.TurSNSiteFacetRangeEnum;
import com.viglet.turing.persistence.model.sn.field.TurSNSiteFacetFieldEnum;
import com.viglet.turing.persistence.model.sn.field.TurSNSiteFieldExt;
import com.viglet.turing.persistence.model.sn.field.TurSNSiteFieldExtFacet;
import com.viglet.turing.sn.TurSNFieldType;
import lombok.*;
import lombok.experimental.Tolerate;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

@Builder(toBuilder = true)
@AllArgsConstructor
@Setter
@Getter
public class TurSNSiteFieldExtDto {
    private String id;
    private String externalId;
    private String name;
    private String description;
    private String facetName;
    @Builder.Default
    private Set<TurSNSiteFieldExtFacetDto> facetLocales = new HashSet<>();
    private TurSNFieldType snType;
    private TurSEFieldType type;
    private int multiValued;
    private int facet;
    private TurSNSiteFacetRangeEnum facetRange;
    private TurSNSiteFacetFieldEnum facetType;
    private TurSNSiteFacetFieldEnum facetItemType;
    private Boolean secondaryFacet;
    private Boolean showAllFacetItems;
    private int hl;
    private int mlt;
    private int enabled;
    private int required;
    private String defaultValue;
    private TurSNSite turSNSite;

    @Tolerate
    public TurSNSiteFieldExtDto() {
        this.facetLocales = new HashSet<>();
    }

    @Tolerate
    public TurSNSiteFieldExtDto(TurSNSiteFieldExt turSNSiteFieldExt) {
        this.id = turSNSiteFieldExt.getId();
        this.externalId = turSNSiteFieldExt.getExternalId();
        this.name = turSNSiteFieldExt.getName();
        this.description = turSNSiteFieldExt.getDescription();
        this.facet = turSNSiteFieldExt.getFacet();
        this.facetRange = turSNSiteFieldExt.getFacetRange();
        this.facetName = turSNSiteFieldExt.getFacetName();
        this.facetType = turSNSiteFieldExt.getFacetType();
        this.facetItemType = turSNSiteFieldExt.getFacetItemType();
        this.secondaryFacet = turSNSiteFieldExt.getSecondaryFacet();
        this.showAllFacetItems = turSNSiteFieldExt.getShowAllFacetItems();
        this.snType = turSNSiteFieldExt.getSnType();
        this.type = turSNSiteFieldExt.getType();
        this.multiValued = turSNSiteFieldExt.getMultiValued();
        this.hl = turSNSiteFieldExt.getHl();
        this.mlt = turSNSiteFieldExt.getMlt();
        this.enabled = turSNSiteFieldExt.getEnabled();
        this.required = turSNSiteFieldExt.getRequired();
        this.defaultValue = turSNSiteFieldExt.getDefaultValue();
        this.turSNSite = turSNSiteFieldExt.getTurSNSite();
    }

    public void setFacetLocales(Set<TurSNSiteFieldExtFacet> facetLocales) {
        if (this.facetLocales != null) {
            this.facetLocales.clear();
        } else {
            this.facetLocales = new HashSet<>();
        }
        if (facetLocales != null) {
            this.facetLocales = new HashSet<>();
            this.facetLocales.addAll(facetLocales.stream().map(TurSNSiteFieldExtFacetDto::new)
                    .collect(Collectors.toSet()));
        }
    }


}