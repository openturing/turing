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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import com.viglet.turing.api.sn.bean.TurSNFieldExtCheck;
import com.viglet.turing.commons.se.field.TurSEFieldType;
import com.viglet.turing.persistence.model.se.TurSEInstance;
import com.viglet.turing.persistence.model.sn.TurSNSite;
import com.viglet.turing.persistence.model.sn.field.TurSNSiteFieldExt;
import com.viglet.turing.persistence.model.sn.locale.TurSNSiteLocale;
import com.viglet.turing.persistence.repository.sn.TurSNSiteRepository;
import com.viglet.turing.persistence.repository.sn.field.TurSNSiteFieldExtRepository;
import com.viglet.turing.persistence.repository.sn.locale.TurSNSiteLocaleRepository;
import com.viglet.turing.solr.TurSolrUtils;
import com.viglet.turing.solr.bean.TurSolrFieldBean;

/**
 * Unit tests for TurSNSiteFieldCheckAPI.
 *
 * @author Alexandre Oliveira
 * @since 2026.1.10
 */
class TurSNSiteFieldCheckAPITest {

    @Test
    void testFieldCheckReturnsEmptyWhenSiteMissing() {
        TurSNSiteRepository siteRepository = mock(TurSNSiteRepository.class);
        TurSNSiteFieldExtRepository fieldExtRepository = mock(TurSNSiteFieldExtRepository.class);
        TurSNSiteLocaleRepository localeRepository = mock(TurSNSiteLocaleRepository.class);
        TurSNSiteFieldCheckAPI api = new TurSNSiteFieldCheckAPI(siteRepository, fieldExtRepository, localeRepository);

        when(siteRepository.findById("site")).thenReturn(Optional.empty());

        TurSNFieldExtCheck result = api.turSNSiteFieldExtCheckList("site");

        assertThat(result).isNotNull();
    }

    @Test
    void testFieldCheckReturnsEmptyListsWhenNoFieldsOrLocales() {
        TurSNSiteRepository siteRepository = mock(TurSNSiteRepository.class);
        TurSNSiteFieldExtRepository fieldExtRepository = mock(TurSNSiteFieldExtRepository.class);
        TurSNSiteLocaleRepository localeRepository = mock(TurSNSiteLocaleRepository.class);
        TurSNSiteFieldCheckAPI api = new TurSNSiteFieldCheckAPI(siteRepository, fieldExtRepository, localeRepository);
        TurSNSite site = new TurSNSite();

        when(siteRepository.findById("site")).thenReturn(Optional.of(site));
        when(fieldExtRepository.findByTurSNSite(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.eq(site)))
                .thenReturn(Collections.emptyList());
        when(localeRepository.findByTurSNSite(site)).thenReturn(Collections.emptyList());

        TurSNFieldExtCheck result = api.turSNSiteFieldExtCheckList("site");

        assertThat(result.getCores()).isEmpty();
        assertThat(result.getFields()).isEmpty();
    }

    @Test
    void testFieldCheckReturnsCoreAndCorrectFieldStatusWhenSchemaMatches() {
        TurSNSiteRepository siteRepository = mock(TurSNSiteRepository.class);
        TurSNSiteFieldExtRepository fieldExtRepository = mock(TurSNSiteFieldExtRepository.class);
        TurSNSiteLocaleRepository localeRepository = mock(TurSNSiteLocaleRepository.class);
        TurSNSiteFieldCheckAPI api = new TurSNSiteFieldCheckAPI(siteRepository, fieldExtRepository, localeRepository);

        TurSEInstance instance = new TurSEInstance();
        TurSNSite site = new TurSNSite();
        site.setTurSEInstance(instance);

        TurSNSiteLocale locale = new TurSNSiteLocale();
        locale.setCore("core_en");

        TurSNSiteFieldExt fieldExt = new TurSNSiteFieldExt();
        fieldExt.setId("id1");
        fieldExt.setExternalId("ext1");
        fieldExt.setName("title");
        fieldExt.setType(TurSEFieldType.STRING);
        fieldExt.setMultiValued(1);
        fieldExt.setFacet(0);

        when(siteRepository.findById("site")).thenReturn(Optional.of(site));
        when(fieldExtRepository.findByTurSNSite(any(), org.mockito.ArgumentMatchers.eq(site)))
                .thenReturn(List.of(fieldExt));
        when(localeRepository.findByTurSNSite(site)).thenReturn(List.of(locale));

        TurSolrFieldBean schemaField = TurSolrFieldBean.builder().name("title").type("string").multiValued(true)
                .build();
        try (MockedStatic<TurSolrUtils> utils = Mockito.mockStatic(TurSolrUtils.class)) {
            utils.when(() -> TurSolrUtils.coreExists(instance, "core_en")).thenReturn(true);
            utils.when(() -> TurSolrUtils.existsField(instance, "core_en", "title")).thenReturn(true);
            utils.when(() -> TurSolrUtils.getField(instance, "core_en", "title")).thenReturn(schemaField);
            utils.when(() -> TurSolrUtils.getSolrFieldType(TurSEFieldType.STRING)).thenReturn("string");

            TurSNFieldExtCheck result = api.turSNSiteFieldExtCheckList("site");

            assertThat(result.getCores()).hasSize(1);
            assertThat(result.getCores().get(0).isExists()).isTrue();
            assertThat(result.getFields()).hasSize(1);
            assertThat(result.getFields().get(0).isCorrect()).isTrue();
            assertThat(result.getFields().get(0).isFacetIsCorrect()).isTrue();
            assertThat(result.getFields().get(0).getCores()).hasSize(1);
            assertThat(result.getFields().get(0).getCores().get(0).isCorrect()).isTrue();
        }
    }

    @Test
    void testFieldCheckMarksIncorrectWhenFieldMissingAndFacetInvalid() {
        TurSNSiteRepository siteRepository = mock(TurSNSiteRepository.class);
        TurSNSiteFieldExtRepository fieldExtRepository = mock(TurSNSiteFieldExtRepository.class);
        TurSNSiteLocaleRepository localeRepository = mock(TurSNSiteLocaleRepository.class);
        TurSNSiteFieldCheckAPI api = new TurSNSiteFieldCheckAPI(siteRepository, fieldExtRepository, localeRepository);

        TurSEInstance instance = new TurSEInstance();
        TurSNSite site = new TurSNSite();
        site.setTurSEInstance(instance);

        TurSNSiteLocale locale = new TurSNSiteLocale();
        locale.setCore("core_pt");

        TurSNSiteFieldExt fieldExt = new TurSNSiteFieldExt();
        fieldExt.setId("id2");
        fieldExt.setExternalId("ext2");
        fieldExt.setName("content");
        fieldExt.setType(TurSEFieldType.TEXT);
        fieldExt.setMultiValued(0);
        fieldExt.setFacet(1);

        when(siteRepository.findById("site")).thenReturn(Optional.of(site));
        when(fieldExtRepository.findByTurSNSite(any(), org.mockito.ArgumentMatchers.eq(site)))
                .thenReturn(List.of(fieldExt));
        when(localeRepository.findByTurSNSite(site)).thenReturn(List.of(locale));

        try (MockedStatic<TurSolrUtils> utils = Mockito.mockStatic(TurSolrUtils.class)) {
            utils.when(() -> TurSolrUtils.coreExists(instance, "core_pt")).thenReturn(false);
            utils.when(() -> TurSolrUtils.existsField(instance, "core_pt", "content")).thenReturn(false);

            TurSNFieldExtCheck result = api.turSNSiteFieldExtCheckList("site");

            assertThat(result.getCores()).hasSize(1);
            assertThat(result.getCores().get(0).isExists()).isFalse();
            assertThat(result.getFields()).hasSize(1);
            assertThat(result.getFields().get(0).isCorrect()).isFalse();
            assertThat(result.getFields().get(0).isFacetIsCorrect()).isFalse();
            assertThat(result.getFields().get(0).getCores()).hasSize(1);
            assertThat(result.getFields().get(0).getCores().get(0).isExists()).isFalse();
        }
    }

    @Test
    void testFieldCheckMarksIncorrectWhenTypeOrMultivaluedMismatch() {
        TurSNSiteRepository siteRepository = mock(TurSNSiteRepository.class);
        TurSNSiteFieldExtRepository fieldExtRepository = mock(TurSNSiteFieldExtRepository.class);
        TurSNSiteLocaleRepository localeRepository = mock(TurSNSiteLocaleRepository.class);
        TurSNSiteFieldCheckAPI api = new TurSNSiteFieldCheckAPI(siteRepository, fieldExtRepository, localeRepository);

        TurSEInstance instance = new TurSEInstance();
        TurSNSite site = new TurSNSite();
        site.setTurSEInstance(instance);

        TurSNSiteLocale locale = new TurSNSiteLocale();
        locale.setCore("core_es");

        TurSNSiteFieldExt fieldExt = new TurSNSiteFieldExt();
        fieldExt.setId("id3");
        fieldExt.setExternalId("ext3");
        fieldExt.setName("summary");
        fieldExt.setType(TurSEFieldType.STRING);
        fieldExt.setMultiValued(1);
        fieldExt.setFacet(0);

        when(siteRepository.findById("site")).thenReturn(Optional.of(site));
        when(fieldExtRepository.findByTurSNSite(any(), org.mockito.ArgumentMatchers.eq(site)))
                .thenReturn(List.of(fieldExt));
        when(localeRepository.findByTurSNSite(site)).thenReturn(List.of(locale));

        TurSolrFieldBean schemaField = TurSolrFieldBean.builder().name("summary").type("text_general")
                .multiValued(false)
                .build();
        try (MockedStatic<TurSolrUtils> utils = Mockito.mockStatic(TurSolrUtils.class)) {
            utils.when(() -> TurSolrUtils.coreExists(instance, "core_es")).thenReturn(true);
            utils.when(() -> TurSolrUtils.existsField(instance, "core_es", "summary")).thenReturn(true);
            utils.when(() -> TurSolrUtils.getField(instance, "core_es", "summary")).thenReturn(schemaField);
            utils.when(() -> TurSolrUtils.getSolrFieldType(TurSEFieldType.STRING)).thenReturn("string");

            TurSNFieldExtCheck result = api.turSNSiteFieldExtCheckList("site");

            assertThat(result.getFields()).hasSize(1);
            assertThat(result.getFields().get(0).isCorrect()).isFalse();
            assertThat(result.getFields().get(0).getCores()).hasSize(1);
            assertThat(result.getFields().get(0).getCores().get(0).isTypeIsCorrect()).isFalse();
            assertThat(result.getFields().get(0).getCores().get(0).isMultiValuedIsCorrect()).isFalse();
        }
    }

    @Test
    void testFieldCheckDoesNotThrowWhenSolrFieldTypeIsNull() {
        TurSNSiteRepository siteRepository = mock(TurSNSiteRepository.class);
        TurSNSiteFieldExtRepository fieldExtRepository = mock(TurSNSiteFieldExtRepository.class);
        TurSNSiteLocaleRepository localeRepository = mock(TurSNSiteLocaleRepository.class);
        TurSNSiteFieldCheckAPI api = new TurSNSiteFieldCheckAPI(siteRepository, fieldExtRepository, localeRepository);

        TurSEInstance instance = new TurSEInstance();
        TurSNSite site = new TurSNSite();
        site.setTurSEInstance(instance);

        TurSNSiteLocale locale = new TurSNSiteLocale();
        locale.setCore("core_ca");

        TurSNSiteFieldExt fieldExt = new TurSNSiteFieldExt();
        fieldExt.setId("id4");
        fieldExt.setExternalId("ext4");
        fieldExt.setName("author");
        fieldExt.setType(TurSEFieldType.STRING);
        fieldExt.setMultiValued(0);
        fieldExt.setFacet(0);

        when(siteRepository.findById("site")).thenReturn(Optional.of(site));
        when(fieldExtRepository.findByTurSNSite(any(), org.mockito.ArgumentMatchers.eq(site)))
                .thenReturn(List.of(fieldExt));
        when(localeRepository.findByTurSNSite(site)).thenReturn(List.of(locale));

        TurSolrFieldBean schemaField = TurSolrFieldBean.builder().name("author").multiValued(false).build();
        try (MockedStatic<TurSolrUtils> utils = Mockito.mockStatic(TurSolrUtils.class)) {
            utils.when(() -> TurSolrUtils.coreExists(instance, "core_ca")).thenReturn(true);
            utils.when(() -> TurSolrUtils.existsField(instance, "core_ca", "author")).thenReturn(true);
            utils.when(() -> TurSolrUtils.getField(instance, "core_ca", "author")).thenReturn(schemaField);
            utils.when(() -> TurSolrUtils.getSolrFieldType(TurSEFieldType.STRING)).thenReturn("string");

            TurSNFieldExtCheck result = api.turSNSiteFieldExtCheckList("site");

            assertThat(result.getFields()).hasSize(1);
            assertThat(result.getFields().get(0).isCorrect()).isFalse();
            assertThat(result.getFields().get(0).getCores()).hasSize(1);
            assertThat(result.getFields().get(0).getCores().get(0).isTypeIsCorrect()).isFalse();
        }
    }
}
