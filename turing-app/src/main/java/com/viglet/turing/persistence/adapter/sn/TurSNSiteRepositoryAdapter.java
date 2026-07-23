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

import com.viglet.turing.domain.sn.TurSNSiteDomain;
import com.viglet.turing.domain.sn.TurSNSiteRepositoryPort;
import com.viglet.turing.persistence.model.sn.TurSNSite;
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

    @Override
    public List<String> findAllNamesOrderedByNameIgnoreCase() {
        return turSNSiteRepository
                .findAll(Sort.by(Sort.Order.asc("name").ignoreCase()))
                .stream()
                .map(TurSNSite::getName)
                .filter(name -> name != null && !name.isBlank())
                .toList();
    }

    @Override
    public boolean hasRagEnabledForSiteName(String siteName) {
        // Walks site → genAi config → agent in one call so consumers can
        // gate UI/route paths on RAG availability with a single round-trip
        // instead of three port hops. Both hops are LAZY @ManyToOne/@OneToOne,
        // and this adapter is called from paths with no open session (e.g.
        // startup structured-feed provisioning), so the graph must be
        // JOIN FETCH-ed up front — a plain findByName would throw
        // LazyInitializationException on the getTurAIAgent() dereference.
        return turSNSiteRepository.findByNameWithGenAi(siteName)
                .map(site -> {
                    var genAi = site.getTurSNSiteGenAi();
                    if (genAi == null) {
                        return false;
                    }
                    var agent = genAi.getTurAIAgent();
                    return agent != null && agent.getEnabled() == 1 && agent.isRagEnabled();
                })
                .orElse(false);
    }
}
