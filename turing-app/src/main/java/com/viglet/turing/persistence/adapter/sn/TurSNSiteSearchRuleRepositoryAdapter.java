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

import com.viglet.turing.domain.sn.TurSNSiteSearchRuleDomain;
import com.viglet.turing.domain.sn.TurSNSiteSearchRuleRepositoryPort;
import com.viglet.turing.persistence.model.sn.TurSNSite;
import com.viglet.turing.persistence.repository.sn.searchrule.TurSNSiteSearchRuleRepository;

/**
 * Infrastructure adapter implementing
 * {@link TurSNSiteSearchRuleRepositoryPort}.
 *
 * @author Alexandre Oliveira
 * @since 2026.2.6
 */
@Component
public class TurSNSiteSearchRuleRepositoryAdapter implements TurSNSiteSearchRuleRepositoryPort {

    private final TurSNSiteSearchRuleRepository turSNSiteSearchRuleRepository;
    private final TurSNSiteSearchRuleDomainMapper mapper;

    public TurSNSiteSearchRuleRepositoryAdapter(
            TurSNSiteSearchRuleRepository turSNSiteSearchRuleRepository,
            TurSNSiteSearchRuleDomainMapper mapper) {
        this.turSNSiteSearchRuleRepository = turSNSiteSearchRuleRepository;
        this.mapper = mapper;
    }

    @Override
    public Optional<TurSNSiteSearchRuleDomain> findById(String id) {
        return turSNSiteSearchRuleRepository.findById(id).map(mapper::toDomain);
    }

    @Override
    public List<TurSNSiteSearchRuleDomain> findBySnSiteIdWithDetails(String snSiteId) {
        return mapper.toDomainList(
                turSNSiteSearchRuleRepository.findByTurSNSiteWithDetails(stub(snSiteId)));
    }

    @Override
    public Optional<TurSNSiteSearchRuleDomain> findBySnSiteIdAndId(String snSiteId, String id) {
        return turSNSiteSearchRuleRepository.findByTurSNSiteAndId(stub(snSiteId), id)
                .map(mapper::toDomain);
    }

    @Override
    public List<TurSNSiteSearchRuleDomain> findEnabledBySnSiteId(String snSiteId) {
        return mapper.toDomainList(
                turSNSiteSearchRuleRepository.findEnabledByTurSNSite(stub(snSiteId)));
    }

    private static TurSNSite stub(String id) {
        TurSNSite stub = new TurSNSite();
        stub.setId(id);
        return stub;
    }
}
