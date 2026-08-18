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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import com.viglet.turing.api.sn.bean.TurProfileFieldConfigRequest;
import com.viglet.turing.api.sn.bean.TurSNSiteFieldProfileConfigDto;
import com.viglet.turing.api.sn.bean.TurSNSiteFieldProfileConfigDto.FacetConfig;
import com.viglet.turing.persistence.model.profile.TurProfile;
import com.viglet.turing.persistence.model.profile.TurProfileFieldConfig;
import com.viglet.turing.persistence.model.sn.TurSNSite;
import com.viglet.turing.persistence.model.sn.field.TurSNSiteFieldExt;
import com.viglet.turing.persistence.repository.profile.TurProfileFieldConfigRepository;
import com.viglet.turing.persistence.repository.profile.TurProfileRepository;
import com.viglet.turing.persistence.repository.sn.TurSNSiteRepository;
import com.viglet.turing.persistence.repository.sn.field.TurSNSiteFieldExtRepository;

@ExtendWith(MockitoExtension.class)
class TurProfileFieldConfigServiceTest {

    private static final String SITE_ID = "site-1";
    private static final String PROFILE_ID = "profile-1";
    private static final String DEFAULT_ID = "profile-default";
    private static final String FIELD_ID = "field-1";

    @Mock
    private TurProfileRepository turProfileRepository;
    @Mock
    private TurProfileFieldConfigRepository turProfileFieldConfigRepository;
    @Mock
    private TurSNSiteRepository turSNSiteRepository;
    @Mock
    private TurSNSiteFieldExtRepository turSNSiteFieldExtRepository;

    @InjectMocks
    private TurProfileFieldConfigService service;

    private static TurSNSite site() {
        TurSNSite site = new TurSNSite();
        site.setId(SITE_ID);
        site.setName(SITE_ID);
        return site;
    }

    private static TurProfile profile(String id, boolean isDefault) {
        return TurProfile.builder().id(id).name(id).defaultProfile(isDefault).build();
    }

    private static TurSNSiteFieldExt field(int globalFacet) {
        return TurSNSiteFieldExt.builder().id(FIELD_ID).name("catedras").facet(globalFacet)
                .facetName("catedras").enabled(1).turSNSite(site()).build();
    }

    private static TurProfileFieldConfig override(TurProfile profile, TurSNSiteFieldExt field,
            Integer facet, String facetName) {
        return TurProfileFieldConfig.builder().turProfile(profile).turSNSiteFieldExt(field)
                .facet(facet).facetName(facetName).build();
    }

    // -------------------------------------------------------------------- reads

    @Test
    void effectiveConfigMergesOverrideWithGlobal() {
        TurProfile profile = profile(PROFILE_ID, false);
        TurSNSiteFieldExt field = field(0); // global: not a facet
        when(turSNSiteRepository.findById(SITE_ID)).thenReturn(Optional.of(site()));
        when(turProfileRepository.findById(PROFILE_ID)).thenReturn(Optional.of(profile));
        when(turSNSiteFieldExtRepository.findByTurSNSite(any(), any())).thenReturn(List.of(field));
        when(turProfileFieldConfigRepository.findByProfileId(PROFILE_ID))
                .thenReturn(List.of(override(profile, field, 1, "Cátedras")));

        List<TurSNSiteFieldProfileConfigDto> result = service.getEffectiveConfigs(SITE_ID, PROFILE_ID);

        assertThat(result).hasSize(1);
        TurSNSiteFieldProfileConfigDto dto = result.getFirst();
        assertThat(dto.effective().facet()).isEqualTo(1);          // from override
        assertThat(dto.effective().facetName()).isEqualTo("Cátedras");
        assertThat(dto.global().facet()).isZero();                 // untouched global
        assertThat(dto.overridden().facet()).isTrue();
        assertThat(dto.overridden().facetName()).isTrue();
        assertThat(dto.overridden().facetSort()).isFalse();        // inherited
    }

    @Test
    void defaultProfileNeverReadsOverridesAndEffectiveEqualsGlobal() {
        when(turSNSiteRepository.findById(SITE_ID)).thenReturn(Optional.of(site()));
        when(turProfileRepository.findById(DEFAULT_ID)).thenReturn(Optional.of(profile(DEFAULT_ID, true)));
        when(turSNSiteFieldExtRepository.findByTurSNSite(any(), any())).thenReturn(List.of(field(1)));

        List<TurSNSiteFieldProfileConfigDto> result = service.getEffectiveConfigs(SITE_ID, DEFAULT_ID);

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().effective().facet()).isEqualTo(1);
        assertThat(result.getFirst().overridden().facet()).isFalse();
        // No override lookup for the default profile (D3).
        verify(turProfileFieldConfigRepository, never()).findByProfileId(any());
    }

    @Test
    void resolveEffectiveReusesTheSameMergeForRuntime() {
        TurProfile profile = profile(PROFILE_ID, false);
        TurSNSiteFieldExt field = field(0);
        when(turProfileFieldConfigRepository.findByProfileId(PROFILE_ID))
                .thenReturn(List.of(override(profile, field, 1, null)));

        Map<String, FacetConfig> effective = service.resolveEffective(profile, List.of(field));

        assertThat(effective).containsOnlyKeys(FIELD_ID);
        assertThat(effective.get(FIELD_ID).facet()).isEqualTo(1);          // override
        assertThat(effective.get(FIELD_ID).facetName()).isEqualTo("catedras"); // inherited global
    }

    // ------------------------------------------------------------------- writes

    @Test
    void saveOverrideUpsertsForNonDefaultProfile() {
        TurProfile profile = profile(PROFILE_ID, false);
        TurSNSiteFieldExt field = field(1);
        when(turSNSiteRepository.findById(SITE_ID)).thenReturn(Optional.of(site()));
        when(turProfileRepository.findById(PROFILE_ID)).thenReturn(Optional.of(profile));
        when(turSNSiteFieldExtRepository.findById(FIELD_ID)).thenReturn(Optional.of(field));
        when(turProfileFieldConfigRepository.findByTurProfileAndTurSNSiteFieldExt(profile, field))
                .thenReturn(Optional.empty());

        TurSNSiteFieldProfileConfigDto dto = service.saveOverride(SITE_ID, PROFILE_ID, FIELD_ID,
                new TurProfileFieldConfigRequest(0, null, null, null, null, null, null, null));

        verify(turProfileFieldConfigRepository).save(any(TurProfileFieldConfig.class));
        verify(turSNSiteFieldExtRepository, never()).save(any());
        assertThat(dto.effective().facet()).isZero();          // override facet=0 wins over global 1
        assertThat(dto.overridden().facet()).isTrue();
    }

    @Test
    void saveOverrideWithNoValuesRemovesExistingOverride() {
        TurProfile profile = profile(PROFILE_ID, false);
        TurSNSiteFieldExt field = field(1);
        TurProfileFieldConfig existing = override(profile, field, 1, null);
        when(turSNSiteRepository.findById(SITE_ID)).thenReturn(Optional.of(site()));
        when(turProfileRepository.findById(PROFILE_ID)).thenReturn(Optional.of(profile));
        when(turSNSiteFieldExtRepository.findById(FIELD_ID)).thenReturn(Optional.of(field));
        when(turProfileFieldConfigRepository.findByTurProfileAndTurSNSiteFieldExt(profile, field))
                .thenReturn(Optional.of(existing));

        TurSNSiteFieldProfileConfigDto dto = service.saveOverride(SITE_ID, PROFILE_ID, FIELD_ID,
                new TurProfileFieldConfigRequest(null, null, null, null, null, null, null, null));

        verify(turProfileFieldConfigRepository).delete(existing);
        verify(turProfileFieldConfigRepository, never()).save(any());
        assertThat(dto.overridden().facet()).isFalse();        // back to inheriting global
    }

    @Test
    void saveOverrideForDefaultProfileWritesToGlobalField() {
        TurProfile def = profile(DEFAULT_ID, true);
        TurSNSiteFieldExt field = field(0);
        when(turSNSiteRepository.findById(SITE_ID)).thenReturn(Optional.of(site()));
        when(turProfileRepository.findById(DEFAULT_ID)).thenReturn(Optional.of(def));
        when(turSNSiteFieldExtRepository.findById(FIELD_ID)).thenReturn(Optional.of(field));
        when(turSNSiteFieldExtRepository.findMaxFacetPosition(any())).thenReturn(Optional.of(2));

        service.saveOverride(SITE_ID, DEFAULT_ID, FIELD_ID,
                new TurProfileFieldConfigRequest(1, "Cátedras", null, null, null, null, null, null));

        verify(turSNSiteFieldExtRepository).save(field);
        verify(turProfileFieldConfigRepository, never()).save(any());
        assertThat(field.getFacet()).isEqualTo(1);
        assertThat(field.getFacetName()).isEqualTo("Cátedras");
        assertThat(field.getFacetPosition()).isEqualTo(3);     // max(2)+1
    }

    @Test
    void clearOverrideDeletesRowForNonDefaultProfile() {
        TurProfile profile = profile(PROFILE_ID, false);
        TurSNSiteFieldExt field = field(1);
        TurProfileFieldConfig existing = override(profile, field, 0, null);
        when(turSNSiteRepository.findById(SITE_ID)).thenReturn(Optional.of(site()));
        when(turProfileRepository.findById(PROFILE_ID)).thenReturn(Optional.of(profile));
        when(turSNSiteFieldExtRepository.findById(FIELD_ID)).thenReturn(Optional.of(field));
        when(turProfileFieldConfigRepository.findByTurProfileAndTurSNSiteFieldExt(profile, field))
                .thenReturn(Optional.of(existing));

        assertThat(service.clearOverride(SITE_ID, PROFILE_ID, FIELD_ID)).isTrue();
        verify(turProfileFieldConfigRepository).delete(existing);
    }

    @Test
    void clearOverrideOnDefaultProfileIsRejected() {
        when(turSNSiteRepository.findById(SITE_ID)).thenReturn(Optional.of(site()));
        when(turProfileRepository.findById(DEFAULT_ID)).thenReturn(Optional.of(profile(DEFAULT_ID, true)));

        assertThatThrownBy(() -> service.clearOverride(SITE_ID, DEFAULT_ID, FIELD_ID))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("default profile");
    }
}
