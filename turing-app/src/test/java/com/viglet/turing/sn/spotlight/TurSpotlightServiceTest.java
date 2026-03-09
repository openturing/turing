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

package com.viglet.turing.sn.spotlight;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.viglet.turing.persistence.model.sn.TurSNSite;
import com.viglet.turing.persistence.model.sn.spotlight.TurSNSiteSpotlight;
import com.viglet.turing.persistence.repository.sn.TurSNSiteRepository;
import com.viglet.turing.persistence.repository.sn.spotlight.TurSNSiteSpotlightRepository;

/**
 * Unit tests for TurSpotlightService.
 *
 * @author Alexandre Oliveira
 * @since 2026.1.10
 */
@ExtendWith(MockitoExtension.class)
class TurSpotlightServiceTest {

    @Mock
    private TurSNSiteRepository turSNSiteRepository;

    @Mock
    private TurSNSiteSpotlightRepository turSNSiteSpotlightRepository;

    @Test
    void testFindSpotlightBySNSiteAndLanguageReturnsList() {
        TurSNSite site = new TurSNSite();
        when(turSNSiteRepository.findByName("site")).thenReturn(Optional.of(site));
        List<TurSNSiteSpotlight> spotlights = List.of(new TurSNSiteSpotlight());
        when(turSNSiteSpotlightRepository.findByTurSNSiteAndLanguage(site, Locale.US))
                .thenReturn(spotlights);

        TurSpotlightService service = new TurSpotlightService(turSNSiteRepository,
                turSNSiteSpotlightRepository);

        assertThat(service.findSpotlightBySNSiteAndLanguage("site", Locale.US))
                .isEqualTo(spotlights);
    }

    @Test
    void testFindSpotlightBySNSiteAndLanguageReturnsEmptyWhenSiteMissing() {
        when(turSNSiteRepository.findByName("site")).thenReturn(Optional.empty());

        TurSpotlightService service = new TurSpotlightService(turSNSiteRepository,
                turSNSiteSpotlightRepository);

        assertThat(service.findSpotlightBySNSiteAndLanguage("site", Locale.US))
                .isEqualTo(Collections.emptyList());
    }
}
