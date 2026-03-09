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

package com.viglet.turing.persistence.model.sn.field;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.viglet.turing.commons.se.field.TurSEFieldType;
import com.viglet.turing.persistence.model.sn.TurSNSite;
import com.viglet.turing.persistence.model.sn.TurSNSiteFacetRangeEnum;
import com.viglet.turing.sn.TurSNFieldType;

/**
 * Unit tests for TurSNSiteFieldExt.
 *
 * @author Alexandre Oliveira
 * @since 2025.1.10
 */
@ExtendWith(MockitoExtension.class)
class TurSNSiteFieldExtTest {

    @Mock
    private TurSNSite turSNSite;

    private TurSNSiteFieldExt turSNSiteFieldExt;

    @BeforeEach
    void setUp() {
        turSNSiteFieldExt = new TurSNSiteFieldExt();
    }

    @Test
    void testGettersAndSetters() {
        String id = "field-id-123";
        String externalId = "ext-123";
        String name = "testField";
        String description = "Test field description";
        String facetName = "Test Facet";
        Integer facetPosition = 5;
        Boolean secondaryFacet = true;
        Boolean showAllFacetItems = false;
        String defaultValue = "default";

        turSNSiteFieldExt.setId(id);
        turSNSiteFieldExt.setExternalId(externalId);
        turSNSiteFieldExt.setName(name);
        turSNSiteFieldExt.setDescription(description);
        turSNSiteFieldExt.setFacetName(facetName);
        turSNSiteFieldExt.setFacetRange(TurSNSiteFacetRangeEnum.YEAR);
        turSNSiteFieldExt.setFacetType(TurSNSiteFacetFieldEnum.AND);
        turSNSiteFieldExt.setFacetItemType(TurSNSiteFacetFieldEnum.OR);
        turSNSiteFieldExt.setFacetSort(TurSNSiteFacetFieldSortEnum.COUNT);
        turSNSiteFieldExt.setFacetPosition(facetPosition);
        turSNSiteFieldExt.setSecondaryFacet(secondaryFacet);
        turSNSiteFieldExt.setShowAllFacetItems(showAllFacetItems);
        turSNSiteFieldExt.setSnType(TurSNFieldType.SE);
        turSNSiteFieldExt.setType(TurSEFieldType.STRING);
        turSNSiteFieldExt.setMultiValued(1);
        turSNSiteFieldExt.setFacet(1);
        turSNSiteFieldExt.setHl(1);
        turSNSiteFieldExt.setMlt(1);
        turSNSiteFieldExt.setEnabled(1);
        turSNSiteFieldExt.setRequired(1);
        turSNSiteFieldExt.setDefaultValue(defaultValue);
        turSNSiteFieldExt.setTurSNSite(turSNSite);

        assertThat(turSNSiteFieldExt.getId()).isEqualTo(id);
        assertThat(turSNSiteFieldExt.getExternalId()).isEqualTo(externalId);
        assertThat(turSNSiteFieldExt.getName()).isEqualTo(name);
        assertThat(turSNSiteFieldExt.getDescription()).isEqualTo(description);
        assertThat(turSNSiteFieldExt.getFacetName()).isEqualTo(facetName);
        assertThat(turSNSiteFieldExt.getFacetRange()).isEqualTo(TurSNSiteFacetRangeEnum.YEAR);
        assertThat(turSNSiteFieldExt.getFacetType()).isEqualTo(TurSNSiteFacetFieldEnum.AND);
        assertThat(turSNSiteFieldExt.getFacetItemType()).isEqualTo(TurSNSiteFacetFieldEnum.OR);
        assertThat(turSNSiteFieldExt.getFacetSort()).isEqualTo(TurSNSiteFacetFieldSortEnum.COUNT);
        assertThat(turSNSiteFieldExt.getFacetPosition()).isEqualTo(facetPosition);
        assertThat(turSNSiteFieldExt.getSecondaryFacet()).isEqualTo(secondaryFacet);
        assertThat(turSNSiteFieldExt.getShowAllFacetItems()).isEqualTo(showAllFacetItems);
        assertThat(turSNSiteFieldExt.getSnType()).isEqualTo(TurSNFieldType.SE);
        assertThat(turSNSiteFieldExt.getType()).isEqualTo(TurSEFieldType.STRING);
        assertThat(turSNSiteFieldExt.getMultiValued()).isEqualTo(1);
        assertThat(turSNSiteFieldExt.getFacet()).isEqualTo(1);
        assertThat(turSNSiteFieldExt.getHl()).isEqualTo(1);
        assertThat(turSNSiteFieldExt.getMlt()).isEqualTo(1);
        assertThat(turSNSiteFieldExt.getEnabled()).isEqualTo(1);
        assertThat(turSNSiteFieldExt.getRequired()).isEqualTo(1);
        assertThat(turSNSiteFieldExt.getDefaultValue()).isEqualTo(defaultValue);
        assertThat(turSNSiteFieldExt.getTurSNSite()).isEqualTo(turSNSite);
    }

    @Test
    void testBuilder() {
        TurSNSiteFieldExt fieldExt = TurSNSiteFieldExt.builder()
                .id("field-123")
                .externalId("ext-123")
                .name("testField")
                .description("Test description")
                .facetName("Test Facet")
                .facetRange(TurSNSiteFacetRangeEnum.MONTH)
                .facetType(TurSNSiteFacetFieldEnum.AND)
                .facetItemType(TurSNSiteFacetFieldEnum.OR)
                .facetSort(TurSNSiteFacetFieldSortEnum.ALPHABETICAL)
                .facetPosition(10)
                .secondaryFacet(false)
                .showAllFacetItems(true)
                .snType(TurSNFieldType.NER)
                .type(TurSEFieldType.INT)
                .multiValued(0)
                .facet(1)
                .hl(0)
                .mlt(1)
                .enabled(1)
                .required(0)
                .defaultValue("0")
                .turSNSite(turSNSite)
                .build();

        assertThat(fieldExt.getId()).isEqualTo("field-123");
        assertThat(fieldExt.getExternalId()).isEqualTo("ext-123");
        assertThat(fieldExt.getName()).isEqualTo("testField");
        assertThat(fieldExt.getDescription()).isEqualTo("Test description");
        assertThat(fieldExt.getFacetName()).isEqualTo("Test Facet");
        assertThat(fieldExt.getFacetRange()).isEqualTo(TurSNSiteFacetRangeEnum.MONTH);
        assertThat(fieldExt.getFacetType()).isEqualTo(TurSNSiteFacetFieldEnum.AND);
        assertThat(fieldExt.getFacetItemType()).isEqualTo(TurSNSiteFacetFieldEnum.OR);
        assertThat(fieldExt.getFacetSort()).isEqualTo(TurSNSiteFacetFieldSortEnum.ALPHABETICAL);
        assertThat(fieldExt.getFacetPosition()).isEqualTo(10);
        assertThat(fieldExt.getSecondaryFacet()).isFalse();
        assertThat(fieldExt.getShowAllFacetItems()).isTrue();
        assertThat(fieldExt.getSnType()).isEqualTo(TurSNFieldType.NER);
        assertThat(fieldExt.getType()).isEqualTo(TurSEFieldType.INT);
        assertThat(fieldExt.getTurSNSite()).isEqualTo(turSNSite);
    }

    @Test
    void testBuilderDefaults() {
        TurSNSiteFieldExt fieldExt = TurSNSiteFieldExt.builder()
                .externalId("ext-123")
                .name("testField")
                .snType(TurSNFieldType.SE)
                .type(TurSEFieldType.STRING)
                .turSNSite(turSNSite)
                .build();

        assertThat(fieldExt.getFacetRange()).isEqualTo(TurSNSiteFacetRangeEnum.DISABLED);
        assertThat(fieldExt.getFacetType()).isEqualTo(TurSNSiteFacetFieldEnum.DEFAULT);
        assertThat(fieldExt.getFacetItemType()).isEqualTo(TurSNSiteFacetFieldEnum.DEFAULT);
        assertThat(fieldExt.getFacetSort()).isEqualTo(TurSNSiteFacetFieldSortEnum.DEFAULT);
        assertThat(fieldExt.getFacetLocales()).isNotNull().isEmpty();
    }

    @Test
    void testToBuilder() {
        TurSNSiteFieldExt original = TurSNSiteFieldExt.builder()
                .id("field-123")
                .name("originalField")
                .snType(TurSNFieldType.SE)
                .type(TurSEFieldType.STRING)
                .turSNSite(turSNSite)
                .build();

        TurSNSiteFieldExt modified = original.toBuilder()
                .name("modifiedField")
                .build();

        assertThat(modified.getId()).isEqualTo("field-123");
        assertThat(modified.getName()).isEqualTo("modifiedField");
        assertThat(modified.getSnType()).isEqualTo(TurSNFieldType.SE);
    }

    @Test
    void testFacetLocalesInitialization() {
        assertThat(turSNSiteFieldExt.getFacetLocales()).isNotNull();
    }

    @Test
    void testSetFacetLocalesWithNull() {
        turSNSiteFieldExt.setFacetLocales(null);
        assertThat(turSNSiteFieldExt.getFacetLocales()).isEmpty();
    }

    @Test
    void testSetFacetLocalesWithValues() {
        TurSNSiteFieldExtFacet facet1 = new TurSNSiteFieldExtFacet();
        TurSNSiteFieldExtFacet facet2 = new TurSNSiteFieldExtFacet();
        Set<TurSNSiteFieldExtFacet> facets = new HashSet<>();
        facets.add(facet1);
        facets.add(facet2);

        turSNSiteFieldExt.setFacetLocales(facets);
        assertThat(turSNSiteFieldExt.getFacetLocales()).hasSize(2);
    }

    @Test
    void testSetFacetLocalesClearsExisting() {
        TurSNSiteFieldExtFacet facet1 = new TurSNSiteFieldExtFacet();
        Set<TurSNSiteFieldExtFacet> facets1 = new HashSet<>();
        facets1.add(facet1);
        turSNSiteFieldExt.setFacetLocales(facets1);

        TurSNSiteFieldExtFacet facet2 = new TurSNSiteFieldExtFacet();
        Set<TurSNSiteFieldExtFacet> facets2 = new HashSet<>();
        facets2.add(facet2);
        turSNSiteFieldExt.setFacetLocales(facets2);

        assertThat(turSNSiteFieldExt.getFacetLocales()).hasSize(1).contains(facet2);
    }

    @Test
    void testIntegerFieldValues() {
        turSNSiteFieldExt.setMultiValued(1);
        turSNSiteFieldExt.setFacet(0);
        turSNSiteFieldExt.setHl(1);
        turSNSiteFieldExt.setMlt(0);
        turSNSiteFieldExt.setEnabled(1);
        turSNSiteFieldExt.setRequired(0);

        assertThat(turSNSiteFieldExt.getMultiValued()).isEqualTo(1);
        assertThat(turSNSiteFieldExt.getFacet()).isZero();
        assertThat(turSNSiteFieldExt.getHl()).isEqualTo(1);
        assertThat(turSNSiteFieldExt.getMlt()).isZero();
        assertThat(turSNSiteFieldExt.getEnabled()).isEqualTo(1);
        assertThat(turSNSiteFieldExt.getRequired()).isZero();
    }

    @Test
    void testAllFacetRangeEnums() {
        turSNSiteFieldExt.setFacetRange(TurSNSiteFacetRangeEnum.DISABLED);
        assertThat(turSNSiteFieldExt.getFacetRange()).isEqualTo(TurSNSiteFacetRangeEnum.DISABLED);

        turSNSiteFieldExt.setFacetRange(TurSNSiteFacetRangeEnum.YEAR);
        assertThat(turSNSiteFieldExt.getFacetRange()).isEqualTo(TurSNSiteFacetRangeEnum.YEAR);

        turSNSiteFieldExt.setFacetRange(TurSNSiteFacetRangeEnum.MONTH);
        assertThat(turSNSiteFieldExt.getFacetRange()).isEqualTo(TurSNSiteFacetRangeEnum.MONTH);
    }

    @Test
    void testAllFacetFieldEnums() {
        turSNSiteFieldExt.setFacetType(TurSNSiteFacetFieldEnum.DEFAULT);
        assertThat(turSNSiteFieldExt.getFacetType()).isEqualTo(TurSNSiteFacetFieldEnum.DEFAULT);

        turSNSiteFieldExt.setFacetType(TurSNSiteFacetFieldEnum.AND);
        assertThat(turSNSiteFieldExt.getFacetType()).isEqualTo(TurSNSiteFacetFieldEnum.AND);

        turSNSiteFieldExt.setFacetType(TurSNSiteFacetFieldEnum.OR);
        assertThat(turSNSiteFieldExt.getFacetType()).isEqualTo(TurSNSiteFacetFieldEnum.OR);
    }

    @Test
    void testFieldTypes() {
        turSNSiteFieldExt.setSnType(TurSNFieldType.SE);
        turSNSiteFieldExt.setType(TurSEFieldType.STRING);
        assertThat(turSNSiteFieldExt.getSnType()).isEqualTo(TurSNFieldType.SE);
        assertThat(turSNSiteFieldExt.getType()).isEqualTo(TurSEFieldType.STRING);

        turSNSiteFieldExt.setSnType(TurSNFieldType.NER);
        turSNSiteFieldExt.setType(TurSEFieldType.INT);
        assertThat(turSNSiteFieldExt.getSnType()).isEqualTo(TurSNFieldType.NER);
        assertThat(turSNSiteFieldExt.getType()).isEqualTo(TurSEFieldType.INT);
    }

    @Test
    void testNullableFields() {
        turSNSiteFieldExt.setDescription(null);
        turSNSiteFieldExt.setFacetName(null);
        turSNSiteFieldExt.setFacetPosition(null);
        turSNSiteFieldExt.setSecondaryFacet(null);
        turSNSiteFieldExt.setShowAllFacetItems(null);
        turSNSiteFieldExt.setDefaultValue(null);

        assertThat(turSNSiteFieldExt.getDescription()).isNull();
        assertThat(turSNSiteFieldExt.getFacetName()).isNull();
        assertThat(turSNSiteFieldExt.getFacetPosition()).isNull();
        assertThat(turSNSiteFieldExt.getSecondaryFacet()).isNull();
        assertThat(turSNSiteFieldExt.getShowAllFacetItems()).isNull();
        assertThat(turSNSiteFieldExt.getDefaultValue()).isNull();
    }
}
