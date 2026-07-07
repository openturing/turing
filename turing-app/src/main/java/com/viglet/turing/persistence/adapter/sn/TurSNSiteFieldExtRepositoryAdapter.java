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

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

import com.viglet.turing.domain.sn.TurSNSiteFieldExtDomain;
import com.viglet.turing.domain.sn.TurSNSiteFieldExtRepositoryPort;
import com.viglet.turing.persistence.model.sn.TurSNSite;
import com.viglet.turing.persistence.repository.sn.field.TurSNSiteFieldExtRepository;

/**
 * Infrastructure adapter implementing
 * {@link TurSNSiteFieldExtRepositoryPort} on top of the Spring Data JPA
 * repository.
 *
 * <p>Parent-id queries synthesise a stub {@link TurSNSite} carrying just
 * the ID, keeping the JPA entity out of the public port signatures —
 * same pattern established by the spotlight child adapters.
 *
 * @author Alexandre Oliveira
 * @since 2026.2.6
 */
@Component
public class TurSNSiteFieldExtRepositoryAdapter implements TurSNSiteFieldExtRepositoryPort {

    private final TurSNSiteFieldExtRepository turSNSiteFieldExtRepository;
    private final TurSNSiteFieldExtDomainMapper mapper;

    public TurSNSiteFieldExtRepositoryAdapter(
            TurSNSiteFieldExtRepository turSNSiteFieldExtRepository,
            TurSNSiteFieldExtDomainMapper mapper) {
        this.turSNSiteFieldExtRepository = turSNSiteFieldExtRepository;
        this.mapper = mapper;
    }

    @Override
    public Optional<TurSNSiteFieldExtDomain> findById(String id) {
        return turSNSiteFieldExtRepository.findById(id).map(mapper::toDomain);
    }

    @Override
    public List<TurSNSiteFieldExtDomain> findBySnSiteId(String snSiteId) {
        // Case-insensitive ascending name sort — matches the order the
        // GraphQL controller wants for its dynamic schema fields and is the
        // natural admin-facing order.
        return mapper.toDomainList(turSNSiteFieldExtRepository.findByTurSNSite(
                Sort.by(Sort.Order.asc("name").ignoreCase()), stub(snSiteId)));
    }

    @Override
    public List<TurSNSiteFieldExtDomain> findBySnSiteIdAndEnabled(String snSiteId, int enabled) {
        return mapper.toDomainList(
                turSNSiteFieldExtRepository.findByTurSNSiteAndEnabled(stub(snSiteId), enabled));
    }

    @Override
    public List<TurSNSiteFieldExtDomain> findBySnSiteIdAndFacetAndEnabledOrderByFacetPosition(
            String snSiteId, int facet, int enabled) {
        return mapper.toDomainList(turSNSiteFieldExtRepository
                .findByTurSNSiteAndFacetAndEnabledOrderByFacetPosition(stub(snSiteId), facet,
                        enabled));
    }

    @Override
    public boolean existsBySnSiteIdAndName(String snSiteId, String name) {
        return turSNSiteFieldExtRepository.existsByTurSNSiteAndName(stub(snSiteId), name);
    }

    @Override
    public boolean existsBySnSiteIdAndHlAndEnabled(String snSiteId, int hl, int enabled) {
        // Routes through the repository's findByTurSNSiteAndHlAndEnabled finder;
        // emptiness check at the boundary keeps the port contract a pure boolean.
        return !turSNSiteFieldExtRepository
                .findByTurSNSiteAndHlAndEnabled(stub(snSiteId), hl, enabled).isEmpty();
    }

    private static TurSNSite stub(String id) {
        TurSNSite stub = new TurSNSite();
        stub.setId(id);
        return stub;
    }
}
