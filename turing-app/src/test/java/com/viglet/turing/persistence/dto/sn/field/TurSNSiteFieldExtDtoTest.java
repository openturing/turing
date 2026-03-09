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

package com.viglet.turing.persistence.dto.sn.field;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.viglet.turing.commons.se.field.TurSEFieldType;
import com.viglet.turing.persistence.model.sn.TurSNSite;
import com.viglet.turing.persistence.model.sn.TurSNSiteFacetRangeEnum;
import com.viglet.turing.persistence.model.sn.field.TurSNSiteFacetFieldEnum;
import com.viglet.turing.persistence.model.sn.field.TurSNSiteFieldExt;
import com.viglet.turing.persistence.model.sn.field.TurSNSiteFieldExtFacet;
import com.viglet.turing.sn.TurSNFieldType;

/**
 * Unit tests for TurSNSiteFieldExtDto.
 *
 * @author Alexandre Oliveira
 * @since 2025.1.10
 */
class TurSNSiteFieldExtDtoTest {

    @Test
    void testNoArgsConstructor() {
        TurSNSiteFieldExtDto dto = new TurSNSiteFieldExtDto();

        assertThat(dto).isNotNull();
        assertThat(dto.getFacetLocales()).isNotNull().isEmpty();
    }

    @Test
    void testAllArgsConstructor() {
        Set<TurSNSiteFieldExtFacetDto> facetLocales = new HashSet<>();
        TurSNSite turSNSite = mock(TurSNSite.class);

        TurSNSiteFieldExtDto dto = new TurSNSiteFieldExtDto(
                "id-123",
                "ext-id",
                "fieldName",
                "Field Description",
                "Facet Name",
                facetLocales,
                TurSNFieldType.SE,
                TurSEFieldType.STRING,
                1,
                1,
                TurSNSiteFacetRangeEnum.DAY,
                TurSNSiteFacetFieldEnum.DEFAULT,
                TurSNSiteFacetFieldEnum.DEFAULT,
                true,
                false,
                1,
                1,
                1,
                1,
                "default",
                turSNSite);

        assertThat(dto.getId()).isEqualTo("id-123");
        assertThat(dto.getExternalId()).isEqualTo("ext-id");
        assertThat(dto.getName()).isEqualTo("fieldName");
        assertThat(dto.getDescription()).isEqualTo("Field Description");
        assertThat(dto.getFacetName()).isEqualTo("Facet Name");
        assertThat(dto.getFacetLocales()).isEqualTo(facetLocales);
        assertThat(dto.getSnType()).isEqualTo(TurSNFieldType.SE);
        assertThat(dto.getType()).isEqualTo(TurSEFieldType.STRING);
        assertThat(dto.getMultiValued()).isEqualTo(1);
        assertThat(dto.getFacet()).isEqualTo(1);
        assertThat(dto.getFacetRange()).isEqualTo(TurSNSiteFacetRangeEnum.DAY);
        assertThat(dto.getFacetType()).isEqualTo(TurSNSiteFacetFieldEnum.DEFAULT);
        assertThat(dto.getFacetItemType()).isEqualTo(TurSNSiteFacetFieldEnum.DEFAULT);
        assertThat(dto.getSecondaryFacet()).isTrue();
        assertThat(dto.getShowAllFacetItems()).isFalse();
        assertThat(dto.getHl()).isEqualTo(1);
        assertThat(dto.getMlt()).isEqualTo(1);
        assertThat(dto.getEnabled()).isEqualTo(1);
        assertThat(dto.getRequired()).isEqualTo(1);
        assertThat(dto.getDefaultValue()).isEqualTo("default");
        assertThat(dto.getTurSNSite()).isEqualTo(turSNSite);
    }

    @Test
    void testBuilder() {
        TurSNSite turSNSite = mock(TurSNSite.class);

        TurSNSiteFieldExtDto dto = TurSNSiteFieldExtDto.builder()
                .id("id-456")
                .externalId("ext-456")
                .name("title")
                .description("Title field")
                .facetName("Title Facet")
                .snType(TurSNFieldType.NER)
                .type(TurSEFieldType.TEXT)
                .multiValued(0)
                .facet(1)
                .facetRange(TurSNSiteFacetRangeEnum.YEAR)
                .facetType(TurSNSiteFacetFieldEnum.AND)
                .facetItemType(TurSNSiteFacetFieldEnum.OR)
                .secondaryFacet(false)
                .showAllFacetItems(true)
                .hl(1)
                .mlt(0)
                .enabled(1)
                .required(0)
                .defaultValue("")
                .turSNSite(turSNSite)
                .build();

        assertThat(dto.getId()).isEqualTo("id-456");
        assertThat(dto.getExternalId()).isEqualTo("ext-456");
        assertThat(dto.getName()).isEqualTo("title");
        assertThat(dto.getDescription()).isEqualTo("Title field");
        assertThat(dto.getFacetName()).isEqualTo("Title Facet");
        assertThat(dto.getSnType()).isEqualTo(TurSNFieldType.NER);
        assertThat(dto.getType()).isEqualTo(TurSEFieldType.TEXT);
        assertThat(dto.getMultiValued()).isZero();
        assertThat(dto.getFacet()).isEqualTo(1);
        assertThat(dto.getFacetRange()).isEqualTo(TurSNSiteFacetRangeEnum.YEAR);
        assertThat(dto.getFacetType()).isEqualTo(TurSNSiteFacetFieldEnum.AND);
        assertThat(dto.getFacetItemType()).isEqualTo(TurSNSiteFacetFieldEnum.OR);
        assertThat(dto.getSecondaryFacet()).isFalse();
        assertThat(dto.getShowAllFacetItems()).isTrue();
        assertThat(dto.getHl()).isEqualTo(1);
        assertThat(dto.getMlt()).isZero();
        assertThat(dto.getEnabled()).isEqualTo(1);
        assertThat(dto.getRequired()).isZero();
        assertThat(dto.getDefaultValue()).isEmpty();
        assertThat(dto.getTurSNSite()).isEqualTo(turSNSite);
    }

    @Test
    void testBuilderWithDefaults() {
        TurSNSiteFieldExtDto dto = TurSNSiteFieldExtDto.builder()
                .name("testField")
                .build();

        assertThat(dto.getName()).isEqualTo("testField");
        assertThat(dto.getFacetLocales()).isNotNull().isEmpty();
    }

    @Test
    void testToBuilder() {
        TurSNSiteFieldExtDto original = TurSNSiteFieldExtDto.builder()
                .id("original-id")
                .name("originalName")
                .description("Original Description")
                .build();

        TurSNSiteFieldExtDto modified = original.toBuilder()
                .description("Modified Description")
                .build();

        assertThat(modified.getId()).isEqualTo("original-id");
        assertThat(modified.getName()).isEqualTo("originalName");
        assertThat(modified.getDescription()).isEqualTo("Modified Description");
    }

    @Test
    void testEntityConstructor() {
        TurSNSiteFieldExt entity = mock(TurSNSiteFieldExt.class);
        TurSNSite turSNSite = mock(TurSNSite.class);

        when(entity.getId()).thenReturn("entity-id");
        when(entity.getExternalId()).thenReturn("entity-ext-id");
        when(entity.getName()).thenReturn("entityField");
        when(entity.getDescription()).thenReturn("Entity Description");
        when(entity.getFacet()).thenReturn(1);
        when(entity.getFacetRange()).thenReturn(TurSNSiteFacetRangeEnum.MONTH);
        when(entity.getFacetName()).thenReturn("Entity Facet");
        when(entity.getFacetType()).thenReturn(TurSNSiteFacetFieldEnum.AND);
        when(entity.getFacetItemType()).thenReturn(TurSNSiteFacetFieldEnum.OR);
        when(entity.getSecondaryFacet()).thenReturn(true);
        when(entity.getShowAllFacetItems()).thenReturn(false);
        when(entity.getSnType()).thenReturn(TurSNFieldType.THESAURUS);
        when(entity.getType()).thenReturn(TurSEFieldType.DATE);
        when(entity.getMultiValued()).thenReturn(0);
        when(entity.getHl()).thenReturn(1);
        when(entity.getMlt()).thenReturn(1);
        when(entity.getEnabled()).thenReturn(1);
        when(entity.getRequired()).thenReturn(0);
        when(entity.getDefaultValue()).thenReturn("default-value");
        when(entity.getTurSNSite()).thenReturn(turSNSite);

        TurSNSiteFieldExtDto dto = new TurSNSiteFieldExtDto(entity);

        assertThat(dto.getId()).isEqualTo("entity-id");
        assertThat(dto.getExternalId()).isEqualTo("entity-ext-id");
        assertThat(dto.getName()).isEqualTo("entityField");
        assertThat(dto.getDescription()).isEqualTo("Entity Description");
        assertThat(dto.getFacet()).isEqualTo(1);
        assertThat(dto.getFacetRange()).isEqualTo(TurSNSiteFacetRangeEnum.MONTH);
        assertThat(dto.getFacetName()).isEqualTo("Entity Facet");
        assertThat(dto.getFacetType()).isEqualTo(TurSNSiteFacetFieldEnum.AND);
        assertThat(dto.getFacetItemType()).isEqualTo(TurSNSiteFacetFieldEnum.OR);
        assertThat(dto.getSecondaryFacet()).isTrue();
        assertThat(dto.getShowAllFacetItems()).isFalse();
        assertThat(dto.getSnType()).isEqualTo(TurSNFieldType.THESAURUS);
        assertThat(dto.getType()).isEqualTo(TurSEFieldType.DATE);
        assertThat(dto.getMultiValued()).isZero();
        assertThat(dto.getHl()).isEqualTo(1);
        assertThat(dto.getMlt()).isEqualTo(1);
        assertThat(dto.getEnabled()).isEqualTo(1);
        assertThat(dto.getRequired()).isZero();
        assertThat(dto.getDefaultValue()).isEqualTo("default-value");
        assertThat(dto.getTurSNSite()).isEqualTo(turSNSite);
    }

    @Test
    void testGettersAndSetters() {
        TurSNSiteFieldExtDto dto = new TurSNSiteFieldExtDto();
        TurSNSite turSNSite = mock(TurSNSite.class);

        dto.setId("test-id");
        dto.setExternalId("test-ext-id");
        dto.setName("testName");
        dto.setDescription("Test Description");
        dto.setFacetName("Test Facet");
        dto.setSnType(TurSNFieldType.NER);
        dto.setType(TurSEFieldType.INT);
        dto.setMultiValued(1);
        dto.setFacet(0);
        dto.setFacetRange(TurSNSiteFacetRangeEnum.MONTH);
        dto.setFacetType(TurSNSiteFacetFieldEnum.OR);
        dto.setFacetItemType(TurSNSiteFacetFieldEnum.AND);
        dto.setSecondaryFacet(true);
        dto.setShowAllFacetItems(true);
        dto.setHl(0);
        dto.setMlt(1);
        dto.setEnabled(0);
        dto.setRequired(1);
        dto.setDefaultValue("42");
        dto.setTurSNSite(turSNSite);

        assertThat(dto.getId()).isEqualTo("test-id");
        assertThat(dto.getExternalId()).isEqualTo("test-ext-id");
        assertThat(dto.getName()).isEqualTo("testName");
        assertThat(dto.getDescription()).isEqualTo("Test Description");
        assertThat(dto.getFacetName()).isEqualTo("Test Facet");
        assertThat(dto.getSnType()).isEqualTo(TurSNFieldType.NER);
        assertThat(dto.getType()).isEqualTo(TurSEFieldType.INT);
        assertThat(dto.getMultiValued()).isEqualTo(1);
        assertThat(dto.getFacet()).isZero();
        assertThat(dto.getFacetRange()).isEqualTo(TurSNSiteFacetRangeEnum.MONTH);
        assertThat(dto.getFacetType()).isEqualTo(TurSNSiteFacetFieldEnum.OR);
        assertThat(dto.getFacetItemType()).isEqualTo(TurSNSiteFacetFieldEnum.AND);
        assertThat(dto.getSecondaryFacet()).isTrue();
        assertThat(dto.getShowAllFacetItems()).isTrue();
        assertThat(dto.getHl()).isZero();
        assertThat(dto.getMlt()).isEqualTo(1);
        assertThat(dto.getEnabled()).isZero();
        assertThat(dto.getRequired()).isEqualTo(1);
        assertThat(dto.getDefaultValue()).isEqualTo("42");
        assertThat(dto.getTurSNSite()).isEqualTo(turSNSite);
    }

    @Test
    void testSetFacetLocalesWithNull() {
        TurSNSiteFieldExtDto dto = new TurSNSiteFieldExtDto();

        Set<TurSNSiteFieldExtFacet> nullSet = null;
        dto.setFacetLocales(nullSet);

        assertThat(dto.getFacetLocales()).isNotNull().isEmpty();
    }

    @Test
    void testSetFacetLocalesWithExistingData() {
        TurSNSiteFieldExtDto dto = new TurSNSiteFieldExtDto();

        TurSNSiteFieldExtFacet facet1 = mock(TurSNSiteFieldExtFacet.class);
        when(facet1.getId()).thenReturn("facet-1");
        when(facet1.getLocale()).thenReturn(Locale.US);
        when(facet1.getLabel()).thenReturn("English Label");

        TurSNSiteFieldExtFacet facet2 = mock(TurSNSiteFieldExtFacet.class);
        when(facet2.getId()).thenReturn("facet-2");
        when(facet2.getLocale()).thenReturn(Locale.FRENCH);
        when(facet2.getLabel()).thenReturn("French Label");

        Set<TurSNSiteFieldExtFacet> facetSet = new HashSet<>();
        facetSet.add(facet1);
        facetSet.add(facet2);

        dto.setFacetLocales(facetSet);

        assertThat(dto.getFacetLocales()).hasSize(2);
    }

    @Test
    void testSetFacetLocalesConvertsFromEntity() {
        TurSNSiteFieldExtDto dto = new TurSNSiteFieldExtDto();

        TurSNSiteFieldExtFacet facet = mock(TurSNSiteFieldExtFacet.class);
        when(facet.getId()).thenReturn("facet-id");
        when(facet.getLocale()).thenReturn(Locale.GERMAN);
        when(facet.getLabel()).thenReturn("German Label");

        Set<TurSNSiteFieldExtFacet> facetSet = new HashSet<>();
        facetSet.add(facet);

        dto.setFacetLocales(facetSet);

        assertThat(dto.getFacetLocales()).hasSize(1);
        TurSNSiteFieldExtFacetDto facetDto = dto.getFacetLocales().iterator().next();
        assertThat(facetDto.getId()).isEqualTo("facet-id");
        assertThat(facetDto.getLocale()).isEqualTo(Locale.GERMAN);
        assertThat(facetDto.getLabel()).isEqualTo("German Label");
    }

    @Test
    void testAllFieldTypes() {
        for (TurSNFieldType snType : TurSNFieldType.values()) {
            TurSNSiteFieldExtDto dto = TurSNSiteFieldExtDto.builder()
                    .snType(snType)
                    .build();

            assertThat(dto.getSnType()).isEqualTo(snType);
        }
    }

    @Test
    void testAllSEFieldTypes() {
        for (TurSEFieldType seType : TurSEFieldType.values()) {
            TurSNSiteFieldExtDto dto = TurSNSiteFieldExtDto.builder()
                    .type(seType)
                    .build();

            assertThat(dto.getType()).isEqualTo(seType);
        }
    }

    @Test
    void testBooleanFields() {
        TurSNSiteFieldExtDto dto = TurSNSiteFieldExtDto.builder()
                .secondaryFacet(null)
                .showAllFacetItems(null)
                .build();

        assertThat(dto.getSecondaryFacet()).isNull();
        assertThat(dto.getShowAllFacetItems()).isNull();

        dto.setSecondaryFacet(true);
        dto.setShowAllFacetItems(false);

        assertThat(dto.getSecondaryFacet()).isTrue();
        assertThat(dto.getShowAllFacetItems()).isFalse();
    }
}
