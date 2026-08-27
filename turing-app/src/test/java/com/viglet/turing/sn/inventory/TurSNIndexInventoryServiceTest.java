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
package com.viglet.turing.sn.inventory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Locale;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.viglet.turing.persistence.model.sn.TurSNSite;
import com.viglet.turing.persistence.model.sn.locale.TurSNSiteLocale;
import com.viglet.turing.persistence.repository.sn.locale.TurSNSiteLocaleRepository;
import com.viglet.turing.plugins.se.TurSEInventoryEntry;
import com.viglet.turing.plugins.se.TurSEInventoryPage;
import com.viglet.turing.plugins.se.TurSearchEnginePlugin;
import com.viglet.turing.plugins.se.TurSearchEnginePluginFactory;

/**
 * T1029 / §XX.16 — the audit loop's Turing half. What matters here is that the
 * service never turns "cannot answer" into "the index is empty", since a caller
 * acting on the latter republishes a whole source.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@ExtendWith(MockitoExtension.class)
class TurSNIndexInventoryServiceTest {

    @Mock
    private TurSNSiteLocaleRepository snSiteLocaleRepository;

    @Mock
    private TurSearchEnginePluginFactory pluginFactory;

    @Mock
    private TurSearchEnginePlugin plugin;

    private TurSNIndexInventoryService service;
    private TurSNSite site;
    private TurSNSiteLocale enLocale;
    private TurSNSiteLocale ptLocale;

    @BeforeEach
    void setUp() {
        service = new TurSNIndexInventoryService(snSiteLocaleRepository, pluginFactory);
        site = new TurSNSite();
        site.setId("site-1");
        site.setName("Sample");
        enLocale = locale("en");
        ptLocale = locale("pt-BR");
    }

    private static TurSNSiteLocale locale(String tag) {
        TurSNSiteLocale locale = new TurSNSiteLocale();
        locale.setLanguage(Locale.forLanguageTag(tag));
        return locale;
    }

    private void givenLocales(TurSNSiteLocale... locales) {
        when(snSiteLocaleRepository.findByTurSNSiteOrderByPositionAsc(site))
                .thenReturn(List.of(locales));
    }

    private void givenPage(TurSEInventoryPage page) {
        when(pluginFactory.getPluginForSite(site)).thenReturn(plugin);
        when(plugin.retrieveInventoryPage(any(), any(), any(), anyInt())).thenReturn(page);
    }

    @Test
    @DisplayName("With no locale named, the site's first locale by position is enumerated")
    void defaultsToTheFirstLocale() {
        givenLocales(enLocale, ptLocale);
        givenPage(TurSEInventoryPage.of(
                List.of(new TurSEInventoryEntry("a", "Page", 5L, "modification_date")), 1));

        var response = service.inventory(site, null, "aem", null, 0);

        assertThat(response.locale()).isEqualTo("en");
        assertThat(response.locales()).containsExactly("en", "pt-BR");
        assertThat(response.source()).isEqualTo("aem");
        assertThat(response.entries()).hasSize(1);
        assertThat(response.nextCursor()).isEqualTo("a");
        assertThat(response.supported()).isTrue();
        verify(plugin).retrieveInventoryPage(eq(enLocale), eq("aem"), eq(null),
                eq(TurSNIndexInventoryService.DEFAULT_ROWS));
    }

    @Test
    @DisplayName("A named locale is resolved against the site's configured indexes")
    void resolvesTheNamedLocale() {
        givenLocales(enLocale, ptLocale);
        when(snSiteLocaleRepository.findByTurSNSiteAndLanguage(site, Locale.forLanguageTag("pt-BR")))
                .thenReturn(ptLocale);
        givenPage(TurSEInventoryPage.empty());

        var response = service.inventory(site, "pt-BR", null, null, 50);

        assertThat(response.locale()).isEqualTo("pt-BR");
        verify(plugin).retrieveInventoryPage(eq(ptLocale), eq(null), eq(null), eq(50));
    }

    @Test
    @DisplayName("A locale the site has no index for is a bad request, not an empty index")
    void unknownLocaleIsRejected() {
        givenLocales(enLocale);
        when(snSiteLocaleRepository.findByTurSNSiteAndLanguage(site, Locale.forLanguageTag("de")))
                .thenReturn(null);

        assertThatThrownBy(() -> service.inventory(site, "de", null, null, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("no index for locale 'de'");
    }

    @Test
    @DisplayName("A site with no locale configured has no index to enumerate")
    void siteWithoutLocalesIsRejected() {
        when(snSiteLocaleRepository.findByTurSNSiteOrderByPositionAsc(site)).thenReturn(List.of());

        assertThatThrownBy(() -> service.inventory(site, null, null, null, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("no locale configured");
    }

    @Test
    @DisplayName("Page size is clamped to the ceiling so a sweep pages instead of timing out")
    void rowsAreClamped() {
        givenLocales(enLocale);
        givenPage(TurSEInventoryPage.empty());

        service.inventory(site, null, null, null, 100_000);

        ArgumentCaptor<Integer> rows = ArgumentCaptor.forClass(Integer.class);
        verify(plugin).retrieveInventoryPage(any(), any(), any(), rows.capture());
        assertThat(rows.getValue()).isEqualTo(TurSNIndexInventoryService.MAX_ROWS);
    }

    @Test
    @DisplayName("Blank source and cursor are normalised to null — an empty filter is no filter")
    void blankFiltersAreNormalised() {
        givenLocales(enLocale);
        givenPage(TurSEInventoryPage.empty());

        var response = service.inventory(site, "  ", "  ", "  ", 10);

        assertThat(response.locale()).isEqualTo("en");
        assertThat(response.source()).isNull();
        verify(plugin).retrieveInventoryPage(eq(enLocale), eq(null), eq(null), eq(10));
    }

    @Test
    @DisplayName("An unreachable index reports unsupported, never an empty page")
    void engineFailureIsReportedAsUnsupported() {
        givenLocales(enLocale);
        when(pluginFactory.getPluginForSite(site)).thenReturn(plugin);
        when(plugin.retrieveInventoryPage(any(), any(), any(), anyInt()))
                .thenThrow(new IllegalStateException("connection refused"));

        var response = service.inventory(site, null, "aem", null, 0);

        assertThat(response.supported()).isFalse();
        assertThat(response.entries()).isEmpty();
        assertThat(response.remaining()).isEqualTo(-1L);
    }

    @Test
    @DisplayName("The engine's remaining count and cursor reach the response unchanged")
    void pageFieldsArePassedThrough() {
        givenLocales(enLocale);
        givenPage(TurSEInventoryPage.of(List.of(
                new TurSEInventoryEntry("a", "Page", 1L, "modification_date"),
                new TurSEInventoryEntry("b", null, null, null)), 42));

        var response = service.inventory(site, null, null, "previous", 2);

        assertThat(response.remaining()).isEqualTo(42L);
        assertThat(response.hasMore()).isTrue();
        assertThat(response.nextCursor()).isEqualTo("b");
        assertThat(response.siteId()).isEqualTo("site-1");
        assertThat(response.siteName()).isEqualTo("Sample");
        verify(plugin).retrieveInventoryPage(eq(enLocale), eq(null), eq("previous"), eq(2));
    }
}
