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
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.viglet.turing.persistence.model.sn.TurSNSite;
import com.viglet.turing.persistence.model.sn.field.TurSNSiteFacetProfile;
import com.viglet.turing.persistence.model.sn.field.TurSNSiteFacetProfileItem;
import com.viglet.turing.persistence.model.sn.field.TurSNSiteFieldExt;
import com.viglet.turing.persistence.repository.sn.TurSNSiteRepository;
import com.viglet.turing.persistence.repository.sn.field.TurSNSiteCustomFacetRepository;
import com.viglet.turing.persistence.repository.sn.field.TurSNSiteFacetProfileRepository;
import com.viglet.turing.persistence.repository.sn.field.TurSNSiteFieldExtRepository;
import com.viglet.turing.sn.facet.TurSNFacetDefinitionFactory;

/**
 * Tests for the profile-aware facet ordering resolution (Fase 3 / runtime).
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
    private TurSNSiteFacetProfileRepository turSNSiteFacetProfileRepository;

    private TurSNFieldProcess turSNFieldProcess() {
        return new TurSNFieldProcess(turSNSiteRepository, turSNSiteFieldExtRepository,
                new TurSNFacetDefinitionFactory(turSNSiteCustomFacetRepository),
                turSNSiteFacetProfileRepository);
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

    private static TurSNSiteFacetProfile profile(String id, String name, boolean isDefault,
            TurSNSite site, TurSNSiteFieldExt field, int position) {
        TurSNSiteFacetProfile profile = TurSNSiteFacetProfile.builder()
                .id(id).name(name).defaultProfile(isDefault).turSNSite(site).build();
        profile.setItems(Set.of(TurSNSiteFacetProfileItem.builder()
                .turSNSiteFacetProfile(profile).turSNSiteFieldExt(field).facetPosition(position).build()));
        return profile;
    }

    @Test
    void appliesProfilePositionsByNameAndPushesUnpositionedToMaxValue() {
        TurSNSite site = site("site-1");
        TurSNSiteFieldExt category = fieldFacet("f1", "category", 5, site);
        TurSNSiteFieldExt author = fieldFacet("f2", "author", 6, site);

        when(turSNSiteRepository.findById("site-1")).thenReturn(Optional.of(site));
        when(turSNSiteFieldExtRepository.findByTurSNSiteAndEnabled(site, 1))
                .thenReturn(List.of(category, author));
        when(turSNSiteCustomFacetRepository.findByFieldExtsWithDetails(any()))
                .thenReturn(Collections.emptyList());
        when(turSNSiteFacetProfileRepository.findByTurSNSiteAndNameIgnoreCase(site, "Cursos EE"))
                .thenReturn(Optional.of(profile("p1", "Cursos EE", false, site, category, 1)));

        List<TurSNSiteFieldExt> result =
                turSNFieldProcess().getTurSNSiteFieldOrdering("site-1", "Cursos EE").orElseThrow();

        assertThat(result).extracting(TurSNSiteFieldExt::getName).containsExactly("category", "author");
        assertThat(result.get(0).getFacetPosition()).isEqualTo(1);
        assertThat(result.get(1).getFacetPosition()).isEqualTo(Integer.MAX_VALUE);
    }

    @Test
    void fallsBackToDefaultProfileWhenRequestedProfileIsNotFound() {
        TurSNSite site = site("site-1");
        TurSNSiteFieldExt category = fieldFacet("f1", "category", 5, site);
        TurSNSiteFieldExt author = fieldFacet("f2", "author", 6, site);

        when(turSNSiteRepository.findById("site-1")).thenReturn(Optional.of(site));
        when(turSNSiteFieldExtRepository.findByTurSNSiteAndEnabled(site, 1))
                .thenReturn(List.of(category, author));
        when(turSNSiteCustomFacetRepository.findByFieldExtsWithDetails(any()))
                .thenReturn(Collections.emptyList());
        when(turSNSiteFacetProfileRepository.findByTurSNSiteAndNameIgnoreCase(site, "Nope"))
                .thenReturn(Optional.empty());
        when(turSNSiteFacetProfileRepository.findById("Nope")).thenReturn(Optional.empty());
        when(turSNSiteFacetProfileRepository.findByTurSNSiteAndDefaultProfileTrue(site))
                .thenReturn(Optional.of(profile("def", "Geral", true, site, author, 1)));

        List<TurSNSiteFieldExt> result =
                turSNFieldProcess().getTurSNSiteFieldOrdering("site-1", "Nope").orElseThrow();

        // The default profile positions "author" first; "category" has no entry -> MAX_VALUE.
        assertThat(result).extracting(TurSNSiteFieldExt::getName).containsExactly("author", "category");
        assertThat(result.get(0).getFacetPosition()).isEqualTo(1);
        assertThat(result.get(1).getFacetPosition()).isEqualTo(Integer.MAX_VALUE);
    }
}
