/*
 * Copyright (C) 2016-2022 the original author or authors.
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package com.viglet.turing.sn;

import com.google.inject.Inject;
import com.viglet.turing.commons.sn.bean.TurSNSiteSearchBean;
import com.viglet.turing.commons.sn.search.TurSNSiteSearchContext;
import com.viglet.turing.persistence.repository.sn.TurSNSiteRepository;
import com.viglet.turing.persistence.repository.sn.metric.TurSNSiteMetricAccessRepository;
import com.viglet.turing.persistence.repository.sn.metric.TurSNSiteMetricAccessTerm;
import com.viglet.turing.plugins.se.TurSeCommons;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * @author Alexandre Oliveira
 * @since 0.3.6
 */
@Slf4j
@Component
public class TurSNSearchProcess {
    private final TurSNSiteRepository turSNSiteRepository;
    private final TurSNSiteMetricAccessRepository turSNSiteMetricAccessRepository;
    private final TurSeCommons turSeCommons;
    @Inject
    public TurSNSearchProcess(TurSNSiteRepository turSNSiteRepository,
                              TurSNSiteMetricAccessRepository turSNSiteMetricAccessRepository,
                              TurSeCommons turSeCommons) {
        this.turSNSiteRepository = turSNSiteRepository;
        this.turSNSiteMetricAccessRepository = turSNSiteMetricAccessRepository;
        this.turSeCommons = turSeCommons;
    }

    public List<String> latestSearches(String siteName, String locale, String userId, int rows) {
        return turSNSiteRepository.findByName(siteName).map(turSNSite -> turSNSiteMetricAccessRepository
                .findLatestSearches(turSNSite, locale, userId, PageRequest.of(0, rows)).stream()
                .map(TurSNSiteMetricAccessTerm::getTerm).toList()).orElse(Collections.emptyList());
    }

    public TurSNSiteSearchBean search(TurSNSiteSearchContext turSNSiteSearchContext) {
        return Objects.requireNonNull(turSeCommons.getTurSeConnector(turSNSiteSearchContext))
                .map(seConnector -> seConnector.snSearch(turSNSiteSearchContext))
                .orElseGet(TurSNSiteSearchBean::new);
    }
}
