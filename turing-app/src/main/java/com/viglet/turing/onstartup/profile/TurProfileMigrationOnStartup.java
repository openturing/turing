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
package com.viglet.turing.onstartup.profile;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.viglet.turing.persistence.model.profile.TurProfile;
import com.viglet.turing.persistence.model.profile.TurProfileFacetOrdering;
import com.viglet.turing.persistence.model.profile.TurProfileFacetOrderingItem;
import com.viglet.turing.persistence.model.sn.TurSNSite;
import com.viglet.turing.persistence.model.sn.field.TurSNSiteCustomFacet;
import com.viglet.turing.persistence.model.sn.field.TurSNSiteFieldExt;
import com.viglet.turing.persistence.repository.profile.TurProfileFacetOrderingRepository;
import com.viglet.turing.persistence.repository.sn.TurSNSiteRepository;
import com.viglet.turing.persistence.repository.sn.field.TurSNSiteCustomFacetRepository;
import com.viglet.turing.persistence.repository.sn.field.TurSNSiteFieldExtRepository;
import com.viglet.turing.profile.TurProfileService;

import lombok.extern.slf4j.Slf4j;

/**
 * Ensures the global default profile ("Padrão") exists and seeds its per-site facet
 * ordering from the legacy {@code facetPosition} columns (spec 002).
 * <p>
 * Idempotent and order-independent:
 * <ul>
 * <li>the global default is seeded once (delegated to
 * {@link TurProfileService#ensureDefaultProfile()});</li>
 * <li>if the "Padrão" has no ordering for a site, it is seeded from the legacy
 * {@code facetPosition} columns, so every site gets a default ordering in the new model.</li>
 * </ul>
 * Runs at the lowest precedence so it also covers sites created by earlier runners.
 *
 * @author Arthur Nascimento
 * @since 2026.2.7
 */
@Slf4j
@Component
@Transactional
@Order(Ordered.LOWEST_PRECEDENCE)
public class TurProfileMigrationOnStartup implements ApplicationRunner {

    private final TurProfileService turProfileService;
    private final TurProfileFacetOrderingRepository turProfileFacetOrderingRepository;
    private final TurSNSiteRepository turSNSiteRepository;
    private final TurSNSiteFieldExtRepository turSNSiteFieldExtRepository;
    private final TurSNSiteCustomFacetRepository turSNSiteCustomFacetRepository;

    public TurProfileMigrationOnStartup(TurProfileService turProfileService,
            TurProfileFacetOrderingRepository turProfileFacetOrderingRepository,
            TurSNSiteRepository turSNSiteRepository,
            TurSNSiteFieldExtRepository turSNSiteFieldExtRepository,
            TurSNSiteCustomFacetRepository turSNSiteCustomFacetRepository) {
        this.turProfileService = turProfileService;
        this.turProfileFacetOrderingRepository = turProfileFacetOrderingRepository;
        this.turSNSiteRepository = turSNSiteRepository;
        this.turSNSiteFieldExtRepository = turSNSiteFieldExtRepository;
        this.turSNSiteCustomFacetRepository = turSNSiteCustomFacetRepository;
    }

    @Override
    public void run(ApplicationArguments args) {
        TurProfile defaultProfile = turProfileService.ensureDefaultProfile();
        turSNSiteRepository.findAll().forEach(site -> migrateSite(site, defaultProfile));
    }

    private void migrateSite(TurSNSite site, TurProfile defaultProfile) {
        // Seed "Padrão" from the legacy facetPosition columns if it has no ordering yet.
        if (turProfileFacetOrderingRepository.findByTurProfileAndTurSNSite(defaultProfile, site)
                .isEmpty()) {
            Set<TurProfileFacetOrderingItem> legacyItems = buildFromLegacy(site);
            if (!legacyItems.isEmpty()) {
                saveOrdering(defaultProfile, site, legacyItems);
            }
        }
    }

    private void saveOrdering(TurProfile profile, TurSNSite site,
            Set<TurProfileFacetOrderingItem> items) {
        TurProfileFacetOrdering ordering = TurProfileFacetOrdering.builder()
                .turProfile(profile).turSNSite(site).build();
        ordering.setItems(items);
        turProfileFacetOrderingRepository.save(ordering);
    }

    /** Builds ordering items from the legacy {@code facetPosition} of enabled facets. */
    private Set<TurProfileFacetOrderingItem> buildFromLegacy(TurSNSite site) {
        List<TurSNSiteFieldExt> enabledFields = turSNSiteFieldExtRepository
                .findByTurSNSiteAndEnabled(site, 1);
        Set<TurProfileFacetOrderingItem> items = new HashSet<>();
        enabledFields.stream()
                .filter(field -> field.getFacet() == 1 && field.getFacetPosition() != null)
                .forEach(field -> items.add(buildItem(field, null, field.getFacetPosition())));
        if (!enabledFields.isEmpty()) {
            turSNSiteCustomFacetRepository.findByFieldExtsWithDetails(enabledFields).stream()
                    .filter(customFacet -> customFacet.getFacetPosition() != null)
                    .forEach(customFacet -> items
                            .add(buildItem(null, customFacet, customFacet.getFacetPosition())));
        }
        return items;
    }

    private static TurProfileFacetOrderingItem buildItem(TurSNSiteFieldExt field,
            TurSNSiteCustomFacet customFacet, Integer position) {
        return TurProfileFacetOrderingItem.builder()
                .turSNSiteFieldExt(field)
                .turSNSiteCustomFacet(customFacet)
                .facetPosition(position)
                .build();
    }
}
