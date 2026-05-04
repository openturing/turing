/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed to the Apache Software Foundation (ASF) under one or more contributor license
 * agreements. See the NOTICE file distributed with this work for additional information regarding
 * copyright ownership. The ASF licenses this file to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package com.viglet.turing.persistence.adapter.sn;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import com.viglet.turing.domain.sn.TurSNSiteFieldExtDomain;
import com.viglet.turing.persistence.model.sn.field.TurSNSiteCustomFacet;
import com.viglet.turing.persistence.model.sn.field.TurSNSiteFieldExt;
import com.viglet.turing.persistence.model.sn.field.TurSNSiteFieldExtFacet;

/**
 * MapStruct mapper for the JPA {@link TurSNSiteFieldExt} entity to the
 * {@link TurSNSiteFieldExtDomain} aggregate. Cascade-deleted children
 * (facet locales, custom facets) are projected to immutable sets of IDs.
 *
 * @author Alexandre Oliveira
 * @since 2026.2.6
 */
@Mapper(componentModel = "spring")
public interface TurSNSiteFieldExtDomainMapper {

    @Mapping(target = "snSiteId", source = "turSNSite.id")
    @Mapping(target = "facetLocaleIds", source = "facetLocales")
    @Mapping(target = "customFacetIds", source = "customFacets")
    TurSNSiteFieldExtDomain toDomain(TurSNSiteFieldExt entity);

    List<TurSNSiteFieldExtDomain> toDomainList(List<TurSNSiteFieldExt> entities);

    default Set<String> facetLocalesToIds(Set<TurSNSiteFieldExtFacet> set) {
        return set == null ? Set.of()
                : Collections.unmodifiableSet(set.stream().map(TurSNSiteFieldExtFacet::getId)
                        .collect(Collectors.toSet()));
    }

    default Set<String> customFacetsToIds(Set<TurSNSiteCustomFacet> set) {
        return set == null ? Set.of()
                : Collections.unmodifiableSet(set.stream().map(TurSNSiteCustomFacet::getId)
                        .collect(Collectors.toSet()));
    }
}
