/*
 * Copyright (C) 2016-2026 the original author or authors.
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
package com.viglet.turing.onstartup.sn;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.viglet.turing.persistence.repository.sn.TurSNSiteRepository;
import com.viglet.turing.sn.facet.TurSNSiteFacetProfileService;

import lombok.extern.slf4j.Slf4j;

/**
 * Ensures every Semantic Navigation site has a default facet ordering profile
 * ("Geral") at startup, delegating the (idempotent) seeding to
 * {@link TurSNSiteFacetProfileService#ensureDefaultProfile}.
 * <p>
 * Runs at the lowest precedence so it also covers sites created or imported by
 * earlier startup runners.
 *
 * @author Arthur Nascimento
 * @since 2026.2.7
 */
@Slf4j
@Component
@Transactional
@Order(Ordered.LOWEST_PRECEDENCE)
public class TurSNSiteFacetProfileOnStartup implements ApplicationRunner {

    private final TurSNSiteRepository turSNSiteRepository;
    private final TurSNSiteFacetProfileService turSNSiteFacetProfileService;

    public TurSNSiteFacetProfileOnStartup(TurSNSiteRepository turSNSiteRepository,
            TurSNSiteFacetProfileService turSNSiteFacetProfileService) {
        this.turSNSiteRepository = turSNSiteRepository;
        this.turSNSiteFacetProfileService = turSNSiteFacetProfileService;
    }

    @Override
    public void run(ApplicationArguments args) {
        turSNSiteRepository.findAll()
                .forEach(turSNSiteFacetProfileService::ensureDefaultProfile);
    }
}
