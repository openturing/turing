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

package com.viglet.turing.api.sn.search;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Locale;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import com.viglet.turing.commons.se.result.spellcheck.TurSESpellCheckResult;
import com.viglet.turing.commons.sn.bean.spellcheck.TurSNSiteSpellCheckBean;
import com.viglet.turing.persistence.model.sn.TurSNSite;
import com.viglet.turing.persistence.repository.sn.TurSNSiteRepository;
import com.viglet.turing.plugins.se.TurSearchEnginePlugin;
import com.viglet.turing.plugins.se.TurSearchEnginePluginFactory;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Unit tests for TurSNSiteSpellCheckAPI. Since T686 the endpoint resolves the
 * site's search-engine plugin and delegates to its {@code spellCheck} seam
 * rather than calling Solr directly.
 *
 * @author Alexandre Oliveira
 * @since 2026.1.10
 */
@ExtendWith(MockitoExtension.class)
class TurSNSiteSpellCheckAPITest {

    @Test
    void testSpellCheckReturnsNullWhenSiteMissing() {
        TurSearchEnginePluginFactory pluginFactory = mock(TurSearchEnginePluginFactory.class);
        TurSNSiteRepository siteRepository = mock(TurSNSiteRepository.class);
        TurSNSiteSpellCheckAPI api = new TurSNSiteSpellCheckAPI(pluginFactory, siteRepository);

        when(siteRepository.findByNameIgnoreCase("site")).thenReturn(Optional.empty());

        TurSNSiteSpellCheckBean result = api.turSNSiteSpellCheck("site", "en_US", "helo",
                mock(HttpServletRequest.class));

        assertThat(result).isNull();
        verifyNoInteractions(pluginFactory);
    }

    @Test
    void testSpellCheckBuildsResponseWhenSiteExists() {
        TurSearchEnginePluginFactory pluginFactory = mock(TurSearchEnginePluginFactory.class);
        TurSNSiteRepository siteRepository = mock(TurSNSiteRepository.class);
        TurSearchEnginePlugin plugin = mock(TurSearchEnginePlugin.class);
        TurSNSiteSpellCheckAPI api = new TurSNSiteSpellCheckAPI(pluginFactory, siteRepository);
        HttpServletRequest request = mock(HttpServletRequest.class);
        TurSNSite site = new TurSNSite();
        site.setName("site");

        when(siteRepository.findByNameIgnoreCase("site")).thenReturn(Optional.of(site));
        when(pluginFactory.getPluginForSite(site)).thenReturn(plugin);
        TurSESpellCheckResult spellCheckResult = new TurSESpellCheckResult(true, "hello");
        spellCheckResult.setUsingCorrected(true);
        when(plugin.spellCheck(eq("site"), eq("helo"), any(Locale.class)))
                .thenReturn(spellCheckResult);
        when(request.getRequestURL()).thenReturn(new StringBuffer("http://example.com/api"));
        when(request.getQueryString()).thenReturn("q=helo");

        TurSNSiteSpellCheckBean result = api.turSNSiteSpellCheck("site", "en_US", "helo", request);

        assertThat(result).isNotNull();
        assertThat(result.isCorrectedText()).isTrue();
        assertThat(result.isUsingCorrectedText()).isTrue();
        assertThat(result.getCorrected().getText()).isEqualTo("hello");
        assertThat(result.getOriginal().getText()).isEqualTo("helo");
    }

    @Test
    void testSpellCheckKeepsOriginalWhenNotCorrected() {
        TurSearchEnginePluginFactory pluginFactory = mock(TurSearchEnginePluginFactory.class);
        TurSNSiteRepository siteRepository = mock(TurSNSiteRepository.class);
        TurSearchEnginePlugin plugin = mock(TurSearchEnginePlugin.class);
        TurSNSiteSpellCheckAPI api = new TurSNSiteSpellCheckAPI(pluginFactory, siteRepository);
        HttpServletRequest request = mock(HttpServletRequest.class);
        TurSNSite site = new TurSNSite();
        site.setName("site");

        when(siteRepository.findByNameIgnoreCase("site")).thenReturn(Optional.of(site));
        when(pluginFactory.getPluginForSite(site)).thenReturn(plugin);
        when(plugin.spellCheck(eq("site"), eq("query"), any(Locale.class)))
                .thenReturn(new TurSESpellCheckResult(false, ""));
        when(request.getRequestURL()).thenReturn(new StringBuffer("http://example.com/api"));
        when(request.getQueryString()).thenReturn("q=query");

        TurSNSiteSpellCheckBean result = api.turSNSiteSpellCheck("site", "en_US", "query", request);

        assertThat(result).isNotNull();
        assertThat(result.isCorrectedText()).isFalse();
        assertThat(result.getOriginal().getText()).isEqualTo("query");
    }
}
