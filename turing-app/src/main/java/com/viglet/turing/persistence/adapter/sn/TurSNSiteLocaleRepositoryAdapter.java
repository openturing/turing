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
import java.util.Locale;
import java.util.Optional;

import org.springframework.stereotype.Component;

import com.viglet.turing.domain.sn.TurSNSiteCoreUsageDomain;
import com.viglet.turing.domain.sn.TurSNSiteLocaleDomain;
import com.viglet.turing.domain.sn.TurSNSiteLocaleRepositoryPort;
import com.viglet.turing.persistence.model.sn.TurSNSite;
import com.viglet.turing.persistence.repository.sn.locale.TurSNSiteLocaleRepository;

/**
 * Infrastructure adapter implementing {@link TurSNSiteLocaleRepositoryPort}
 * on top of the Spring Data JPA repository.
 *
 * @author Alexandre Oliveira
 * @since 2026.2.6
 */
@Component
public class TurSNSiteLocaleRepositoryAdapter implements TurSNSiteLocaleRepositoryPort {

    private final TurSNSiteLocaleRepository turSNSiteLocaleRepository;
    private final TurSNSiteLocaleDomainMapper mapper;

    public TurSNSiteLocaleRepositoryAdapter(TurSNSiteLocaleRepository turSNSiteLocaleRepository,
            TurSNSiteLocaleDomainMapper mapper) {
        this.turSNSiteLocaleRepository = turSNSiteLocaleRepository;
        this.mapper = mapper;
    }

    @Override
    public List<TurSNSiteLocaleDomain> findByCore(String core) {
        return mapper.toDomainList(turSNSiteLocaleRepository.findByCore(core));
    }

    @Override
    public boolean existsByCore(String core) {
        return !turSNSiteLocaleRepository.findByCore(core).isEmpty();
    }

    @Override
    public boolean existsBySnSiteIdAndLanguage(String snSiteId, Locale language) {
        TurSNSite stub = new TurSNSite();
        stub.setId(snSiteId);
        return turSNSiteLocaleRepository.existsByTurSNSiteAndLanguage(stub, language);
    }

    @Override
    public List<TurSNSiteCoreUsageDomain> findCoreUsage(String core) {
        return turSNSiteLocaleRepository.findByCore(core).stream()
                .map(locale -> new TurSNSiteCoreUsageDomain(
                        locale.getId(),
                        locale.getLanguage(),
                        locale.getTurSNSite().getId(),
                        locale.getTurSNSite().getName()))
                .toList();
    }

    @Override
    public List<TurSNSiteLocaleDomain> findBySnSiteIdOrderedByPosition(String snSiteId) {
        TurSNSite stub = new TurSNSite();
        stub.setId(snSiteId);
        return mapper.toDomainList(
                turSNSiteLocaleRepository.findByTurSNSiteOrderByPositionAsc(stub));
    }

    @Override
    public Optional<TurSNSiteLocaleDomain> findFirstLocaleBySnSiteIdOrderByPosition(
            String snSiteId) {
        TurSNSite stub = new TurSNSite();
        stub.setId(snSiteId);
        return Optional.ofNullable(
                turSNSiteLocaleRepository.findFirstByTurSNSiteOrderByPositionAsc(stub))
                .map(mapper::toDomain);
    }
}
