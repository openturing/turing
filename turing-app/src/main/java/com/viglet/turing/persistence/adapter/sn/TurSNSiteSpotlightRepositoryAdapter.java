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
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.viglet.turing.domain.sn.TurSNSiteSpotlightDomain;
import com.viglet.turing.domain.sn.TurSNSiteSpotlightRepositoryPort;
import com.viglet.turing.persistence.repository.sn.spotlight.TurSNSiteSpotlightRepository;

/**
 * Infrastructure adapter implementing
 * {@link TurSNSiteSpotlightRepositoryPort} on top of the Spring Data JPA
 * repository.
 *
 * @author Alexandre Oliveira
 * @since 2026.2.6
 */
@Component
public class TurSNSiteSpotlightRepositoryAdapter implements TurSNSiteSpotlightRepositoryPort {

    private final TurSNSiteSpotlightRepository turSNSiteSpotlightRepository;
    private final TurSNSiteSpotlightDomainMapper mapper;

    public TurSNSiteSpotlightRepositoryAdapter(
            TurSNSiteSpotlightRepository turSNSiteSpotlightRepository,
            TurSNSiteSpotlightDomainMapper mapper) {
        this.turSNSiteSpotlightRepository = turSNSiteSpotlightRepository;
        this.mapper = mapper;
    }

    @Override
    public Optional<TurSNSiteSpotlightDomain> findById(String id) {
        return turSNSiteSpotlightRepository.findById(id).map(mapper::toDomain);
    }

    @Override
    public List<TurSNSiteSpotlightDomain> findAll() {
        return mapper.toDomainList(turSNSiteSpotlightRepository.findAll());
    }

    @Override
    public Set<TurSNSiteSpotlightDomain> findByProvider(String provider) {
        return turSNSiteSpotlightRepository.findByProvider(provider).stream()
                .map(mapper::toDomain)
                .collect(Collectors.toSet());
    }
}
