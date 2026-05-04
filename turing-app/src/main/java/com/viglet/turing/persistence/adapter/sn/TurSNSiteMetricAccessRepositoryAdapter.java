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

import java.time.Instant;
import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import com.viglet.turing.domain.sn.TurSNSiteMetricAccessDomain;
import com.viglet.turing.domain.sn.TurSNSiteMetricAccessRepositoryPort;
import com.viglet.turing.domain.sn.TurSNSiteMetricAccessTermDomain;
import com.viglet.turing.persistence.model.sn.TurSNSite;
import com.viglet.turing.persistence.repository.sn.metric.TurSNSiteMetricAccessRepository;
import com.viglet.turing.persistence.repository.sn.metric.TurSNSiteMetricAccessTerm;

/**
 * Infrastructure adapter implementing
 * {@link TurSNSiteMetricAccessRepositoryPort}.
 *
 * <p>The adapter translates between the repository's
 * {@link TurSNSiteMetricAccessTerm} projection class (lives in the
 * persistence layer) and the domain-side
 * {@link TurSNSiteMetricAccessTermDomain} record. Two factory methods on
 * the domain record correspond to the two JPA constructors so the
 * adapter can route latest-searches vs. top-terms results unambiguously.
 *
 * @author Alexandre Oliveira
 * @since 2026.2.6
 */
@Component
public class TurSNSiteMetricAccessRepositoryAdapter
        implements TurSNSiteMetricAccessRepositoryPort {

    private final TurSNSiteMetricAccessRepository turSNSiteMetricAccessRepository;
    private final TurSNSiteMetricAccessDomainMapper mapper;

    public TurSNSiteMetricAccessRepositoryAdapter(
            TurSNSiteMetricAccessRepository turSNSiteMetricAccessRepository,
            TurSNSiteMetricAccessDomainMapper mapper) {
        this.turSNSiteMetricAccessRepository = turSNSiteMetricAccessRepository;
        this.mapper = mapper;
    }

    @Override
    public List<TurSNSiteMetricAccessDomain> findBySnSiteIdAndLanguage(String snSiteId,
            String language) {
        return mapper.toDomainList(
                turSNSiteMetricAccessRepository.findByTurSNSiteAndLanguage(stub(snSiteId),
                        language));
    }

    @Override
    public List<TurSNSiteMetricAccessDomain> findBySnSiteId(String snSiteId) {
        return mapper.toDomainList(
                turSNSiteMetricAccessRepository.findByTurSNSite(stub(snSiteId)));
    }

    @Override
    public List<TurSNSiteMetricAccessDomain> findLastMinuteMetrics(String snSiteId, Instant since) {
        return mapper.toDomainList(
                turSNSiteMetricAccessRepository.findLastMinuteMetrics(snSiteId, since));
    }

    @Override
    public List<TurSNSiteMetricAccessTermDomain> findLatestSearches(String snSiteId,
            String language, String userId, Pageable pageable) {
        return turSNSiteMetricAccessRepository
                .findLatestSearches(stub(snSiteId), language, userId, pageable).stream()
                .map(t -> TurSNSiteMetricAccessTermDomain.latest(t.getTerm(), t.getAcessDate()))
                .toList();
    }

    @Override
    public List<TurSNSiteMetricAccessTermDomain> topTerms(String snSiteId, Pageable pageable) {
        return turSNSiteMetricAccessRepository.topTerms(stub(snSiteId), pageable).stream()
                .map(t -> TurSNSiteMetricAccessTermDomain.aggregated(t.getTerm(), t.getTotal(),
                        t.getNumFound()))
                .toList();
    }

    @Override
    public List<TurSNSiteMetricAccessTermDomain> topTermsBetweenDates(String snSiteId,
            Instant startDate, Instant endDate, Pageable pageable) {
        return turSNSiteMetricAccessRepository
                .topTermsBetweenDates(stub(snSiteId), startDate, endDate, pageable).stream()
                .map(t -> TurSNSiteMetricAccessTermDomain.aggregated(t.getTerm(), t.getTotal(),
                        t.getNumFound()))
                .toList();
    }

    @Override
    public int countTerms(String snSiteId) {
        return turSNSiteMetricAccessRepository.countTerms(stub(snSiteId));
    }

    @Override
    public int countTermsByPeriod(String snSiteId, Instant startDate, Instant endDate) {
        return turSNSiteMetricAccessRepository.countTermsByPeriod(stub(snSiteId), startDate,
                endDate);
    }

    private static TurSNSite stub(String id) {
        TurSNSite stub = new TurSNSite();
        stub.setId(id);
        return stub;
    }
}
