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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import com.viglet.turing.persistence.dto.sn.spotlight.TurSNSiteSpotlightDto;
import com.viglet.turing.persistence.mapper.sn.spotlight.TurSNSiteSpotlightMapper;
import com.viglet.turing.persistence.model.sn.TurSNSite;
import com.viglet.turing.persistence.model.sn.spotlight.TurSNSiteSpotlight;
import com.viglet.turing.persistence.model.sn.spotlight.TurSNSiteSpotlightDocument;
import com.viglet.turing.persistence.model.sn.spotlight.TurSNSiteSpotlightTerm;
import com.viglet.turing.persistence.repository.sn.TurSNSiteRepository;
import com.viglet.turing.persistence.repository.sn.spotlight.TurSNSiteSpotlightDocumentRepository;
import com.viglet.turing.persistence.repository.sn.spotlight.TurSNSiteSpotlightRepository;
import com.viglet.turing.persistence.repository.sn.spotlight.TurSNSiteSpotlightTermRepository;

/**
 * Unit tests for TurSNSiteSpotlightAPI.
 *
 * @author Alexandre Oliveira
 * @since 2026.1.10
 */
class TurSNSiteSpotlightAPIUnitTest {

    @Test
    void testSpotlightListReturnsEmptyWhenMissing() {
        TurSNSiteRepository siteRepository = mock(TurSNSiteRepository.class);
        TurSNSiteSpotlightRepository spotlightRepository = mock(TurSNSiteSpotlightRepository.class);
        TurSNSiteSpotlightMapper spotlightMapper = Mappers.getMapper(TurSNSiteSpotlightMapper.class);
        TurSNSiteSpotlightAPI api = new TurSNSiteSpotlightAPI(siteRepository, spotlightRepository,
                mock(TurSNSiteSpotlightDocumentRepository.class), mock(TurSNSiteSpotlightTermRepository.class),
                spotlightMapper);

        when(siteRepository.findById("site")).thenReturn(Optional.empty());

        List<TurSNSiteSpotlightDto> result = api.turSNSiteSpotlightList("site");

        assertThat(result).isEmpty();
    }

    @Test
    void testSpotlightGetPopulatesRelations() {
        TurSNSiteSpotlightRepository spotlightRepository = mock(TurSNSiteSpotlightRepository.class);
        TurSNSiteSpotlightDocumentRepository documentRepository = mock(TurSNSiteSpotlightDocumentRepository.class);
        TurSNSiteSpotlightTermRepository termRepository = mock(TurSNSiteSpotlightTermRepository.class);
        TurSNSiteSpotlightMapper spotlightMapper = Mappers.getMapper(TurSNSiteSpotlightMapper.class);
        TurSNSiteSpotlightAPI api = new TurSNSiteSpotlightAPI(mock(TurSNSiteRepository.class), spotlightRepository,
                documentRepository, termRepository, spotlightMapper);
        TurSNSiteSpotlight spotlight = new TurSNSiteSpotlight();
        TurSNSiteSpotlightDocument doc = new TurSNSiteSpotlightDocument();
        TurSNSiteSpotlightTerm term = new TurSNSiteSpotlightTerm();

        when(spotlightRepository.findById("id")).thenReturn(Optional.of(spotlight));
        when(documentRepository.findByTurSNSiteSpotlight(spotlight)).thenReturn(Collections.singleton(doc));
        when(termRepository.findByTurSNSiteSpotlight(spotlight)).thenReturn(Collections.singleton(term));

        TurSNSiteSpotlightDto result = api.turSNSiteSpotlightGet("site", "id");

        assertThat(result.getTurSNSiteSpotlightDocuments()).contains(doc);
        assertThat(result.getTurSNSiteSpotlightTerms()).contains(term);
    }

    @Test
    void testSpotlightDeleteAlwaysTrue() {
        TurSNSiteSpotlightRepository spotlightRepository = mock(TurSNSiteSpotlightRepository.class);
        TurSNSiteSpotlightMapper spotlightMapper = Mappers.getMapper(TurSNSiteSpotlightMapper.class);
        TurSNSiteSpotlightAPI api = new TurSNSiteSpotlightAPI(mock(TurSNSiteRepository.class), spotlightRepository,
                mock(TurSNSiteSpotlightDocumentRepository.class), mock(TurSNSiteSpotlightTermRepository.class),
                spotlightMapper);

        boolean result = api.turSNSiteSpotlightDelete("id", "site");

        assertThat(result).isTrue();
        verify(spotlightRepository).deleteById("id");
    }

    @Test
    void testSpotlightStructureSetsSite() {
        TurSNSiteRepository siteRepository = mock(TurSNSiteRepository.class);
        TurSNSiteSpotlightMapper spotlightMapper = Mappers.getMapper(TurSNSiteSpotlightMapper.class);
        TurSNSiteSpotlightAPI api = new TurSNSiteSpotlightAPI(siteRepository,
                mock(TurSNSiteSpotlightRepository.class), mock(TurSNSiteSpotlightDocumentRepository.class),
                mock(TurSNSiteSpotlightTermRepository.class), spotlightMapper);
        TurSNSite site = new TurSNSite();

        when(siteRepository.findById("site")).thenReturn(Optional.of(site));

        TurSNSiteSpotlightDto result = api.turSNSiteSpotlightStructure("site");

        assertThat(result.getTurSNSite()).isSameAs(site);
    }
}
