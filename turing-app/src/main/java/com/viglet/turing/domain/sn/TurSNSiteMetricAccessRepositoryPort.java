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
package com.viglet.turing.domain.sn;

import java.time.Instant;
import java.util.List;

import org.springframework.data.domain.Pageable;

/**
 * Domain-side port for retrieving {@link TurSNSiteMetricAccessDomain}
 * rows and their aggregated {@link TurSNSiteMetricAccessTermDomain}
 * projections. Read-only.
 *
 * @author Alexandre Oliveira
 * @since 2026.2.6
 */
public interface TurSNSiteMetricAccessRepositoryPort {

    List<TurSNSiteMetricAccessDomain> findBySnSiteIdAndLanguage(String snSiteId, String language);

    List<TurSNSiteMetricAccessDomain> findBySnSiteId(String snSiteId);

    /** All accesses for a site since the given timestamp, oldest first. */
    List<TurSNSiteMetricAccessDomain> findLastMinuteMetrics(String snSiteId, Instant since);

    /** Latest unique sanitised search terms for a (site, language, user) triple. */
    List<TurSNSiteMetricAccessTermDomain> findLatestSearches(String snSiteId, String language,
            String userId, Pageable pageable);

    /** Top sanitised terms for a site, ordered by access count. */
    List<TurSNSiteMetricAccessTermDomain> topTerms(String snSiteId, Pageable pageable);

    /** Top sanitised terms for a site within a time window, ordered by access count. */
    List<TurSNSiteMetricAccessTermDomain> topTermsBetweenDates(String snSiteId, Instant startDate,
            Instant endDate, Pageable pageable);

    int countTerms(String snSiteId);

    int countTermsByPeriod(String snSiteId, Instant startDate, Instant endDate);
}
