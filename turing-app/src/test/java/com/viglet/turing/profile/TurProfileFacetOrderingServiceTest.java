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
package com.viglet.turing.profile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.viglet.turing.api.sn.bean.TurSNSiteFacetOrderingDto;
import com.viglet.turing.persistence.model.profile.TurProfile;
import com.viglet.turing.persistence.model.profile.TurProfileFacetOrdering;
import com.viglet.turing.persistence.model.profile.TurProfileFacetOrderingItem;
import com.viglet.turing.persistence.model.sn.TurSNSite;
import com.viglet.turing.persistence.model.sn.field.TurSNSiteCustomFacet;
import com.viglet.turing.persistence.model.sn.field.TurSNSiteFieldExt;
import com.viglet.turing.persistence.repository.profile.TurProfileFacetOrderingRepository;
import com.viglet.turing.persistence.repository.profile.TurProfileRepository;
import com.viglet.turing.persistence.repository.sn.TurSNSiteRepository;
import com.viglet.turing.persistence.repository.sn.field.TurSNSiteCustomFacetRepository;
import com.viglet.turing.persistence.repository.sn.field.TurSNSiteFieldExtRepository;
import com.viglet.turing.sn.TurSNFieldProcess;
import com.viglet.turing.sn.facet.TurSNFieldFacetDefinition;

@ExtendWith(MockitoExtension.class)
class TurProfileFacetOrderingServiceTest {

    private static final String SITE_ID = "site-1";

    @Mock
    private TurProfileService turProfileService;
    @Mock
    private TurProfileRepository turProfileRepository;
    @Mock
    private TurProfileFacetOrderingRepository turProfileFacetOrderingRepository;
    @Mock
    private TurSNSiteRepository turSNSiteRepository;
    @Mock
    private TurSNSiteFieldExtRepository turSNSiteFieldExtRepository;
    @Mock
    private TurSNSiteCustomFacetRepository turSNSiteCustomFacetRepository;
    @Mock
    private TurSNFieldProcess turSNFieldProcess;

    @InjectMocks
    private TurProfileFacetOrderingService service;

    private static TurSNSite site() {
        TurSNSite site = new TurSNSite();
        site.setId(SITE_ID);
        site.setName(SITE_ID);
        return site;
    }

    private static TurProfile profile(String id, String name, boolean isDefault) {
        return TurProfile.builder().id(id).name(name).defaultProfile(isDefault).build();
    }

    private static TurSNSiteFieldExt fieldFacet(String id, int position) {
        return TurSNSiteFieldExt.builder().id(id).name(id).facet(1).enabled(1)
                .facetPosition(position).build();
    }

    private static TurProfileFacetOrdering ordering(TurProfile profile, TurSNSite site,
            TurProfileFacetOrderingItem... items) {
        TurProfileFacetOrdering ordering = TurProfileFacetOrdering.builder()
                .turProfile(profile).turSNSite(site).build();
        ordering.setItems(Set.of(items));
        return ordering;
    }

    private static TurProfileFacetOrderingItem fieldItem(TurSNSiteFieldExt field, int position) {
        return TurProfileFacetOrderingItem.builder().turSNSiteFieldExt(field)
                .facetPosition(position).build();
    }

    private static TurProfileFacetOrderingItem customItem(TurSNSiteCustomFacet customFacet,
            int position) {
        return TurProfileFacetOrderingItem.builder().turSNSiteCustomFacet(customFacet)
                .facetPosition(position).build();
    }

    @Test
    void getOrderingAppliesOwnConfigAndPutsUnpositionedLast() {
        TurSNSite site = site();
        TurProfile profile = profile("p1", "Cursos EE", false);
        TurSNSiteFieldExt f1 = fieldFacet("f1", 9);
        TurSNSiteFieldExt f2 = fieldFacet("f2", 9);

        when(turSNSiteRepository.findById(SITE_ID)).thenReturn(Optional.of(site));
        when(turProfileRepository.findById("p1")).thenReturn(Optional.of(profile));
        when(turProfileFacetOrderingRepository.findByTurProfileAndTurSNSite(profile, site))
                .thenReturn(Optional.of(ordering(profile, site, fieldItem(f1, 1))));
        when(turSNFieldProcess.getTurSNSiteFacetOrdering(eq(SITE_ID), any())).thenReturn(Optional.of(List.of(
                new TurSNFieldFacetDefinition(f1, Set.of()),
                new TurSNFieldFacetDefinition(f2, Set.of()))));

        List<TurSNSiteFacetOrderingDto> result = service.getOrdering(SITE_ID, "p1");

        assertThat(result).extracting(TurSNSiteFacetOrderingDto::getId).containsExactly("f1", "f2");
        assertThat(result.get(0).getFacetPosition()).isEqualTo(1);
        assertThat(result.get(1).getFacetPosition()).isNull();
    }

    @Test
    void getOrderingFallsBackToDefaultProfileConfig() {
        TurSNSite site = site();
        TurProfile profile = profile("p1", "Cursos EE", false);
        TurProfile padrao = profile("pd", "Padrão", true);
        TurSNSiteFieldExt f1 = fieldFacet("f1", 9);

        when(turSNSiteRepository.findById(SITE_ID)).thenReturn(Optional.of(site));
        when(turProfileRepository.findById("p1")).thenReturn(Optional.of(profile));
        when(turProfileFacetOrderingRepository.findByTurProfileAndTurSNSite(profile, site))
                .thenReturn(Optional.empty());
        when(turProfileRepository.findByDefaultProfileTrue()).thenReturn(Optional.of(padrao));
        when(turProfileFacetOrderingRepository.findByTurProfileAndTurSNSite(padrao, site))
                .thenReturn(Optional.of(ordering(padrao, site, fieldItem(f1, 3))));
        when(turSNFieldProcess.getTurSNSiteFacetOrdering(eq(SITE_ID), any())).thenReturn(
                Optional.of(List.of(new TurSNFieldFacetDefinition(f1, Set.of()))));

        List<TurSNSiteFacetOrderingDto> result = service.getOrdering(SITE_ID, "p1");

        assertThat(result.get(0).getFacetPosition()).isEqualTo(3);
    }

    @Test
    void getOrderingFallsBackToLegacyPositionsWhenNoConfigsExist() {
        TurSNSite site = site();
        TurProfile profile = profile("p1", "Cursos EE", false);
        TurSNSiteFieldExt f1 = fieldFacet("f1", 5);

        when(turSNSiteRepository.findById(SITE_ID)).thenReturn(Optional.of(site));
        when(turProfileRepository.findById("p1")).thenReturn(Optional.of(profile));
        when(turProfileFacetOrderingRepository.findByTurProfileAndTurSNSite(any(), any()))
                .thenReturn(Optional.empty());
        when(turProfileRepository.findByDefaultProfileTrue())
                .thenReturn(Optional.of(profile("pd", "Padrão", true)));
        when(turSNSiteFieldExtRepository.findByTurSNSiteAndEnabled(site, 1))
                .thenReturn(List.of(f1));
        when(turSNSiteCustomFacetRepository.findByFieldExtsWithDetails(any()))
                .thenReturn(List.of());
        when(turSNFieldProcess.getTurSNSiteFacetOrdering(eq(SITE_ID), any())).thenReturn(
                Optional.of(List.of(new TurSNFieldFacetDefinition(f1, Set.of()))));

        List<TurSNSiteFacetOrderingDto> result = service.getOrdering(SITE_ID, "p1");

        assertThat(result.get(0).getFacetPosition()).isEqualTo(5);
    }

    @Test
    void saveOrderingOnDefaultProfileSyncsLegacyPositions() {
        TurSNSite site = site();
        TurProfile padrao = profile("pd", "Padrão", true);
        TurSNSiteFieldExt field = fieldFacet("f1", 9);
        TurSNSiteCustomFacet customFacet = TurSNSiteCustomFacet.builder()
                .id("cf1").name("price_range").facetPosition(5).build();

        when(turSNSiteRepository.findById(SITE_ID)).thenReturn(Optional.of(site));
        when(turProfileRepository.findById("pd")).thenReturn(Optional.of(padrao));
        when(turSNSiteFieldExtRepository.findByTurSNSiteAndEnabled(site, 1))
                .thenReturn(List.of(field));
        when(turSNSiteCustomFacetRepository.findByFieldExtsWithDetails(any()))
                .thenReturn(List.of(customFacet));
        when(turProfileFacetOrderingRepository.findByTurProfileAndTurSNSite(padrao, site))
                .thenReturn(Optional.empty());
        when(turProfileFacetOrderingRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(turSNFieldProcess.getTurSNSiteFacetOrdering(eq(SITE_ID), any())).thenReturn(
                Optional.of(List.of(new TurSNFieldFacetDefinition(field, Set.of()))));

        service.saveOrdering(SITE_ID, "pd", List.of(
                new TurSNSiteFacetOrderingDto().setId("f1").setFacetPosition(1),
                new TurSNSiteFacetOrderingDto().setId("cf1").setFacetPosition(2)));

        assertThat(field.getFacetPosition()).isEqualTo(1);
        assertThat(customFacet.getFacetPosition()).isEqualTo(2);
        verify(turSNSiteFieldExtRepository).saveAll(any());
        verify(turSNSiteCustomFacetRepository).save(customFacet);
    }

    @Test
    void saveOrderingOnNonDefaultProfileLeavesLegacyUntouched() {
        TurSNSite site = site();
        TurProfile profile = profile("p2", "Cursos EE", false);
        TurSNSiteFieldExt field = fieldFacet("f1", 9);

        when(turSNSiteRepository.findById(SITE_ID)).thenReturn(Optional.of(site));
        when(turProfileRepository.findById("p2")).thenReturn(Optional.of(profile));
        when(turSNSiteFieldExtRepository.findByTurSNSiteAndEnabled(site, 1))
                .thenReturn(List.of(field));
        when(turSNSiteCustomFacetRepository.findByFieldExtsWithDetails(any()))
                .thenReturn(List.of());
        when(turProfileFacetOrderingRepository.findByTurProfileAndTurSNSite(profile, site))
                .thenReturn(Optional.empty());
        when(turProfileFacetOrderingRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(turSNFieldProcess.getTurSNSiteFacetOrdering(eq(SITE_ID), any())).thenReturn(
                Optional.of(List.of(new TurSNFieldFacetDefinition(field, Set.of()))));

        service.saveOrdering(SITE_ID, "p2",
                List.of(new TurSNSiteFacetOrderingDto().setId("f1").setFacetPosition(1)));

        assertThat(field.getFacetPosition()).isEqualTo(9);
        verify(turSNSiteFieldExtRepository, never()).saveAll(any());
        verify(turSNSiteCustomFacetRepository, never()).save(any());
    }

    @Test
    void saveOrderingUpsertsIntoExistingConfig() {
        TurSNSite site = site();
        TurProfile profile = profile("p2", "Cursos EE", false);
        TurSNSiteFieldExt field = fieldFacet("f1", 9);
        TurProfileFacetOrdering existing = ordering(profile, site, fieldItem(field, 7));

        when(turSNSiteRepository.findById(SITE_ID)).thenReturn(Optional.of(site));
        when(turProfileRepository.findById("p2")).thenReturn(Optional.of(profile));
        when(turSNSiteFieldExtRepository.findByTurSNSiteAndEnabled(site, 1))
                .thenReturn(List.of(field));
        when(turSNSiteCustomFacetRepository.findByFieldExtsWithDetails(any()))
                .thenReturn(List.of());
        when(turProfileFacetOrderingRepository.findByTurProfileAndTurSNSite(profile, site))
                .thenReturn(Optional.of(existing));
        when(turProfileFacetOrderingRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(turSNFieldProcess.getTurSNSiteFacetOrdering(eq(SITE_ID), any())).thenReturn(
                Optional.of(List.of(new TurSNFieldFacetDefinition(field, Set.of()))));

        List<TurSNSiteFacetOrderingDto> result = service.saveOrdering(SITE_ID, "p2",
                List.of(new TurSNSiteFacetOrderingDto().setId("f1").setFacetPosition(2)));

        ArgumentCaptor<TurProfileFacetOrdering> captor =
                ArgumentCaptor.forClass(TurProfileFacetOrdering.class);
        verify(turProfileFacetOrderingRepository).save(captor.capture());
        assertThat(captor.getValue()).isSameAs(existing);
        assertThat(existing.getItems()).hasSize(1);
        assertThat(existing.getItems().iterator().next().getFacetPosition()).isEqualTo(2);
        assertThat(result.get(0).getFacetPosition()).isEqualTo(2);
    }

    @Test
    void setDefaultSyncsLegacyMirrorOnEverySiteWithOrdering() {
        TurProfile cursos = profile("c", "Cursos EE", false);
        TurSNSiteFieldExt field = fieldFacet("f1", 9);
        TurSNSiteCustomFacet customFacet = TurSNSiteCustomFacet.builder()
                .id("cf1").name("price_range").facetPosition(9).build();
        TurProfileFacetOrdering siteOrdering = ordering(cursos, site(),
                fieldItem(field, 4), customItem(customFacet, 2));

        when(turProfileService.setDefault("c")).thenReturn(List.of());
        when(turProfileRepository.findById("c")).thenReturn(Optional.of(cursos));
        when(turProfileFacetOrderingRepository.findByTurProfile(cursos))
                .thenReturn(List.of(siteOrdering));

        service.setDefault("c");

        assertThat(field.getFacetPosition()).isEqualTo(4);
        assertThat(customFacet.getFacetPosition()).isEqualTo(2);
        verify(turSNSiteFieldExtRepository).saveAll(any());
        verify(turSNSiteCustomFacetRepository).save(customFacet);
    }
}
