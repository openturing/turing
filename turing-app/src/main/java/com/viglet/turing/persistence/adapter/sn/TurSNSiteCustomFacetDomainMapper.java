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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import com.viglet.turing.domain.sn.TurSNSiteCustomFacetDomain;
import com.viglet.turing.domain.sn.TurSNSiteCustomFacetItemDomain;
import com.viglet.turing.persistence.model.sn.field.TurSNSiteCustomFacet;
import com.viglet.turing.persistence.model.sn.field.TurSNSiteCustomFacetItem;

/**
 * MapStruct mapper for the JPA {@link TurSNSiteCustomFacet} entity to the
 * {@link TurSNSiteCustomFacetDomain} aggregate. The locale-keyed label map
 * is copied into an immutable map. The cascade-deleted items are projected
 * as full {@link TurSNSiteCustomFacetItemDomain} records (not just IDs)
 * because consumers reading a custom facet always need the item payload —
 * label, ranges, operator — and the JPA repository fetches them eagerly.
 *
 * @author Alexandre Oliveira
 * @since 2026.2.6
 */
@Mapper(componentModel = "spring")
public interface TurSNSiteCustomFacetDomainMapper {

    @Mapping(target = "fieldExtId", source = "turSNSiteFieldExt.id")
    @Mapping(target = "items", source = "items")
    @Mapping(target = "label", source = "label")
    TurSNSiteCustomFacetDomain toDomain(TurSNSiteCustomFacet entity);

    List<TurSNSiteCustomFacetDomain> toDomainList(List<TurSNSiteCustomFacet> entities);

    TurSNSiteCustomFacetItemDomain toItemDomain(TurSNSiteCustomFacetItem entity);

    default Set<TurSNSiteCustomFacetItemDomain> itemsToDomain(Set<TurSNSiteCustomFacetItem> set) {
        return set == null ? Set.of()
                : Collections.unmodifiableSet(
                        set.stream().map(this::toItemDomain).collect(Collectors.toSet()));
    }

    default Map<String, String> labelMap(Map<String, String> source) {
        return source == null ? Map.of() : Collections.unmodifiableMap(new HashMap<>(source));
    }
}
