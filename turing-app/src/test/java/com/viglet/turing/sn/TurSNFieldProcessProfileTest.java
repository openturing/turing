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
package com.viglet.turing.sn;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.viglet.turing.persistence.model.profile.TurProfile;
import com.viglet.turing.persistence.model.profile.TurProfileFacetOrdering;
import com.viglet.turing.persistence.model.profile.TurProfileFacetOrderingItem;
import com.viglet.turing.persistence.model.sn.TurSNSite;
import com.viglet.turing.persistence.model.sn.field.TurSNSiteFieldExt;
import com.viglet.turing.persistence.repository.profile.TurProfileFacetOrderingRepository;
import com.viglet.turing.persistence.repository.profile.TurProfileRepository;
import com.viglet.turing.persistence.repository.sn.TurSNSiteRepository;
import com.viglet.turing.persistence.repository.sn.field.TurSNSiteCustomFacetRepository;
import com.viglet.turing.persistence.repository.sn.field.TurSNSiteFieldExtRepository;
import com.viglet.turing.api.sn.bean.TurSNSiteFieldProfileConfigDto.FacetConfig;
import com.viglet.turing.sn.facet.TurSNFacetDefinition;
import com.viglet.turing.sn.facet.TurSNFacetDefinitionFactory;

/**
 * Tests for the global-profile-aware facet ordering resolution at search runtime
 * (spec 002, Fase 4). The {@code profile} search parameter resolves a global profile by name/id,
 * then reads its (profile × site) ordering, falling back to the default profile's
 * ordering for the site.
 *
 * @author Arthur Nascimento
 * @since 2026.2.7
 */
@ExtendWith(MockitoExtension.class)
class TurSNFieldProcessProfileTest {

    @Mock
    private TurSNSiteRepository turSNSiteRepository;
    @Mock
    private TurSNSiteFieldExtRepository turSNSiteFieldExtRepository;
    @Mock
    private TurSNSiteCustomFacetRepository turSNSiteCustomFacetRepository;
    @Mock
    private TurProfileRepository turProfileRepository;
    @Mock
    private TurProfileFacetOrderingRepository turProfileFacetOrderingRepository;
    @Mock
    private com.viglet.turing.profile.TurProfileFieldConfigService turProfileFieldConfigService;

    private TurSNFieldProcess turSNFieldProcess() {
        return new TurSNFieldProcess(turSNSiteRepository, turSNSiteFieldExtRepository,
                new TurSNFacetDefinitionFactory(turSNSiteCustomFacetRepository),
                turProfileRepository, turProfileFacetOrderingRepository, turProfileFieldConfigService);
    }

    private static TurSNSite site(String id) {
        TurSNSite site = new TurSNSite();
        site.setId(id);
        site.setName(id);
        return site;
    }

    private static TurSNSiteFieldExt fieldFacet(String id, String name, int legacyPosition,
            TurSNSite site) {
        TurSNSiteFieldExt field = new TurSNSiteFieldExt();
        field.setId(id);
        field.setName(name);
        field.setFacetName(name);
        field.setFacet(1);
        field.setEnabled(1);
        field.setFacetPosition(legacyPosition);
        field.setTurSNSite(site);
        return field;
    }

    private static TurProfileFacetOrdering ordering(TurProfile profile, TurSNSite site,
            TurSNSiteFieldExt field, int position) {
        TurProfileFacetOrdering ordering = TurProfileFacetOrdering.builder()
                .turProfile(profile).turSNSite(site).build();
        ordering.setItems(Set.of(TurProfileFacetOrderingItem.builder()
                .turSNSiteFieldExt(field).facetPosition(position).build()));
        return ordering;
    }

    @Test
    void facetDeactivatedForProfileInFieldsIsHiddenFromFacets() {
        // spec 003 interlink: a facet turned off for the profile in "Campos" (override facet=0)
        // must not appear in "Facetas" for that profile.
        TurSNSite site = site("site-1");
        TurSNSiteFieldExt category = fieldFacet("f1", "category", 5, site);
        TurSNSiteFieldExt author = fieldFacet("f2", "author", 6, site);
        TurProfile cursos = TurProfile.builder().id("p1").name("Cursos EE").build();

        when(turSNSiteRepository.findById("site-1")).thenReturn(Optional.of(site));
        when(turSNSiteFieldExtRepository.findByTurSNSiteAndEnabled(site, 1))
                .thenReturn(List.of(category, author));
        when(turSNSiteCustomFacetRepository.findByFieldExtsWithDetails(any()))
                .thenReturn(Collections.emptyList());
        when(turProfileFieldConfigService.resolveEffective(eq(cursos), any())).thenReturn(Map.of(
                "f1", new FacetConfig(0, null, null, null, null, null, null, null),
                "f2", new FacetConfig(1, null, null, null, null, null, null, null)));

        List<TurSNFacetDefinition> defs =
                turSNFieldProcess().getTurSNSiteFacetOrdering("site-1", cursos).orElseThrow();

        assertThat(defs).extracting(TurSNFacetDefinition::getName).containsExactly("author");
    }

    @Test
    void appliesProfileConfigByNameAndPushesUnpositionedToMaxValue() {
        TurSNSite site = site("site-1");
        TurSNSiteFieldExt category = fieldFacet("f1", "category", 5, site);
        TurSNSiteFieldExt author = fieldFacet("f2", "author", 6, site);
        TurProfile cursos = TurProfile.builder().id("p1").name("Cursos EE").build();

        when(turSNSiteRepository.findById("site-1")).thenReturn(Optional.of(site));
        when(turSNSiteFieldExtRepository.findByTurSNSiteAndEnabled(site, 1))
                .thenReturn(List.of(category, author));
        when(turSNSiteCustomFacetRepository.findByFieldExtsWithDetails(any()))
                .thenReturn(Collections.emptyList());
        when(turProfileRepository.findByNameIgnoreCase("Cursos EE")).thenReturn(Optional.of(cursos));
        when(turProfileFacetOrderingRepository.findByTurProfileAndTurSNSiteWithItems(cursos, site))
                .thenReturn(Optional.of(ordering(cursos, site, category, 1)));

        List<TurSNSiteFieldExt> result =
                turSNFieldProcess().getTurSNSiteFieldOrdering("site-1", "Cursos EE").orElseThrow();

        assertThat(result).extracting(TurSNSiteFieldExt::getName).containsExactly("category", "author");
        assertThat(result.get(0).getFacetPosition()).isEqualTo(1);
        assertThat(result.get(1).getFacetPosition()).isEqualTo(Integer.MAX_VALUE);
    }

    @Test
    void fallsBackToDefaultProfileConfigWhenRequestedHasNoneForSite() {
        TurSNSite site = site("site-1");
        TurSNSiteFieldExt category = fieldFacet("f1", "category", 5, site);
        TurSNSiteFieldExt author = fieldFacet("f2", "author", 6, site);
        TurProfile cursos = TurProfile.builder().id("p1").name("Cursos EE").build();
        TurProfile padrao = TurProfile.builder().id("pd").name("Padrão").defaultProfile(true).build();

        when(turSNSiteRepository.findById("site-1")).thenReturn(Optional.of(site));
        when(turSNSiteFieldExtRepository.findByTurSNSiteAndEnabled(site, 1))
                .thenReturn(List.of(category, author));
        when(turSNSiteCustomFacetRepository.findByFieldExtsWithDetails(any()))
                .thenReturn(Collections.emptyList());
        when(turProfileRepository.findByNameIgnoreCase("Cursos EE")).thenReturn(Optional.of(cursos));
        when(turProfileFacetOrderingRepository.findByTurProfileAndTurSNSiteWithItems(cursos, site))
                .thenReturn(Optional.empty());
        when(turProfileRepository.findByDefaultProfileTrue()).thenReturn(Optional.of(padrao));
        when(turProfileFacetOrderingRepository.findByTurProfileAndTurSNSiteWithItems(padrao, site))
                .thenReturn(Optional.of(ordering(padrao, site, author, 1)));

        List<TurSNSiteFieldExt> result =
                turSNFieldProcess().getTurSNSiteFieldOrdering("site-1", "Cursos EE").orElseThrow();

        // Default profile positions "author" first; "category" has no entry -> MAX_VALUE.
        assertThat(result).extracting(TurSNSiteFieldExt::getName).containsExactly("author", "category");
        assertThat(result.get(0).getFacetPosition()).isEqualTo(1);
        assertThat(result.get(1).getFacetPosition()).isEqualTo(Integer.MAX_VALUE);
    }

    @Test
    void fallsBackToDefaultProfileWhenProfileAbsent() {
        TurSNSite site = site("site-1");
        TurSNSiteFieldExt category = fieldFacet("f1", "category", 5, site);
        TurProfile padrao = TurProfile.builder().id("pd").name("Padrão").defaultProfile(true).build();

        when(turSNSiteRepository.findById("site-1")).thenReturn(Optional.of(site));
        when(turSNSiteFieldExtRepository.findByTurSNSiteAndEnabled(site, 1))
                .thenReturn(List.of(category));
        when(turSNSiteCustomFacetRepository.findByFieldExtsWithDetails(any()))
                .thenReturn(Collections.emptyList());
        when(turProfileRepository.findByDefaultProfileTrue()).thenReturn(Optional.of(padrao));
        when(turProfileFacetOrderingRepository.findByTurProfileAndTurSNSiteWithItems(padrao, site))
                .thenReturn(Optional.of(ordering(padrao, site, category, 1)));

        List<TurSNSiteFieldExt> result =
                turSNFieldProcess().getTurSNSiteFieldOrdering("site-1", null).orElseThrow();

        assertThat(result.get(0).getFacetPosition()).isEqualTo(1);
    }

    @Test
    void fallsBackToLegacyPositionsWhenNoDefaultConfigExists() {
        TurSNSite site = site("site-1");
        TurSNSiteFieldExt category = fieldFacet("f1", "category", 5, site);
        TurSNSiteFieldExt author = fieldFacet("f2", "author", 3, site);

        when(turSNSiteRepository.findById("site-1")).thenReturn(Optional.of(site));
        when(turSNSiteFieldExtRepository.findByTurSNSiteAndEnabled(site, 1))
                .thenReturn(List.of(category, author));
        when(turSNSiteCustomFacetRepository.findByFieldExtsWithDetails(any()))
                .thenReturn(Collections.emptyList());
        when(turProfileRepository.findByDefaultProfileTrue()).thenReturn(Optional.empty());

        List<TurSNSiteFieldExt> result =
                turSNFieldProcess().getTurSNSiteFieldOrdering("site-1", null).orElseThrow();

        // No profile config -> legacy facetPosition ordering (author=3 before category=5).
        assertThat(result).extracting(TurSNSiteFieldExt::getName).containsExactly("author", "category");
    }
}
