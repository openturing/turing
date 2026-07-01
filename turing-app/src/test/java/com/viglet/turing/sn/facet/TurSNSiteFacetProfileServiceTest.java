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
package com.viglet.turing.sn.facet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
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
import org.springframework.web.server.ResponseStatusException;

import com.viglet.turing.api.sn.bean.TurSNSiteFacetOrderingDto;
import com.viglet.turing.api.sn.bean.TurSNSiteFacetProfileDto;
import com.viglet.turing.api.sn.bean.TurSNSiteFacetProfileRequest;
import com.viglet.turing.persistence.model.sn.TurSNSite;
import com.viglet.turing.persistence.model.sn.field.TurSNSiteCustomFacet;
import com.viglet.turing.persistence.model.sn.field.TurSNSiteFacetProfile;
import com.viglet.turing.persistence.model.sn.field.TurSNSiteFacetProfileItem;
import com.viglet.turing.persistence.model.sn.field.TurSNSiteFieldExt;
import com.viglet.turing.persistence.repository.sn.TurSNSiteRepository;
import com.viglet.turing.persistence.repository.sn.field.TurSNSiteCustomFacetRepository;
import com.viglet.turing.persistence.repository.sn.field.TurSNSiteFacetProfileRepository;
import com.viglet.turing.persistence.repository.sn.field.TurSNSiteFieldExtRepository;
import com.viglet.turing.sn.TurSNFieldProcess;

@ExtendWith(MockitoExtension.class)
class TurSNSiteFacetProfileServiceTest {

    private static final String SITE_ID = "site-1";

    @Mock
    private TurSNSiteRepository turSNSiteRepository;
    @Mock
    private TurSNSiteFacetProfileRepository turSNSiteFacetProfileRepository;
    @Mock
    private TurSNSiteFieldExtRepository turSNSiteFieldExtRepository;
    @Mock
    private TurSNSiteCustomFacetRepository turSNSiteCustomFacetRepository;
    @Mock
    private TurSNFieldProcess turSNFieldProcess;

    @InjectMocks
    private TurSNSiteFacetProfileService service;

    private static TurSNSite site() {
        TurSNSite site = new TurSNSite();
        site.setId(SITE_ID);
        site.setName(SITE_ID);
        return site;
    }

    private static TurSNSiteFieldExt fieldFacet(String id, int position) {
        return TurSNSiteFieldExt.builder().id(id).name(id).facet(1).enabled(1)
                .facetPosition(position).build();
    }

    private static TurSNSiteFacetProfileItem fieldItem(TurSNSiteFacetProfile profile,
            TurSNSiteFieldExt field, int position) {
        return TurSNSiteFacetProfileItem.builder().turSNSiteFacetProfile(profile)
                .turSNSiteFieldExt(field).facetPosition(position).build();
    }

    @Test
    void ensureDefaultProfileSeedsFromLegacyPositions() {
        TurSNSite site = site();
        TurSNSiteCustomFacet customFacet = TurSNSiteCustomFacet.builder()
                .id("cf").name("modality").facetPosition(1).build();
        TurSNSiteFieldExt field = fieldFacet("category", 2);

        when(turSNSiteFacetProfileRepository.findByTurSNSiteAndDefaultProfileTrue(site))
                .thenReturn(Optional.empty());
        when(turSNSiteFieldExtRepository.findByTurSNSiteAndEnabled(site, 1))
                .thenReturn(List.of(field));
        when(turSNSiteCustomFacetRepository.findByFieldExtsWithDetails(any()))
                .thenReturn(List.of(customFacet));
        when(turSNSiteFacetProfileRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        service.ensureDefaultProfile(site);

        ArgumentCaptor<TurSNSiteFacetProfile> captor =
                ArgumentCaptor.forClass(TurSNSiteFacetProfile.class);
        verify(turSNSiteFacetProfileRepository).save(captor.capture());
        TurSNSiteFacetProfile saved = captor.getValue();
        assertThat(saved.getName()).isEqualTo(TurSNSiteFacetProfileService.DEFAULT_PROFILE_NAME);
        assertThat(saved.isDefaultProfile()).isTrue();
        assertThat(saved.getItems()).hasSize(2);
    }

    @Test
    void ensureDefaultProfileIsIdempotent() {
        TurSNSite site = site();
        when(turSNSiteFacetProfileRepository.findByTurSNSiteAndDefaultProfileTrue(site))
                .thenReturn(Optional.of(new TurSNSiteFacetProfile()));

        service.ensureDefaultProfile(site);

        verify(turSNSiteFacetProfileRepository, never()).save(any());
    }

    @Test
    void createClonesDefaultAndIsNotDefault() {
        TurSNSite site = site();
        TurSNSiteFacetProfile current = TurSNSiteFacetProfile.builder()
                .id("default").name("Geral").defaultProfile(true).turSNSite(site).build();
        current.setItems(Set.of(fieldItem(current, fieldFacet("category", 1), 1)));

        when(turSNSiteRepository.findById(SITE_ID)).thenReturn(Optional.of(site));
        when(turSNSiteFacetProfileRepository.existsByTurSNSiteAndNameIgnoreCase(site, "Cursos EE"))
                .thenReturn(false);
        when(turSNSiteFacetProfileRepository.findByTurSNSiteAndDefaultProfileTrue(site))
                .thenReturn(Optional.of(current));
        when(turSNSiteFacetProfileRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        TurSNSiteFacetProfileDto result = service.create(SITE_ID,
                new TurSNSiteFacetProfileRequest("Cursos EE", null));

        assertThat(result.defaultProfile()).isFalse();
        assertThat(result.itemCount()).isEqualTo(1);
    }

    @Test
    void createRejectsDuplicateName() {
        TurSNSite site = site();
        when(turSNSiteRepository.findById(SITE_ID)).thenReturn(Optional.of(site));
        when(turSNSiteFacetProfileRepository.existsByTurSNSiteAndNameIgnoreCase(site, "Geral"))
                .thenReturn(true);

        assertThatThrownBy(() -> service.create(SITE_ID,
                new TurSNSiteFacetProfileRequest("Geral", null)))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("already exists");
    }

    @Test
    void listEnsuresDefaultProfileExists() {
        TurSNSite site = site();
        when(turSNSiteRepository.findById(SITE_ID)).thenReturn(Optional.of(site));
        when(turSNSiteFacetProfileRepository.findByTurSNSiteAndDefaultProfileTrue(site))
                .thenReturn(Optional.empty());
        when(turSNSiteFieldExtRepository.findByTurSNSiteAndEnabled(site, 1)).thenReturn(List.of());
        when(turSNSiteFacetProfileRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(turSNSiteFacetProfileRepository.findByTurSNSite(site)).thenReturn(List.of());

        service.list(SITE_ID);

        // No default existed -> list() must seed it before returning.
        verify(turSNSiteFacetProfileRepository).save(any());
    }

    @Test
    void updateRejectsRenamingDefaultProfile() {
        TurSNSite site = site();
        TurSNSiteFacetProfile profile = TurSNSiteFacetProfile.builder()
                .id("default").name("Geral").defaultProfile(true).turSNSite(site).build();
        when(turSNSiteRepository.findById(SITE_ID)).thenReturn(Optional.of(site));
        when(turSNSiteFacetProfileRepository.findById("default")).thenReturn(Optional.of(profile));

        assertThatThrownBy(() -> service.update(SITE_ID, "default",
                new TurSNSiteFacetProfileRequest("Outro Nome", null)))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("renamed");
        verify(turSNSiteFacetProfileRepository, never()).save(any());
    }

    @Test
    void updateAllowsDescriptionChangeOnDefaultProfileWhenNameUnchanged() {
        TurSNSite site = site();
        TurSNSiteFacetProfile profile = TurSNSiteFacetProfile.builder()
                .id("default").name("Geral").defaultProfile(true).turSNSite(site).build();
        when(turSNSiteRepository.findById(SITE_ID)).thenReturn(Optional.of(site));
        when(turSNSiteFacetProfileRepository.findById("default")).thenReturn(Optional.of(profile));
        when(turSNSiteFacetProfileRepository.findByTurSNSiteAndNameIgnoreCase(site, "Geral"))
                .thenReturn(Optional.of(profile));
        when(turSNSiteFacetProfileRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        TurSNSiteFacetProfileDto result = service.update(SITE_ID, "default",
                new TurSNSiteFacetProfileRequest("Geral", "Nova descrição"));

        assertThat(result.description()).isEqualTo("Nova descrição");
        assertThat(result.name()).isEqualTo("Geral");
    }

    @Test
    void deleteRejectsDefaultProfile() {
        TurSNSite site = site();
        TurSNSiteFacetProfile profile = TurSNSiteFacetProfile.builder()
                .id("default").defaultProfile(true).turSNSite(site).build();
        when(turSNSiteRepository.findById(SITE_ID)).thenReturn(Optional.of(site));
        when(turSNSiteFacetProfileRepository.findById("default")).thenReturn(Optional.of(profile));

        assertThatThrownBy(() -> service.delete(SITE_ID, "default"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("default");
        verify(turSNSiteFacetProfileRepository, never()).delete(any());
    }

    @Test
    void getOrderingAppliesProfilePositionsAndPutsUnpositionedLast() {
        TurSNSite site = site();
        TurSNSiteFieldExt f1 = fieldFacet("f1", 9);
        TurSNSiteFieldExt f2 = fieldFacet("f2", 9);
        TurSNSiteFacetProfile profile = TurSNSiteFacetProfile.builder()
                .id("p1").turSNSite(site).build();
        profile.setItems(Set.of(fieldItem(profile, f1, 1)));

        when(turSNSiteRepository.findById(SITE_ID)).thenReturn(Optional.of(site));
        when(turSNSiteFacetProfileRepository.findById("p1")).thenReturn(Optional.of(profile));
        when(turSNFieldProcess.getTurSNSiteFacetOrdering(SITE_ID)).thenReturn(Optional.of(List.of(
                new TurSNFieldFacetDefinition(f1, Set.of()),
                new TurSNFieldFacetDefinition(f2, Set.of()))));

        List<TurSNSiteFacetOrderingDto> result = service.getOrdering(SITE_ID, "p1");

        assertThat(result).extracting(TurSNSiteFacetOrderingDto::getId).containsExactly("f1", "f2");
        assertThat(result.get(0).getFacetPosition()).isEqualTo(1);
        assertThat(result.get(1).getFacetPosition()).isNull();
    }

    @Test
    void saveOrderingOnDefaultProfileSyncsLegacyPositions() {
        TurSNSite site = site();
        TurSNSiteFieldExt field = fieldFacet("f1", 9);
        TurSNSiteFacetProfile profile = TurSNSiteFacetProfile.builder()
                .id("p1").defaultProfile(true).turSNSite(site).build();

        when(turSNSiteRepository.findById(SITE_ID)).thenReturn(Optional.of(site));
        when(turSNSiteFacetProfileRepository.findById("p1")).thenReturn(Optional.of(profile));
        when(turSNSiteFieldExtRepository.findByTurSNSiteAndEnabled(site, 1))
                .thenReturn(List.of(field));
        when(turSNSiteCustomFacetRepository.findByFieldExtsWithDetails(any()))
                .thenReturn(List.of());
        when(turSNSiteFacetProfileRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(turSNFieldProcess.getTurSNSiteFacetOrdering(SITE_ID))
                .thenReturn(Optional.of(List.of(new TurSNFieldFacetDefinition(field, Set.of()))));

        service.saveOrdering(SITE_ID, "p1",
                List.of(new TurSNSiteFacetOrderingDto().setId("f1").setFacetPosition(1)));

        assertThat(field.getFacetPosition()).isEqualTo(1);
        verify(turSNSiteFieldExtRepository).saveAll(any());
    }

    @Test
    void saveOrderingOnNonDefaultProfileLeavesLegacyUntouched() {
        TurSNSite site = site();
        TurSNSiteFieldExt field = fieldFacet("f1", 9);
        TurSNSiteFacetProfile profile = TurSNSiteFacetProfile.builder()
                .id("p2").defaultProfile(false).turSNSite(site).build();

        when(turSNSiteRepository.findById(SITE_ID)).thenReturn(Optional.of(site));
        when(turSNSiteFacetProfileRepository.findById("p2")).thenReturn(Optional.of(profile));
        when(turSNSiteFieldExtRepository.findByTurSNSiteAndEnabled(site, 1))
                .thenReturn(List.of(field));
        when(turSNSiteCustomFacetRepository.findByFieldExtsWithDetails(any()))
                .thenReturn(List.of());
        when(turSNSiteFacetProfileRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(turSNFieldProcess.getTurSNSiteFacetOrdering(SITE_ID))
                .thenReturn(Optional.of(List.of(new TurSNFieldFacetDefinition(field, Set.of()))));

        service.saveOrdering(SITE_ID, "p2",
                List.of(new TurSNSiteFacetOrderingDto().setId("f1").setFacetPosition(1)));

        assertThat(field.getFacetPosition()).isEqualTo(9);
        verify(turSNSiteFieldExtRepository, never()).saveAll(any());
    }
}
