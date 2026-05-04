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

import org.springframework.stereotype.Component;

import com.viglet.turing.domain.sn.TurSNSiteCustomSortDomain;
import com.viglet.turing.domain.sn.TurSNSiteCustomSortRepositoryPort;
import com.viglet.turing.persistence.model.sn.TurSNSite;
import com.viglet.turing.persistence.repository.sn.sort.TurSNSiteCustomSortRepository;

/**
 * Infrastructure adapter implementing
 * {@link TurSNSiteCustomSortRepositoryPort}.
 *
 * @author Alexandre Oliveira
 * @since 2026.2.6
 */
@Component
public class TurSNSiteCustomSortRepositoryAdapter implements TurSNSiteCustomSortRepositoryPort {

    private final TurSNSiteCustomSortRepository turSNSiteCustomSortRepository;
    private final TurSNSiteCustomSortDomainMapper mapper;

    public TurSNSiteCustomSortRepositoryAdapter(
            TurSNSiteCustomSortRepository turSNSiteCustomSortRepository,
            TurSNSiteCustomSortDomainMapper mapper) {
        this.turSNSiteCustomSortRepository = turSNSiteCustomSortRepository;
        this.mapper = mapper;
    }

    @Override
    public Optional<TurSNSiteCustomSortDomain> findById(String id) {
        return turSNSiteCustomSortRepository.findById(id).map(mapper::toDomain);
    }

    @Override
    public List<TurSNSiteCustomSortDomain> findBySnSiteIdWithItems(String snSiteId) {
        return mapper.toDomainList(
                turSNSiteCustomSortRepository.findByTurSNSiteWithItems(stub(snSiteId)));
    }

    @Override
    public Optional<TurSNSiteCustomSortDomain> findBySnSiteIdAndId(String snSiteId, String id) {
        return turSNSiteCustomSortRepository.findByTurSNSiteAndId(stub(snSiteId), id)
                .map(mapper::toDomain);
    }

    @Override
    public Optional<TurSNSiteCustomSortDomain> findBySnSiteIdAndName(String snSiteId,
            String name) {
        return turSNSiteCustomSortRepository.findByTurSNSiteAndName(stub(snSiteId), name)
                .map(mapper::toDomain);
    }

    private static TurSNSite stub(String id) {
        TurSNSite stub = new TurSNSite();
        stub.setId(id);
        return stub;
    }
}
