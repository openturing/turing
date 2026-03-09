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

package com.viglet.turing.persistence.model.sn;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.viglet.turing.persistence.model.se.TurSEInstance;
import com.viglet.turing.persistence.model.sn.field.TurSNSiteFacetFieldEnum;
import com.viglet.turing.persistence.model.sn.genai.TurSNSiteGenAi;

/**
 * Unit tests for TurSNSite.
 *
 * @author Alexandre Oliveira
 * @since 2025.1.10
 */
@ExtendWith(MockitoExtension.class)
class TurSNSiteTest {

    @Mock
    private TurSEInstance turSEInstance;

    @Mock
    private TurSNSiteGenAi turSNSiteGenAi;

    private TurSNSite turSNSite;

    @BeforeEach
    void setUp() {
        turSNSite = new TurSNSite();
    }

    @Test
    void testGettersAndSetters() {
        String id = "site-id-123";
        String name = "Test Site";
        String description = "Test Description";
        Integer rowsPerPage = 20;
        Integer wildcardNoResults = 1;
        Integer wildcardAlways = 1;
        Integer exactMatch = 1;
        Integer facet = 1;
        Integer itemsPerFacet = 10;
        Integer hl = 1;
        String hlPre = "<em>";
        String hlPost = "</em>";
        Integer mlt = 1;
        Integer thesaurus = 1;
        String defaultField = "text";
        String exactMatchField = "exact_text";
        String defaultTitleField = "title";
        String defaultTextField = "text";
        String defaultDescriptionField = "description";
        String defaultDateField = "date";
        String defaultImageField = "image";
        String defaultURLField = "url";
        Integer spellCheck = 1;
        Integer spellCheckFixes = 3;
        Integer spotlightWithResults = 1;

        turSNSite.setId(id);
        turSNSite.setName(name);
        turSNSite.setDescription(description);
        turSNSite.setRowsPerPage(rowsPerPage);
        turSNSite.setWildcardNoResults(wildcardNoResults);
        turSNSite.setWildcardAlways(wildcardAlways);
        turSNSite.setExactMatch(exactMatch);
        turSNSite.setFacet(facet);
        turSNSite.setItemsPerFacet(itemsPerFacet);
        turSNSite.setHl(hl);
        turSNSite.setHlPre(hlPre);
        turSNSite.setHlPost(hlPost);
        turSNSite.setMlt(mlt);
        turSNSite.setFacetType(TurSNSiteFacetFieldEnum.OR);
        turSNSite.setFacetItemType(TurSNSiteFacetFieldEnum.OR);
        turSNSite.setFacetSort(TurSNSiteFacetSortEnum.ALPHABETICAL);
        turSNSite.setThesaurus(thesaurus);
        turSNSite.setDefaultField(defaultField);
        turSNSite.setExactMatchField(exactMatchField);
        turSNSite.setDefaultTitleField(defaultTitleField);
        turSNSite.setDefaultTextField(defaultTextField);
        turSNSite.setDefaultDescriptionField(defaultDescriptionField);
        turSNSite.setDefaultDateField(defaultDateField);
        turSNSite.setDefaultImageField(defaultImageField);
        turSNSite.setDefaultURLField(defaultURLField);
        turSNSite.setSpellCheck(spellCheck);
        turSNSite.setSpellCheckFixes(spellCheckFixes);
        turSNSite.setSpotlightWithResults(spotlightWithResults);
        turSNSite.setTurSEInstance(turSEInstance);
        turSNSite.setTurSNSiteGenAi(turSNSiteGenAi);

        // Grouped assertions for basic fields
        assertThat(turSNSite)
                .extracting(
                        TurSNSite::getId,
                        TurSNSite::getName,
                        TurSNSite::getDescription,
                        TurSNSite::getRowsPerPage,
                        TurSNSite::getWildcardNoResults,
                        TurSNSite::getWildcardAlways,
                        TurSNSite::getExactMatch,
                        TurSNSite::getFacet,
                        TurSNSite::getItemsPerFacet,
                        TurSNSite::getHl,
                        TurSNSite::getHlPre,
                        TurSNSite::getHlPost,
                        TurSNSite::getMlt,
                        TurSNSite::getFacetType,
                        TurSNSite::getFacetItemType,
                        TurSNSite::getFacetSort,
                        TurSNSite::getThesaurus,
                        TurSNSite::getDefaultField,
                        TurSNSite::getExactMatchField,
                        TurSNSite::getDefaultTitleField,
                        TurSNSite::getDefaultTextField,
                        TurSNSite::getDefaultDescriptionField,
                        TurSNSite::getDefaultDateField,
                        TurSNSite::getDefaultImageField,
                        TurSNSite::getDefaultURLField)
                .containsExactly(
                        id,
                        name,
                        description,
                        rowsPerPage,
                        wildcardNoResults,
                        wildcardAlways,
                        exactMatch,
                        facet,
                        itemsPerFacet,
                        hl,
                        hlPre,
                        hlPost,
                        mlt,
                        TurSNSiteFacetFieldEnum.OR,
                        TurSNSiteFacetFieldEnum.OR,
                        TurSNSiteFacetSortEnum.ALPHABETICAL,
                        thesaurus,
                        defaultField,
                        exactMatchField,
                        defaultTitleField,
                        defaultTextField,
                        defaultDescriptionField,
                        defaultDateField,
                        defaultImageField,
                        defaultURLField);

        // Additional assertions for remaining fields
        assertThat(turSNSite.getSpellCheck()).isEqualTo(spellCheck);
        assertThat(turSNSite.getSpellCheckFixes()).isEqualTo(spellCheckFixes);
        assertThat(turSNSite.getSpotlightWithResults()).isEqualTo(spotlightWithResults);
        assertThat(turSNSite.getTurSEInstance()).isEqualTo(turSEInstance);
        assertThat(turSNSite.getTurSNSiteGenAi()).isEqualTo(turSNSiteGenAi);
    }

    @Test
    void testDefaultValues() {
        assertThat(turSNSite.getId()).isNull();
        assertThat(turSNSite.getName()).isNull();
        assertThat(turSNSite.getDescription()).isNull();
        assertThat(turSNSite.getRowsPerPage()).isEqualTo(10);
        assertThat(turSNSite.getWildcardNoResults()).isZero();
        assertThat(turSNSite.getWildcardAlways()).isZero();
        assertThat(turSNSite.getExactMatch()).isZero();
        assertThat(turSNSite.getFacet()).isNull();
        assertThat(turSNSite.getItemsPerFacet()).isNull();
        assertThat(turSNSite.getHl()).isNull();
        assertThat(turSNSite.getHlPre()).isNull();
        assertThat(turSNSite.getHlPost()).isNull();
        assertThat(turSNSite.getMlt()).isNull();
        assertThat(turSNSite.getFacetType()).isEqualTo(TurSNSiteFacetFieldEnum.AND);
        assertThat(turSNSite.getFacetItemType()).isEqualTo(TurSNSiteFacetFieldEnum.AND);
        assertThat(turSNSite.getFacetSort()).isEqualTo(TurSNSiteFacetSortEnum.COUNT);
        assertThat(turSNSite.getThesaurus()).isZero();
    }

    @Test
    void testCollectionsInitialized() {
        assertThat(turSNSite.getTurSNSiteFields()).isEmpty();
        assertThat(turSNSite.getTurSNSiteFieldExts()).isEmpty();
        assertThat(turSNSite.getTurSNSiteSpotlights()).isEmpty();
        assertThat(turSNSite.getTurSNSiteLocales()).isEmpty();
        assertThat(turSNSite.getTurSNSiteMetricAccesses()).isEmpty();
        assertThat(turSNSite.getTurSNRankingExpressions()).isEmpty();
    }

    @Test
    void testSetRowsPerPageWithDifferentValues() {
        turSNSite.setRowsPerPage(5);
        assertThat(turSNSite.getRowsPerPage()).isEqualTo(5);

        turSNSite.setRowsPerPage(25);
        assertThat(turSNSite.getRowsPerPage()).isEqualTo(25);

        turSNSite.setRowsPerPage(100);
        assertThat(turSNSite.getRowsPerPage()).isEqualTo(100);
    }

    @Test
    void testSetFacetTypeWithDifferentValues() {
        turSNSite.setFacetType(TurSNSiteFacetFieldEnum.AND);
        assertThat(turSNSite.getFacetType()).isEqualTo(TurSNSiteFacetFieldEnum.AND);

        turSNSite.setFacetType(TurSNSiteFacetFieldEnum.OR);
        assertThat(turSNSite.getFacetType()).isEqualTo(TurSNSiteFacetFieldEnum.OR);
    }

    @Test
    void testSetFacetSortWithDifferentValues() {
        turSNSite.setFacetSort(TurSNSiteFacetSortEnum.COUNT);
        assertThat(turSNSite.getFacetSort()).isEqualTo(TurSNSiteFacetSortEnum.COUNT);

        turSNSite.setFacetSort(TurSNSiteFacetSortEnum.ALPHABETICAL);
        assertThat(turSNSite.getFacetSort()).isEqualTo(TurSNSiteFacetSortEnum.ALPHABETICAL);
    }

    @Test
    void testSetHighlightParameters() {
        turSNSite.setHl(1);
        turSNSite.setHlPre("<strong>");
        turSNSite.setHlPost("</strong>");

        assertThat(turSNSite.getHl()).isEqualTo(1);
        assertThat(turSNSite.getHlPre()).isEqualTo("<strong>");
        assertThat(turSNSite.getHlPost()).isEqualTo("</strong>");
    }

    @Test
    void testSetDefaultFields() {
        turSNSite.setDefaultField("content");
        turSNSite.setDefaultTitleField("headline");
        turSNSite.setDefaultTextField("body");
        turSNSite.setDefaultDescriptionField("summary");
        turSNSite.setDefaultDateField("published");
        turSNSite.setDefaultImageField("thumbnail");
        turSNSite.setDefaultURLField("link");

        assertThat(turSNSite.getDefaultField()).isEqualTo("content");
        assertThat(turSNSite.getDefaultTitleField()).isEqualTo("headline");
        assertThat(turSNSite.getDefaultTextField()).isEqualTo("body");
        assertThat(turSNSite.getDefaultDescriptionField()).isEqualTo("summary");
        assertThat(turSNSite.getDefaultDateField()).isEqualTo("published");
        assertThat(turSNSite.getDefaultImageField()).isEqualTo("thumbnail");
        assertThat(turSNSite.getDefaultURLField()).isEqualTo("link");
    }

    @Test
    void testSetNullValues() {
        turSNSite.setId("id");
        turSNSite.setName("name");
        turSNSite.setDescription("description");
        turSNSite.setRowsPerPage(20);
        turSNSite.setTurSEInstance(turSEInstance);
        turSNSite.setTurSNSiteGenAi(turSNSiteGenAi);

        turSNSite.setId(null);
        turSNSite.setName(null);
        turSNSite.setDescription(null);
        turSNSite.setRowsPerPage(null);
        turSNSite.setTurSEInstance(null);
        turSNSite.setTurSNSiteGenAi(null);

        assertThat(turSNSite.getId()).isNull();
        assertThat(turSNSite.getName()).isNull();
        assertThat(turSNSite.getDescription()).isNull();
        assertThat(turSNSite.getRowsPerPage()).isNull();
        assertThat(turSNSite.getTurSEInstance()).isNull();
        assertThat(turSNSite.getTurSNSiteGenAi()).isNull();
    }

    @Test
    void testSerialVersionUID() {
        assertThat(TurSNSite.class)
                .hasDeclaredFields("serialVersionUID");
    }
}
