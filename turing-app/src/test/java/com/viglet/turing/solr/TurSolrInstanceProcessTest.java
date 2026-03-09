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

package com.viglet.turing.solr;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Locale;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.viglet.turing.persistence.model.sn.TurSNSite;
import com.viglet.turing.persistence.repository.sn.TurSNSiteRepository;
import com.viglet.turing.persistence.repository.sn.locale.TurSNSiteLocaleRepository;
import com.viglet.turing.properties.TurConfigProperties;
import com.viglet.turing.properties.TurSolrProperty;

/**
 * Unit tests for TurSolrInstanceProcess.
 *
 * @author Alexandre Oliveira
 * @since 2025.1.10
 */
@ExtendWith(MockitoExtension.class)
class TurSolrInstanceProcessTest {

    @Mock
    private TurSNSiteLocaleRepository turSNSiteLocaleRepository;

    @Mock
    private TurSNSiteRepository turSNSiteRepository;

    @Mock
    private TurSolrCache turSolrCache;

    @Mock
    private TurConfigProperties turConfigProperties;

    @Mock
    private TurSolrProperty solrProperties;

    private TurSolrInstanceProcess turSolrInstanceProcess;

    @BeforeEach
    void setUp() {
        lenient().when(turConfigProperties.getSolr()).thenReturn(solrProperties);
        lenient().when(solrProperties.getTimeout()).thenReturn(5000);

        turSolrInstanceProcess = new TurSolrInstanceProcess(
                turSNSiteLocaleRepository,
                turSNSiteRepository);
    }

    @Test
    void testConstructor() {
        assertThat(turSolrInstanceProcess).isNotNull();
    }

    @Test
    void testInitSolrInstanceBySiteNameNotFound() {
        when(turSNSiteRepository.findByName("nonexistent")).thenReturn(Optional.empty());

        Optional<TurSolrInstance> result = turSolrInstanceProcess.initSolrInstance("nonexistent", Locale.ENGLISH);

        assertThat(result).isEmpty();
        verify(turSNSiteRepository).findByName("nonexistent");
    }

    @Test
    void testInitSolrInstanceBySiteNameLocaleNotFound() {
        TurSNSite turSNSite = mock(TurSNSite.class);
        when(turSNSite.getName()).thenReturn("testSite");
        when(turSNSiteRepository.findByName("testSite")).thenReturn(Optional.of(turSNSite));
        when(turSNSiteLocaleRepository.findByTurSNSiteAndLanguage(turSNSite, Locale.ENGLISH)).thenReturn(null);

        Optional<TurSolrInstance> result = turSolrInstanceProcess.initSolrInstance("testSite", Locale.ENGLISH);

        assertThat(result).isEmpty();
        verify(turSNSiteRepository).findByName("testSite");
        verify(turSNSiteLocaleRepository).findByTurSNSiteAndLanguage(turSNSite, Locale.ENGLISH);
    }

    @Test
    void testMultipleCallsWithDifferentLocales() {
        TurSNSite turSNSite = mock(TurSNSite.class);
        when(turSNSiteRepository.findByName("multiLocaleSite")).thenReturn(Optional.of(turSNSite));
        when(turSNSiteLocaleRepository.findByTurSNSiteAndLanguage(any(TurSNSite.class), any(Locale.class)))
                .thenReturn(null);

        turSolrInstanceProcess.initSolrInstance("multiLocaleSite", Locale.ENGLISH);
        turSolrInstanceProcess.initSolrInstance("multiLocaleSite", Locale.FRENCH);
        turSolrInstanceProcess.initSolrInstance("multiLocaleSite", Locale.GERMAN);

        verify(turSNSiteRepository, times(3)).findByName("multiLocaleSite");
        verify(turSNSiteLocaleRepository).findByTurSNSiteAndLanguage(turSNSite, Locale.ENGLISH);
        verify(turSNSiteLocaleRepository).findByTurSNSiteAndLanguage(turSNSite, Locale.FRENCH);
        verify(turSNSiteLocaleRepository).findByTurSNSiteAndLanguage(turSNSite, Locale.GERMAN);
    }
}
