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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.viglet.turing.persistence.model.sn.spotlight.TurSNSiteSpotlight;
import com.viglet.turing.persistence.model.sn.spotlight.TurSNSiteSpotlightTerm;

/**
 * Unit tests for TurSpotlightCache.
 *
 * @author Alexandre Oliveira
 * @since 2026.1.10
 */
class TurSpotlightCacheTest {

    @Test
    void testFindTermsBySNSiteAndLanguage() {
        TurSNSiteSpotlight spotlight = mock(TurSNSiteSpotlight.class);
        TurSNSiteSpotlightTerm term = mock(TurSNSiteSpotlightTerm.class);
        when(term.getName()).thenReturn("term");
        when(term.getTurSNSiteSpotlight()).thenReturn(spotlight);
        when(spotlight.getTurSNSiteSpotlightTerms()).thenReturn(Set.of(term));

        TurSpotlightService service = mock(TurSpotlightService.class);
        when(service.findSpotlightBySNSiteAndLanguage("site", Locale.US))
                .thenReturn(List.of(spotlight));

        TurSpotlightCache cache = new TurSpotlightCache(service);
        List<TurSNSpotlightTermCacheBean> results = cache.findTermsBySNSiteAndLanguage("site", Locale.US);

        assertThat(results).hasSize(1);
        assertThat(results.getFirst().getTerm()).isEqualTo("term");
        assertThat(results.getFirst().getSpotlight()).isSameAs(spotlight);
    }
}
