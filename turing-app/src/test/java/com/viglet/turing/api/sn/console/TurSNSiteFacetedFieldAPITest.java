/*
 * Copyright (C) 2016-2025 the original author or authors.
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

package com.viglet.turing.api.sn.console;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.viglet.turing.api.sn.bean.TurSNSiteFacetOrderingDto;
import com.viglet.turing.persistence.model.sn.TurSNSite;
import com.viglet.turing.persistence.model.sn.field.TurSNSiteCustomFacet;
import com.viglet.turing.persistence.model.sn.field.TurSNSiteFieldExt;
import com.viglet.turing.persistence.repository.sn.TurSNSiteRepository;
import com.viglet.turing.persistence.repository.sn.field.TurSNSiteFieldExtRepository;
import com.viglet.turing.sn.TurSNFieldProcess;
import com.viglet.turing.sn.facet.TurSNCustomFacetDefinition;
import com.viglet.turing.sn.facet.TurSNFieldFacetDefinition;

/**
 * Unit tests for TurSNSiteFacetedFieldAPI.
 *
 * @author Alexandre Oliveira
 * @since 2026.1.10
 */
class TurSNSiteFacetedFieldAPITest {

        @Test
        void testFacetFieldListReturnsEmptyWhenMissing() {
                TurSNSiteFieldExtRepository fieldExtRepository = mock(TurSNSiteFieldExtRepository.class);
                TurSNSiteRepository siteRepository = mock(TurSNSiteRepository.class);
                TurSNFieldProcess fieldProcess = mock(TurSNFieldProcess.class);
                TurSNSiteFacetedFieldAPI api = new TurSNSiteFacetedFieldAPI(fieldExtRepository, siteRepository,
                                fieldProcess);

                when(fieldProcess.getTurSNSiteFacetOrdering("site")).thenReturn(Optional.empty());

                List<TurSNSiteFacetOrderingDto> result = api.turSNSiteFacetdFieldExtList("site");

                assertThat(result).isEmpty();
        }

        @Test
        void testFacetFieldUpdateReordersPositions() {
                TurSNSiteFieldExtRepository fieldExtRepository = mock(TurSNSiteFieldExtRepository.class);
                TurSNSiteRepository siteRepository = mock(TurSNSiteRepository.class);
                TurSNFieldProcess fieldProcess = mock(TurSNFieldProcess.class);
                TurSNSiteFacetedFieldAPI api = new TurSNSiteFacetedFieldAPI(fieldExtRepository, siteRepository,
                                fieldProcess);

                TurSNSite site = new TurSNSite();

                TurSNSiteFieldExt field1 = new TurSNSiteFieldExt();
                field1.setId("1");
                field1.setName("field_1");
                field1.setFacet(1);
                field1.setFacetPosition(1);
                TurSNSiteFieldExt field2 = new TurSNSiteFieldExt();
                field2.setId("2");
                field2.setName("field_2");
                field2.setFacet(1);
                field2.setFacetPosition(2);

                TurSNSiteFacetOrderingDto update1 = new TurSNSiteFacetOrderingDto();
                update1.setId("1");
                update1.setFacetPosition(2);
                TurSNSiteFacetOrderingDto update2 = new TurSNSiteFacetOrderingDto();
                update2.setId("2");
                update2.setFacetPosition(1);

                when(siteRepository.findById("site")).thenReturn(Optional.of(site));
                when(fieldExtRepository.findByTurSNSiteAndEnabled(site, 1)).thenReturn(List.of(field1, field2));
                lenient().when(fieldProcess.getTurSNSiteFacetOrdering("site")).thenAnswer(invocation -> Optional
                                .of(List.of(field1, field2).stream()
                                                .sorted(java.util.Comparator
                                                                .comparing(TurSNSiteFieldExt::getFacetPosition))
                                                .map(field -> new TurSNFieldFacetDefinition(field, Set.of()))
                                                .toList()));

                List<TurSNSiteFacetOrderingDto> result = api.turSNSiteFieldUpdate("site", List.of(update1, update2));

                assertThat(result).extracting(TurSNSiteFacetOrderingDto::getId).containsExactly("2", "1");
        }

        @Test
        void testFacetFieldUpdateCustomFacetPositionIsIndependent() {
                TurSNSiteFieldExtRepository fieldExtRepository = mock(TurSNSiteFieldExtRepository.class);
                TurSNSiteRepository siteRepository = mock(TurSNSiteRepository.class);
                TurSNFieldProcess fieldProcess = mock(TurSNFieldProcess.class);
                TurSNSiteFacetedFieldAPI api = new TurSNSiteFacetedFieldAPI(fieldExtRepository, siteRepository,
                                fieldProcess);

                TurSNSite site = new TurSNSite();
                TurSNSiteFieldExt idField = new TurSNSiteFieldExt();
                idField.setId("field-id");
                idField.setName("id");
                idField.setFacet(1);
                idField.setFacetPosition(3);

                TurSNSiteCustomFacet customFacet = new TurSNSiteCustomFacet();
                customFacet.setId("custom-price-range");
                customFacet.setName("price_range");
                customFacet.setFacetPosition(2);
                idField.setCustomFacets(new java.util.HashSet<>(java.util.List.of(customFacet)));

                TurSNSiteFacetOrderingDto update = new TurSNSiteFacetOrderingDto();
                update.setId("custom-price-range");
                update.setFacetPosition(1);

                when(siteRepository.findById("site")).thenReturn(Optional.of(site));
                when(fieldExtRepository.findByTurSNSiteAndEnabled(site, 1)).thenReturn(List.of(idField));
                when(fieldProcess.getTurSNSiteFacetOrdering("site"))
                                .thenReturn(Optional.of(List.of(
                                                new TurSNCustomFacetDefinition(idField, customFacet, null),
                                                new TurSNFieldFacetDefinition(idField, Set.of()))));

                List<TurSNSiteFacetOrderingDto> result = api.turSNSiteFieldUpdate("site", List.of(update));

                assertThat(customFacet.getFacetPosition()).isEqualTo(1);
                assertThat(idField.getFacetPosition()).isEqualTo(3);
                assertThat(result).extracting(TurSNSiteFacetOrderingDto::getId)
                                .containsExactly("custom-price-range", "field-id");
        }
}
