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

import java.util.Optional;

import org.springframework.stereotype.Component;

import com.viglet.turing.domain.sn.TurSNSiteDomain;
import com.viglet.turing.domain.sn.TurSNSiteRepositoryPort;
import com.viglet.turing.persistence.repository.sn.TurSNSiteRepository;

/**
 * Infrastructure adapter implementing {@link TurSNSiteRepositoryPort} on top
 * of the Spring Data JPA repository. Translates JPA entities to the domain
 * aggregate via {@link TurSNSiteDomainMapper} so the application layer never
 * sees a {@code TurSNSite} JPA proxy.
 *
 * @author Alexandre Oliveira
 * @since 2026.2.6
 */
@Component
public class TurSNSiteRepositoryAdapter implements TurSNSiteRepositoryPort {

    private final TurSNSiteRepository turSNSiteRepository;
    private final TurSNSiteDomainMapper mapper;

    public TurSNSiteRepositoryAdapter(TurSNSiteRepository turSNSiteRepository,
            TurSNSiteDomainMapper mapper) {
        this.turSNSiteRepository = turSNSiteRepository;
        this.mapper = mapper;
    }

    @Override
    public Optional<TurSNSiteDomain> findByName(String name) {
        return turSNSiteRepository.findByName(name).map(mapper::toDomain);
    }

    @Override
    public Optional<TurSNSiteDomain> findByNameIgnoreCase(String name) {
        return turSNSiteRepository.findByNameIgnoreCase(name).map(mapper::toDomain);
    }

    @Override
    public Optional<TurSNSiteDomain> findById(String id) {
        return turSNSiteRepository.findById(id).map(mapper::toDomain);
    }
}
