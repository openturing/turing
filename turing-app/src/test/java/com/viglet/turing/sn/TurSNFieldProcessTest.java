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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.viglet.turing.persistence.model.sn.TurSNSite;
import com.viglet.turing.persistence.model.sn.field.TurSNSiteCustomFacet;
import com.viglet.turing.persistence.model.sn.field.TurSNSiteFieldExt;
import com.viglet.turing.persistence.repository.sn.TurSNSiteRepository;
import com.viglet.turing.persistence.repository.sn.field.TurSNSiteFieldExtRepository;
import com.viglet.turing.sn.facet.TurSNFacetDefinitionFactory;

@ExtendWith(MockitoExtension.class)
class TurSNFieldProcessTest {

    @Mock
    private TurSNSiteRepository turSNSiteRepository;

    @Mock
    private TurSNSiteFieldExtRepository turSNSiteFieldExtRepository;

    private TurSNFieldProcess turSNFieldProcess;

    @BeforeEach
    void setUp() {
        turSNFieldProcess = new TurSNFieldProcess(
                turSNSiteRepository,
                turSNSiteFieldExtRepository,
                new TurSNFacetDefinitionFactory());
    }

    @Test
    void testGetTurSNSiteFieldOrderingWithFieldAndCustomFacet() {
        String snSiteId = "site-123";
        TurSNSite site = new TurSNSite();
        site.setId(snSiteId);

        TurSNSiteFieldExt fieldFacet = new TurSNSiteFieldExt();
        fieldFacet.setId("field-1");
        fieldFacet.setName("category");
        fieldFacet.setFacetName("Category");
        fieldFacet.setFacet(1);
        fieldFacet.setEnabled(1);
        fieldFacet.setFacetPosition(1);
        fieldFacet.setTurSNSite(site);

        TurSNSiteCustomFacet customFacet = new TurSNSiteCustomFacet();
        customFacet.setId("custom-1");
        customFacet.setName("price_range");
        HashMap<String, String> labels = new HashMap<>();
        labels.put("pt-BR", "Faixa de Preço");
        customFacet.setLabel(labels);

        TurSNSiteFieldExt fieldWithCustomFacet = new TurSNSiteFieldExt();
        fieldWithCustomFacet.setId("field-2");
        fieldWithCustomFacet.setName("price");
        fieldWithCustomFacet.setFacet(0);
        fieldWithCustomFacet.setEnabled(1);
        fieldWithCustomFacet.setTurSNSite(site);
        fieldWithCustomFacet.setCustomFacets(Set.of(customFacet));

        when(turSNSiteRepository.findById(snSiteId)).thenReturn(Optional.of(site));
        when(turSNSiteFieldExtRepository.findByTurSNSiteAndEnabled(site, 1))
                .thenReturn(List.of(fieldWithCustomFacet, fieldFacet));

        Optional<List<TurSNSiteFieldExt>> result = turSNFieldProcess.getTurSNSiteFieldOrdering(snSiteId);

        assertThat(result).isPresent();
        assertThat(result.get()).hasSize(2);
        assertThat(result.get().get(0).getName()).isEqualTo("category");
        assertThat(result.get().get(1).getName()).isEqualTo("price_range");
        assertThat(result.get().get(1).getFacetName()).isEqualTo("Faixa de Preço");
        assertThat(result.get().get(1).getFacetPosition()).isEqualTo(Integer.MAX_VALUE);

        verify(turSNSiteRepository).findById(snSiteId);
        verify(turSNSiteFieldExtRepository).findByTurSNSiteAndEnabled(site, 1);
    }

    @Test
    void testGetTurSNSiteFieldOrderingUsesDefaultLabelBeforeAnyLocale() {
        String snSiteId = "site-456";
        TurSNSite site = new TurSNSite();
        site.setId(snSiteId);

        TurSNSiteCustomFacet customFacet = new TurSNSiteCustomFacet();
        customFacet.setId("custom-2");
        customFacet.setName("status_bucket");
        customFacet.setDefaultLabel("Status");
        HashMap<String, String> labels = new HashMap<>();
        labels.put("en-US", "Status EN");
        labels.put("pt-BR", "Status PT");
        customFacet.setLabel(labels);

        TurSNSiteFieldExt fieldWithCustomFacet = new TurSNSiteFieldExt();
        fieldWithCustomFacet.setId("field-3");
        fieldWithCustomFacet.setName("status");
        fieldWithCustomFacet.setFacet(0);
        fieldWithCustomFacet.setEnabled(1);
        fieldWithCustomFacet.setTurSNSite(site);
        fieldWithCustomFacet.setCustomFacets(Set.of(customFacet));

        when(turSNSiteRepository.findById(snSiteId)).thenReturn(Optional.of(site));
        when(turSNSiteFieldExtRepository.findByTurSNSiteAndEnabled(site, 1))
                .thenReturn(List.of(fieldWithCustomFacet));

        Optional<List<TurSNSiteFieldExt>> result = turSNFieldProcess.getTurSNSiteFieldOrdering(snSiteId);

        assertThat(result).isPresent();
        assertThat(result.get()).hasSize(1);
        assertThat(result.get().get(0).getFacetName()).isEqualTo("Status");
    }

    @Test
    void testGetTurSNSiteFieldOrderingWithNonExistentSite() {
        when(turSNSiteRepository.findById("missing-site")).thenReturn(Optional.empty());

        Optional<List<TurSNSiteFieldExt>> result = turSNFieldProcess
                .getTurSNSiteFieldOrdering("missing-site");

        assertThat(result).isEmpty();
        verify(turSNSiteRepository).findById("missing-site");
        verify(turSNSiteFieldExtRepository, never()).findByTurSNSiteAndEnabled(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyInt());
    }
}
