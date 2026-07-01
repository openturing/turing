/*
 * Copyright (C) 2016-2026 the original author or authors.
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
package com.viglet.turing.onstartup.sn;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.viglet.turing.persistence.model.sn.TurSNSite;
import com.viglet.turing.persistence.repository.sn.TurSNSiteRepository;
import com.viglet.turing.sn.facet.TurSNSiteFacetProfileService;

@ExtendWith(MockitoExtension.class)
class TurSNSiteFacetProfileOnStartupTest {

    @Mock
    private TurSNSiteRepository turSNSiteRepository;
    @Mock
    private TurSNSiteFacetProfileService turSNSiteFacetProfileService;

    @InjectMocks
    private TurSNSiteFacetProfileOnStartup onStartup;

    private static TurSNSite site(String id) {
        TurSNSite site = new TurSNSite();
        site.setId(id);
        site.setName(id);
        return site;
    }

    @Test
    void ensuresDefaultProfileForEverySite() {
        TurSNSite siteA = site("site-a");
        TurSNSite siteB = site("site-b");
        when(turSNSiteRepository.findAll()).thenReturn(List.of(siteA, siteB));

        onStartup.run(null);

        verify(turSNSiteFacetProfileService).ensureDefaultProfile(siteA);
        verify(turSNSiteFacetProfileService).ensureDefaultProfile(siteB);
    }

    @Test
    void doesNothingWhenThereAreNoSites() {
        when(turSNSiteRepository.findAll()).thenReturn(List.of());

        onStartup.run(null);

        verifyNoInteractions(turSNSiteFacetProfileService);
    }
}
