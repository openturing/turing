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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.viglet.turing.persistence.model.profile.TurProfile;
import com.viglet.turing.persistence.model.profile.TurProfileFacetOrdering;
import com.viglet.turing.persistence.model.profile.TurProfileFacetOrderingItem;
import com.viglet.turing.persistence.model.sn.TurSNSite;
import com.viglet.turing.persistence.model.sn.field.TurSNSiteFieldExt;
import com.viglet.turing.persistence.repository.profile.TurProfileFacetOrderingRepository;
import com.viglet.turing.persistence.repository.sn.TurSNSiteRepository;
import com.viglet.turing.persistence.repository.sn.field.TurSNSiteCustomFacetRepository;
import com.viglet.turing.persistence.repository.sn.field.TurSNSiteFieldExtRepository;
import com.viglet.turing.profile.TurProfileService;

@ExtendWith(MockitoExtension.class)
class TurProfileMigrationOnStartupTest {

    @Mock
    private TurProfileService turProfileService;
    @Mock
    private TurProfileFacetOrderingRepository turProfileFacetOrderingRepository;
    @Mock
    private TurSNSiteRepository turSNSiteRepository;
    @Mock
    private TurSNSiteFieldExtRepository turSNSiteFieldExtRepository;
    @Mock
    private TurSNSiteCustomFacetRepository turSNSiteCustomFacetRepository;

    @InjectMocks
    private TurProfileMigrationOnStartup migration;

    private static TurSNSite site(String id) {
        TurSNSite site = new TurSNSite();
        site.setId(id);
        site.setName(id);
        return site;
    }

    private static TurSNSiteFieldExt fieldFacet(String id, int position) {
        return TurSNSiteFieldExt.builder().id(id).name(id).facet(1).enabled(1)
                .facetPosition(position).build();
    }

    @Test
    void runSeedsPadraoFromLegacyFacetPositionWhenNoOrderingYet() {
        TurSNSite site = site("s1");
        TurProfile padrao = TurProfile.builder().id("pd").name("Padrão").defaultProfile(true).build();
        TurSNSiteFieldExt field = fieldFacet("f1", 1);

        when(turProfileService.ensureDefaultProfile()).thenReturn(padrao);
        when(turSNSiteRepository.findAll()).thenReturn(List.of(site));
        when(turProfileFacetOrderingRepository.findByTurProfileAndTurSNSite(padrao, site))
                .thenReturn(Optional.empty());
        when(turSNSiteFieldExtRepository.findByTurSNSiteAndEnabled(site, 1))
                .thenReturn(List.of(field));
        when(turSNSiteCustomFacetRepository.findByFieldExtsWithDetails(any())).thenReturn(List.of());
        when(turProfileFacetOrderingRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        migration.run(null);

        ArgumentCaptor<TurProfileFacetOrdering> captor =
                ArgumentCaptor.forClass(TurProfileFacetOrdering.class);
        verify(turProfileFacetOrderingRepository).save(captor.capture());
        TurProfileFacetOrdering saved = captor.getValue();
        assertThat(saved.getTurProfile()).isSameAs(padrao);
        assertThat(saved.getTurSNSite()).isSameAs(site);
        assertThat(saved.getItems()).extracting(TurProfileFacetOrderingItem::getFacetPosition)
                .containsExactly(1);
    }

    @Test
    void runSkipsSiteThatAlreadyHasDefaultOrdering() {
        TurSNSite site = site("s1");
        TurProfile padrao = TurProfile.builder().id("pd").name("Padrão").defaultProfile(true).build();

        when(turProfileService.ensureDefaultProfile()).thenReturn(padrao);
        when(turSNSiteRepository.findAll()).thenReturn(List.of(site));
        when(turProfileFacetOrderingRepository.findByTurProfileAndTurSNSite(padrao, site))
                .thenReturn(Optional.of(TurProfileFacetOrdering.builder().turProfile(padrao)
                        .turSNSite(site).build()));

        migration.run(null);

        verify(turProfileFacetOrderingRepository, org.mockito.Mockito.never()).save(any());
    }
}
