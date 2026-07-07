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
package com.viglet.turing.sn.contentfit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.viglet.turing.persistence.model.persona.TurPersona;
import com.viglet.turing.persistence.model.sn.TurSNSite;
import com.viglet.turing.persistence.model.sn.genai.TurSNSiteGenAi;
import com.viglet.turing.persistence.model.sn.locale.TurSNSiteLocale;
import com.viglet.turing.persistence.repository.persona.TurPersonaRepository;
import com.viglet.turing.persistence.repository.sn.locale.TurSNSiteLocaleRepository;
import com.viglet.turing.plugins.se.TurSearchEnginePlugin;
import com.viglet.turing.plugins.se.TurSearchEnginePluginFactory;

/**
 * Unit tests for {@link TurSNContentFitCoverageService} — the bucket math
 * (too-complex / borderline / good) and graceful unsupported handling (T472).
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@ExtendWith(MockitoExtension.class)
class TurSNContentFitCoverageServiceTest {

    private static final String FIELD = TurSNContentFitIndexer.FIELD_NAME;

    @Mock
    private TurSNSiteLocaleRepository snSiteLocaleRepository;
    @Mock
    private TurPersonaRepository personaRepository;
    @Mock
    private TurSearchEnginePluginFactory pluginFactory;
    @Mock
    private TurSearchEnginePlugin plugin;

    private TurSNContentFitCoverageService service;
    private TurSNSite site;
    private TurSNSiteLocale locale;

    @BeforeEach
    void setUp() {
        service = new TurSNContentFitCoverageService(snSiteLocaleRepository, personaRepository,
                pluginFactory);
        site = new TurSNSite();
        site.setId("s1");
        site.setName("catalog");
        TurSNSiteGenAi genAi = new TurSNSiteGenAi();
        genAi.setContentFitIndexingEnabled(true);
        genAi.setContentFitPersonaId("p1");
        site.setTurSNSiteGenAi(genAi);
        locale = new TurSNSiteLocale();

        when(pluginFactory.getPluginForSite(site)).thenReturn(plugin);
        when(snSiteLocaleRepository.findByTurSNSite(site)).thenReturn(List.of(locale));
    }

    @Test
    void computesBucketsAndPercent() {
        TurPersona persona = new TurPersona();
        persona.setName("Elementary Reader");
        when(personaRepository.findById("p1")).thenReturn(Optional.of(persona));
        when(plugin.getDocumentTotal(locale)).thenReturn(100L);
        when(plugin.getDocumentCountWithField(locale, FIELD)).thenReturn(80L);
        when(plugin.getDocumentCountWithFieldBelow(locale, FIELD,
                TurSNContentFitCoverageService.TOO_COMPLEX_THRESHOLD)).thenReturn(10L);
        when(plugin.getDocumentCountWithFieldBelow(locale, FIELD,
                TurSNContentFitCoverageService.GOOD_THRESHOLD)).thenReturn(30L);

        TurSNContentFitReport report = service.coverage(site);

        assertThat(report.supported()).isTrue();
        assertThat(report.enabled()).isTrue();
        assertThat(report.personaName()).isEqualTo("Elementary Reader");
        assertThat(report.totalDocuments()).isEqualTo(100L);
        assertThat(report.scoredDocuments()).isEqualTo(80L);
        assertThat(report.tooComplex()).isEqualTo(10L);   // below 40
        assertThat(report.borderline()).isEqualTo(20L);   // below 70 minus below 40
        assertThat(report.good()).isEqualTo(50L);          // scored minus below 70
        assertThat(report.tooComplexPercent()).isEqualTo(12.5); // 10 / 80 * 100
    }

    @Test
    void reportsUnsupportedWhenEngineCannotRangeCount() {
        lenient().when(personaRepository.findById("p1")).thenReturn(Optional.empty());
        when(plugin.getDocumentTotal(locale)).thenReturn(100L);
        when(plugin.getDocumentCountWithField(locale, FIELD)).thenReturn(-1L);

        TurSNContentFitReport report = service.coverage(site);

        assertThat(report.supported()).isFalse();
        assertThat(report.scoredDocuments()).isZero();
        assertThat(report.tooComplexPercent()).isZero();
        assertThat(report.totalDocuments()).isEqualTo(100L);
    }

    @Test
    void zeroScoredYieldsZeroPercentNotDivideByZero() {
        lenient().when(personaRepository.findById(any())).thenReturn(Optional.empty());
        when(plugin.getDocumentTotal(locale)).thenReturn(0L);
        when(plugin.getDocumentCountWithField(locale, FIELD)).thenReturn(0L);
        when(plugin.getDocumentCountWithFieldBelow(locale, FIELD,
                TurSNContentFitCoverageService.TOO_COMPLEX_THRESHOLD)).thenReturn(0L);
        when(plugin.getDocumentCountWithFieldBelow(locale, FIELD,
                TurSNContentFitCoverageService.GOOD_THRESHOLD)).thenReturn(0L);

        TurSNContentFitReport report = service.coverage(site);

        assertThat(report.supported()).isTrue();
        assertThat(report.scoredDocuments()).isZero();
        assertThat(report.tooComplexPercent()).isZero();
    }
}
