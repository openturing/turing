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
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

import com.viglet.turing.domain.sn.TurSNRankingExpressionDomain;
import com.viglet.turing.domain.sn.TurSNRankingExpressionRepositoryPort;
import com.viglet.turing.persistence.model.sn.TurSNSite;
import com.viglet.turing.persistence.repository.sn.ranking.TurSNRankingExpressionRepository;

/**
 * Infrastructure adapter implementing
 * {@link TurSNRankingExpressionRepositoryPort}.
 *
 * @author Alexandre Oliveira
 * @since 2026.2.6
 */
@Component
public class TurSNRankingExpressionRepositoryAdapter
        implements TurSNRankingExpressionRepositoryPort {

    private final TurSNRankingExpressionRepository turSNRankingExpressionRepository;
    private final TurSNRankingExpressionDomainMapper mapper;

    public TurSNRankingExpressionRepositoryAdapter(
            TurSNRankingExpressionRepository turSNRankingExpressionRepository,
            TurSNRankingExpressionDomainMapper mapper) {
        this.turSNRankingExpressionRepository = turSNRankingExpressionRepository;
        this.mapper = mapper;
    }

    @Override
    public Optional<TurSNRankingExpressionDomain> findById(String id) {
        return turSNRankingExpressionRepository.findById(id).map(mapper::toDomain);
    }

    @Override
    public Set<TurSNRankingExpressionDomain> findBySnSiteId(String snSiteId) {
        return turSNRankingExpressionRepository
                .findByTurSNSite(Sort.by("name"), stub(snSiteId)).stream()
                .map(mapper::toDomain)
                .collect(Collectors.toSet());
    }

    private static TurSNSite stub(String id) {
        TurSNSite stub = new TurSNSite();
        stub.setId(id);
        return stub;
    }
}
