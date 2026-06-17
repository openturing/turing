/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed to the Apache Software Foundation (ASF) under one or more contributor license
 * agreements. See the NOTICE file distributed with this work for additional information regarding
 * copyright ownership. The ASF licenses this file to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package com.viglet.turing.sn.coverage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Locale;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import com.viglet.turing.commons.se.field.TurSEFieldType;
import com.viglet.turing.persistence.model.sn.TurSNSite;
import com.viglet.turing.persistence.model.sn.field.TurSNSiteFieldExt;
import com.viglet.turing.persistence.model.sn.locale.TurSNSiteLocale;
import com.viglet.turing.persistence.repository.sn.field.TurSNSiteFieldExtRepository;
import com.viglet.turing.persistence.repository.sn.locale.TurSNSiteLocaleRepository;
import com.viglet.turing.plugins.se.TurSearchEnginePlugin;
import com.viglet.turing.plugins.se.TurSearchEnginePluginFactory;

/**
 * Unit tests for {@link TurSNFieldCoverageService} (T388).
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TurSNFieldCoverageServiceTest {

    @Mock
    private TurSNSiteLocaleRepository snSiteLocaleRepository;
    @Mock
    private TurSNSiteFieldExtRepository snSiteFieldExtRepository;
    @Mock
    private TurSearchEnginePluginFactory pluginFactory;
    @Mock
    private TurSearchEnginePlugin plugin;

    private TurSNFieldCoverageService service;

    @BeforeEach
    void setUp() {
        service = new TurSNFieldCoverageService(
                snSiteLocaleRepository, snSiteFieldExtRepository, pluginFactory);
    }

    private TurSNSite site() {
        TurSNSite site = new TurSNSite();
        site.setId("site-1");
        site.setName("Catalog");
        return site;
    }

    private TurSNSiteLocale locale(TurSNSite site, String lang) {
        TurSNSiteLocale locale = new TurSNSiteLocale();
        locale.setLanguage(Locale.forLanguageTag(lang));
        locale.setTurSNSite(site);
        return locale;
    }

    private TurSNSiteFieldExt field(String name, TurSEFieldType type, int facet, int multiValued) {
        TurSNSiteFieldExt field = new TurSNSiteFieldExt();
        field.setName(name);
        field.setType(type);
        field.setEnabled(1);
        field.setFacet(facet);
        field.setMultiValued(multiValued);
        return field;
    }

    @Test
    void computesCoveragePercentAndOrdersWorstFirst() {
        TurSNSite site = site();
        TurSNSiteLocale en = locale(site, "en");

        TurSNSiteFieldExt price = field("price", TurSEFieldType.STRING, 1, 0);
        TurSNSiteFieldExt title = field("title", TurSEFieldType.TEXT, 0, 0);

        when(snSiteLocaleRepository.findByTurSNSite(site)).thenReturn(List.of(en));
        when(pluginFactory.getPluginForSite(site)).thenReturn(plugin);
        when(plugin.getDocumentTotal(en)).thenReturn(4000L);
        when(snSiteFieldExtRepository.findByTurSNSiteAndEnabled(site, 1))
                .thenReturn(List.of(price, title));
        when(plugin.getDocumentCountWithField(en, "price")).thenReturn(1000L);   // 25%
        when(plugin.getDocumentCountWithField(en, "title")).thenReturn(4000L);   // 100%

        TurSNFieldCoverageReport report = service.coverage(site);

        assertThat(report.totalDocuments()).isEqualTo(4000L);
        assertThat(report.supported()).isTrue();
        assertThat(report.fields()).hasSize(2);
        // worst coverage first
        assertThat(report.fields().get(0).fieldName()).isEqualTo("price");
        assertThat(report.fields().get(0).coveragePercent()).isEqualTo(25.0);
        assertThat(report.fields().get(0).presentDocuments()).isEqualTo(1000L);
        assertThat(report.fields().get(0).facet()).isTrue();
        assertThat(report.fields().get(1).fieldName()).isEqualTo("title");
        assertThat(report.fields().get(1).coveragePercent()).isEqualTo(100.0);
    }

    @Test
    void sumsPresenceAndTotalsAcrossLocales() {
        TurSNSite site = site();
        TurSNSiteLocale en = locale(site, "en");
        TurSNSiteLocale pt = locale(site, "pt-BR");

        TurSNSiteFieldExt field = field("mensalidade", TurSEFieldType.STRING, 0, 0);

        when(snSiteLocaleRepository.findByTurSNSite(site)).thenReturn(List.of(en, pt));
        when(pluginFactory.getPluginForSite(site)).thenReturn(plugin);
        when(plugin.getDocumentTotal(en)).thenReturn(600L);
        when(plugin.getDocumentTotal(pt)).thenReturn(400L);
        when(snSiteFieldExtRepository.findByTurSNSiteAndEnabled(site, 1)).thenReturn(List.of(field));
        when(plugin.getDocumentCountWithField(en, "mensalidade")).thenReturn(300L);
        when(plugin.getDocumentCountWithField(pt, "mensalidade")).thenReturn(430L);

        TurSNFieldCoverageReport report = service.coverage(site);

        assertThat(report.totalDocuments()).isEqualTo(1000L);
        assertThat(report.fields().get(0).presentDocuments()).isEqualTo(730L);
        assertThat(report.fields().get(0).coveragePercent()).isEqualTo(73.0);
    }

    @Test
    void marksFieldUnsupportedWhenEngineCannotCount() {
        TurSNSite site = site();
        TurSNSiteLocale en = locale(site, "en");
        TurSNSiteFieldExt field = field("body", TurSEFieldType.TEXT, 0, 0);

        when(snSiteLocaleRepository.findByTurSNSite(site)).thenReturn(List.of(en));
        when(pluginFactory.getPluginForSite(site)).thenReturn(plugin);
        when(plugin.getDocumentTotal(en)).thenReturn(100L);
        when(snSiteFieldExtRepository.findByTurSNSiteAndEnabled(site, 1)).thenReturn(List.of(field));
        when(plugin.getDocumentCountWithField(en, "body")).thenReturn(-1L);

        TurSNFieldCoverageReport report = service.coverage(site);

        assertThat(report.supported()).isFalse();
        assertThat(report.fields().get(0).supported()).isFalse();
        assertThat(report.fields().get(0).presentDocuments()).isEqualTo(-1L);
        assertThat(report.fields().get(0).coveragePercent()).isZero();
    }

    @Test
    void emptyIndexYieldsZeroPercentWithoutDivideByZero() {
        TurSNSite site = site();
        TurSNSiteLocale en = locale(site, "en");
        TurSNSiteFieldExt field = field("title", TurSEFieldType.TEXT, 0, 0);

        when(snSiteLocaleRepository.findByTurSNSite(site)).thenReturn(List.of(en));
        when(pluginFactory.getPluginForSite(site)).thenReturn(plugin);
        when(plugin.getDocumentTotal(en)).thenReturn(0L);
        when(snSiteFieldExtRepository.findByTurSNSiteAndEnabled(site, 1)).thenReturn(List.of(field));
        when(plugin.getDocumentCountWithField(en, "title")).thenReturn(0L);

        TurSNFieldCoverageReport report = service.coverage(site);

        assertThat(report.totalDocuments()).isZero();
        assertThat(report.fields().get(0).coveragePercent()).isZero();
        assertThat(report.fields().get(0).supported()).isTrue();
    }

    @Test
    void documentTotalExceptionIsSwallowed() {
        TurSNSite site = site();
        TurSNSiteLocale en = locale(site, "en");

        when(snSiteLocaleRepository.findByTurSNSite(site)).thenReturn(List.of(en));
        when(pluginFactory.getPluginForSite(site)).thenReturn(plugin);
        when(plugin.getDocumentTotal(en)).thenThrow(new RuntimeException("index down"));
        when(snSiteFieldExtRepository.findByTurSNSiteAndEnabled(site, 1)).thenReturn(List.of());

        TurSNFieldCoverageReport report = service.coverage(site);

        assertThat(report.totalDocuments()).isZero();
        assertThat(report.fields()).isEmpty();
    }
}
