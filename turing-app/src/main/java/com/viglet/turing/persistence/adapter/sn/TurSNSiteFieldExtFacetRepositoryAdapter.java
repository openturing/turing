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

import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.viglet.turing.domain.sn.TurSNSiteFieldExtFacetDomain;
import com.viglet.turing.domain.sn.TurSNSiteFieldExtFacetRepositoryPort;
import com.viglet.turing.persistence.model.sn.field.TurSNSiteFieldExt;
import com.viglet.turing.persistence.repository.sn.field.TurSNSiteFieldExtFacetRepository;

/**
 * Infrastructure adapter implementing
 * {@link TurSNSiteFieldExtFacetRepositoryPort} on top of the Spring Data
 * JPA repository.
 *
 * <p>Parent-id queries synthesise a stub {@link TurSNSiteFieldExt}
 * carrying just the ID — same pattern reused by the other field-scoped
 * adapters.
 *
 * @author Alexandre Oliveira
 * @since 2026.2.6
 */
@Component
public class TurSNSiteFieldExtFacetRepositoryAdapter
        implements TurSNSiteFieldExtFacetRepositoryPort {

    private final TurSNSiteFieldExtFacetRepository turSNSiteFieldExtFacetRepository;
    private final TurSNSiteFieldExtFacetDomainMapper mapper;

    public TurSNSiteFieldExtFacetRepositoryAdapter(
            TurSNSiteFieldExtFacetRepository turSNSiteFieldExtFacetRepository,
            TurSNSiteFieldExtFacetDomainMapper mapper) {
        this.turSNSiteFieldExtFacetRepository = turSNSiteFieldExtFacetRepository;
        this.mapper = mapper;
    }

    @Override
    public Optional<TurSNSiteFieldExtFacetDomain> findById(String id) {
        return turSNSiteFieldExtFacetRepository.findById(id).map(mapper::toDomain);
    }

    @Override
    public Set<TurSNSiteFieldExtFacetDomain> findByFieldExtId(String fieldExtId) {
        return turSNSiteFieldExtFacetRepository.findByTurSNSiteFieldExt(stub(fieldExtId)).stream()
                .map(mapper::toDomain)
                .collect(Collectors.toSet());
    }

    @Override
    public Set<TurSNSiteFieldExtFacetDomain> findByFieldExtIdAndLocale(String fieldExtId,
            Locale locale) {
        return turSNSiteFieldExtFacetRepository
                .findByTurSNSiteFieldExtAndLocale(stub(fieldExtId), locale).stream()
                .map(mapper::toDomain)
                .collect(Collectors.toSet());
    }

    private static TurSNSiteFieldExt stub(String id) {
        TurSNSiteFieldExt stub = new TurSNSiteFieldExt();
        stub.setId(id);
        return stub;
    }
}
