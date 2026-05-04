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

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.viglet.turing.domain.sn.TurSNSiteCustomFacetDomain;
import com.viglet.turing.domain.sn.TurSNSiteCustomFacetRepositoryPort;
import com.viglet.turing.persistence.model.sn.field.TurSNSiteFieldExt;
import com.viglet.turing.persistence.repository.sn.field.TurSNSiteCustomFacetRepository;

/**
 * Infrastructure adapter implementing
 * {@link TurSNSiteCustomFacetRepositoryPort} on top of the Spring Data JPA
 * repository.
 *
 * <p>Parent-id queries synthesise stub {@link TurSNSiteFieldExt} entities
 * carrying just the IDs, keeping the JPA entity out of the public port
 * signatures.
 *
 * @author Alexandre Oliveira
 * @since 2026.2.6
 */
@Component
public class TurSNSiteCustomFacetRepositoryAdapter implements TurSNSiteCustomFacetRepositoryPort {

    private final TurSNSiteCustomFacetRepository turSNSiteCustomFacetRepository;
    private final TurSNSiteCustomFacetDomainMapper mapper;

    public TurSNSiteCustomFacetRepositoryAdapter(
            TurSNSiteCustomFacetRepository turSNSiteCustomFacetRepository,
            TurSNSiteCustomFacetDomainMapper mapper) {
        this.turSNSiteCustomFacetRepository = turSNSiteCustomFacetRepository;
        this.mapper = mapper;
    }

    @Override
    public Optional<TurSNSiteCustomFacetDomain> findById(String id) {
        return turSNSiteCustomFacetRepository.findById(id).map(mapper::toDomain);
    }

    @Override
    public List<TurSNSiteCustomFacetDomain> findByFieldExtIdsWithDetails(
            Collection<String> fieldExtIds) {
        List<TurSNSiteFieldExt> stubs = fieldExtIds.stream()
                .map(TurSNSiteCustomFacetRepositoryAdapter::stubFieldExt)
                .collect(Collectors.toList());
        return mapper.toDomainList(
                turSNSiteCustomFacetRepository.findByFieldExtsWithDetails(stubs));
    }

    @Override
    public List<TurSNSiteCustomFacetDomain> findByFieldExtIdWithDetails(String fieldExtId) {
        return mapper.toDomainList(turSNSiteCustomFacetRepository
                .findByFieldExtWithDetails(stubFieldExt(fieldExtId)));
    }

    private static TurSNSiteFieldExt stubFieldExt(String id) {
        TurSNSiteFieldExt stub = new TurSNSiteFieldExt();
        stub.setId(id);
        return stub;
    }
}
