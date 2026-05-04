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
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.viglet.turing.domain.sn.TurSNSiteSpotlightTermDomain;
import com.viglet.turing.domain.sn.TurSNSiteSpotlightTermRepositoryPort;
import com.viglet.turing.persistence.model.sn.spotlight.TurSNSiteSpotlight;
import com.viglet.turing.persistence.repository.sn.spotlight.TurSNSiteSpotlightTermRepository;

/**
 * Infrastructure adapter implementing
 * {@link TurSNSiteSpotlightTermRepositoryPort} on top of the Spring Data JPA
 * repository.
 *
 * <p>The {@code findBySnSiteSpotlightId} read uses a parent-id reference
 * (no JPA entity in the public signature) by building a stub
 * {@link TurSNSiteSpotlight} carrying just the ID, since the underlying
 * repository query already projects by FK column.
 *
 * @author Alexandre Oliveira
 * @since 2026.2.6
 */
@Component
public class TurSNSiteSpotlightTermRepositoryAdapter
        implements TurSNSiteSpotlightTermRepositoryPort {

    private final TurSNSiteSpotlightTermRepository turSNSiteSpotlightTermRepository;
    private final TurSNSiteSpotlightTermDomainMapper mapper;

    public TurSNSiteSpotlightTermRepositoryAdapter(
            TurSNSiteSpotlightTermRepository turSNSiteSpotlightTermRepository,
            TurSNSiteSpotlightTermDomainMapper mapper) {
        this.turSNSiteSpotlightTermRepository = turSNSiteSpotlightTermRepository;
        this.mapper = mapper;
    }

    @Override
    public Optional<TurSNSiteSpotlightTermDomain> findById(String id) {
        return turSNSiteSpotlightTermRepository.findById(id).map(mapper::toDomain);
    }

    @Override
    public List<TurSNSiteSpotlightTermDomain> findByNameIn(Collection<String> names) {
        return mapper.toDomainList(turSNSiteSpotlightTermRepository.findByNameIn(names));
    }

    @Override
    public Set<TurSNSiteSpotlightTermDomain> findBySnSiteSpotlightId(String snSiteSpotlightId) {
        TurSNSiteSpotlight stub = new TurSNSiteSpotlight();
        stub.setId(snSiteSpotlightId);
        return turSNSiteSpotlightTermRepository.findByTurSNSiteSpotlight(stub).stream()
                .map(mapper::toDomain)
                .collect(Collectors.toSet());
    }
}
