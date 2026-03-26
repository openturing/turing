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

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;

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
import com.viglet.turing.sn.facet.TurSNFacetDefinition;

/**
 * Tests for TurSNFieldProcess.
 *
 * @author Alexandre Oliveira
 * @since 2026.1.14
 */
@ExtendWith(MockitoExtension.class)
class TurSNFieldProcessTest {

        @Mock
        private TurSNSiteRepository turSNSiteRepository;

        @Mock
        private TurSNSiteFieldExtRepository turSNSiteFieldExtRepository;

        @Mock
        private com.viglet.turing.persistence.repository.sn.field.TurSNSiteCustomFacetRepository turSNSiteCustomFacetRepository;

        private TurSNFieldProcess turSNFieldProcess;

        @BeforeEach
        void setUp() {
                turSNFieldProcess = new TurSNFieldProcess(
                                turSNSiteRepository,
                                turSNSiteFieldExtRepository,
                                new com.viglet.turing.sn.facet.TurSNFacetDefinitionFactory(
                                                turSNSiteCustomFacetRepository));
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
                customFacet.setTurSNSiteFieldExt(fieldWithCustomFacet);

                when(turSNSiteRepository.findById(snSiteId)).thenReturn(Optional.of(site));
                when(turSNSiteFieldExtRepository.findByTurSNSiteAndEnabled(site, 1))
                                .thenReturn(List.of(fieldWithCustomFacet, fieldFacet));
                when(turSNSiteCustomFacetRepository.findByFieldExtsWithDetails(org.mockito.ArgumentMatchers.any()))
                                .thenReturn(List.of(customFacet));

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
                customFacet.setTurSNSiteFieldExt(fieldWithCustomFacet);

                when(turSNSiteRepository.findById(snSiteId)).thenReturn(Optional.of(site));
                when(turSNSiteFieldExtRepository.findByTurSNSiteAndEnabled(site, 1))
                                .thenReturn(List.of(fieldWithCustomFacet));
                when(turSNSiteCustomFacetRepository.findByFieldExtsWithDetails(org.mockito.ArgumentMatchers.any()))
                                .thenReturn(List.of(customFacet));

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

        @Test
        void testGetTurSNSiteFacetOrderingWithNonExistentSite() {
                when(turSNSiteRepository.findById("no-site")).thenReturn(Optional.empty());

                Optional<List<TurSNFacetDefinition>> result = turSNFieldProcess
                                .getTurSNSiteFacetOrdering("no-site");

                assertThat(result).isEmpty();
        }

        @Test
        void testGetTurSNSiteFacetOrderingReturnsPresent() {
                String snSiteId = "site-facet";
                TurSNSite site = new TurSNSite();
                site.setId(snSiteId);

                TurSNSiteFieldExt field = new TurSNSiteFieldExt();
                field.setId("f1");
                field.setName("author");
                field.setFacetName("Author");
                field.setFacet(1);
                field.setEnabled(1);
                field.setFacetPosition(2);
                field.setTurSNSite(site);

                when(turSNSiteRepository.findById(snSiteId)).thenReturn(Optional.of(site));
                when(turSNSiteFieldExtRepository.findByTurSNSiteAndEnabled(site, 1))
                                .thenReturn(List.of(field));
                when(turSNSiteCustomFacetRepository.findByFieldExtsWithDetails(org.mockito.ArgumentMatchers.any()))
                                .thenReturn(Collections.emptyList());

                Optional<List<TurSNFacetDefinition>> result = turSNFieldProcess
                                .getTurSNSiteFacetOrdering(snSiteId);

                assertThat(result).isPresent();
                assertThat(result.get()).hasSize(1);
                assertThat(result.get().get(0).getName()).isEqualTo("author");
                assertThat(result.get().get(0).getLabel()).isEqualTo("Author");
                assertThat(result.get().get(0).getPosition()).isEqualTo(2);
        }

        @Test
        void testGetTurSNSiteFacetOrderingSortsByPositionThenLabel() {
                String snSiteId = "site-sort";
                TurSNSite site = new TurSNSite();
                site.setId(snSiteId);

                TurSNSiteFieldExt fieldA = new TurSNSiteFieldExt();
                fieldA.setId("fa");
                fieldA.setName("zeta");
                fieldA.setFacetName("Zeta");
                fieldA.setFacet(1);
                fieldA.setEnabled(1);
                fieldA.setFacetPosition(1);
                fieldA.setTurSNSite(site);

                TurSNSiteFieldExt fieldB = new TurSNSiteFieldExt();
                fieldB.setId("fb");
                fieldB.setName("alpha");
                fieldB.setFacetName("Alpha");
                fieldB.setFacet(1);
                fieldB.setEnabled(1);
                fieldB.setFacetPosition(1);
                fieldB.setTurSNSite(site);

                TurSNSiteFieldExt fieldC = new TurSNSiteFieldExt();
                fieldC.setId("fc");
                fieldC.setName("beta");
                fieldC.setFacetName("Beta");
                fieldC.setFacet(1);
                fieldC.setEnabled(1);
                fieldC.setFacetPosition(0);
                fieldC.setTurSNSite(site);

                when(turSNSiteRepository.findById(snSiteId)).thenReturn(Optional.of(site));
                when(turSNSiteFieldExtRepository.findByTurSNSiteAndEnabled(site, 1))
                                .thenReturn(List.of(fieldA, fieldB, fieldC));
                when(turSNSiteCustomFacetRepository.findByFieldExtsWithDetails(org.mockito.ArgumentMatchers.any()))
                                .thenReturn(Collections.emptyList());

                Optional<List<TurSNFacetDefinition>> result = turSNFieldProcess
                                .getTurSNSiteFacetOrdering(snSiteId);

                assertThat(result).isPresent();
                List<TurSNFacetDefinition> defs = result.get();
                assertThat(defs).hasSize(3);
                // Position 1 first (sorted by label), then position 0 maps to MAX_VALUE
                assertThat(defs.get(0).getName()).isEqualTo("alpha");
                assertThat(defs.get(1).getName()).isEqualTo("zeta");
                assertThat(defs.get(2).getName()).isEqualTo("beta");
        }

        @Test
        void testGetTurSNSiteFieldOrderingWithEmptyEnabledFields() {
                String snSiteId = "site-empty";
                TurSNSite site = new TurSNSite();
                site.setId(snSiteId);

                when(turSNSiteRepository.findById(snSiteId)).thenReturn(Optional.of(site));
                when(turSNSiteFieldExtRepository.findByTurSNSiteAndEnabled(site, 1))
                                .thenReturn(Collections.emptyList());

                Optional<List<TurSNSiteFieldExt>> result = turSNFieldProcess
                                .getTurSNSiteFieldOrdering(snSiteId);

                assertThat(result).isPresent();
                assertThat(result.get()).isEmpty();
        }

        @Test
        void testGetTurSNSiteFieldOrderingWithEnabledFieldsButNoFacets() {
                String snSiteId = "site-nofacet";
                TurSNSite site = new TurSNSite();
                site.setId(snSiteId);

                TurSNSiteFieldExt nonFacetField = new TurSNSiteFieldExt();
                nonFacetField.setId("nf1");
                nonFacetField.setName("content");
                nonFacetField.setFacet(0);
                nonFacetField.setEnabled(1);
                nonFacetField.setTurSNSite(site);

                when(turSNSiteRepository.findById(snSiteId)).thenReturn(Optional.of(site));
                when(turSNSiteFieldExtRepository.findByTurSNSiteAndEnabled(site, 1))
                                .thenReturn(List.of(nonFacetField));
                when(turSNSiteCustomFacetRepository.findByFieldExtsWithDetails(org.mockito.ArgumentMatchers.any()))
                                .thenReturn(Collections.emptyList());

                Optional<List<TurSNSiteFieldExt>> result = turSNFieldProcess
                                .getTurSNSiteFieldOrdering(snSiteId);

                assertThat(result).isPresent();
                assertThat(result.get()).isEmpty();
        }

        @Test
        void testGetTurSNSiteFacetOrderingNullLabelsSortedLast() {
                String snSiteId = "site-nulllabel";
                TurSNSite site = new TurSNSite();
                site.setId(snSiteId);

                TurSNSiteFieldExt fieldWithLabel = new TurSNSiteFieldExt();
                fieldWithLabel.setId("fl1");
                fieldWithLabel.setName("labeled");
                fieldWithLabel.setFacetName("Abc");
                fieldWithLabel.setFacet(1);
                fieldWithLabel.setEnabled(1);
                fieldWithLabel.setFacetPosition(1);
                fieldWithLabel.setTurSNSite(site);

                TurSNSiteFieldExt fieldNoLabel = new TurSNSiteFieldExt();
                fieldNoLabel.setId("fl2");
                fieldNoLabel.setName("nolabel");
                fieldNoLabel.setFacetName(null);
                fieldNoLabel.setFacet(1);
                fieldNoLabel.setEnabled(1);
                fieldNoLabel.setFacetPosition(1);
                fieldNoLabel.setTurSNSite(site);

                when(turSNSiteRepository.findById(snSiteId)).thenReturn(Optional.of(site));
                when(turSNSiteFieldExtRepository.findByTurSNSiteAndEnabled(site, 1))
                                .thenReturn(List.of(fieldNoLabel, fieldWithLabel));
                when(turSNSiteCustomFacetRepository.findByFieldExtsWithDetails(org.mockito.ArgumentMatchers.any()))
                                .thenReturn(Collections.emptyList());

                Optional<List<TurSNFacetDefinition>> result = turSNFieldProcess
                                .getTurSNSiteFacetOrdering(snSiteId);

                assertThat(result).isPresent();
                List<TurSNFacetDefinition> defs = result.get();
                assertThat(defs).hasSize(2);
                // Labeled field first (nullsLast comparator)
                assertThat(defs.get(0).getName()).isEqualTo("labeled");
                assertThat(defs.get(1).getName()).isEqualTo("nolabel");
        }

        @Test
        void testGetTurSNSiteFieldOrderingMapsFieldExtCorrectly() {
                String snSiteId = "site-mapping";
                TurSNSite site = new TurSNSite();
                site.setId(snSiteId);

                TurSNSiteFieldExt field = new TurSNSiteFieldExt();
                field.setId("map-1");
                field.setName("category");
                field.setFacetName("Category Label");
                field.setFacet(1);
                field.setEnabled(1);
                field.setFacetPosition(5);
                field.setTurSNSite(site);

                when(turSNSiteRepository.findById(snSiteId)).thenReturn(Optional.of(site));
                when(turSNSiteFieldExtRepository.findByTurSNSiteAndEnabled(site, 1))
                                .thenReturn(List.of(field));
                when(turSNSiteCustomFacetRepository.findByFieldExtsWithDetails(org.mockito.ArgumentMatchers.any()))
                                .thenReturn(Collections.emptyList());

                Optional<List<TurSNSiteFieldExt>> result = turSNFieldProcess
                                .getTurSNSiteFieldOrdering(snSiteId);

                assertThat(result).isPresent();
                TurSNSiteFieldExt mapped = result.get().get(0);
                assertThat(mapped.getName()).isEqualTo("category");
                assertThat(mapped.getFacetName()).isEqualTo("Category Label");
                assertThat(mapped.getFacetPosition()).isEqualTo(5);
                assertThat(mapped.getFacet()).isEqualTo(1);
                assertThat(mapped.getEnabled()).isEqualTo(1);
                assertThat(mapped.getTurSNSite()).isEqualTo(site);
        }

        @Test
        void testGetTurSNSiteFacetOrderingWithMultipleCustomFacetsOnSameField() {
                String snSiteId = "site-multi-custom";
                TurSNSite site = new TurSNSite();
                site.setId(snSiteId);

                TurSNSiteFieldExt field = new TurSNSiteFieldExt();
                field.setId("field-mc");
                field.setName("price");
                field.setFacet(0);
                field.setEnabled(1);
                field.setTurSNSite(site);

                TurSNSiteCustomFacet custom1 = new TurSNSiteCustomFacet();
                custom1.setId("c1");
                custom1.setName("price_low");
                custom1.setDefaultLabel("Low Price");
                custom1.setTurSNSiteFieldExt(field);

                TurSNSiteCustomFacet custom2 = new TurSNSiteCustomFacet();
                custom2.setId("c2");
                custom2.setName("price_high");
                custom2.setDefaultLabel("High Price");
                custom2.setTurSNSiteFieldExt(field);

                when(turSNSiteRepository.findById(snSiteId)).thenReturn(Optional.of(site));
                when(turSNSiteFieldExtRepository.findByTurSNSiteAndEnabled(site, 1))
                                .thenReturn(List.of(field));
                when(turSNSiteCustomFacetRepository.findByFieldExtsWithDetails(org.mockito.ArgumentMatchers.any()))
                                .thenReturn(List.of(custom1, custom2));

                Optional<List<TurSNFacetDefinition>> result = turSNFieldProcess
                                .getTurSNSiteFacetOrdering(snSiteId);

                assertThat(result).isPresent();
                assertThat(result.get()).hasSize(2);
        }
}
