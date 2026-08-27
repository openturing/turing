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
package com.viglet.turing.exchange.sn;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import com.viglet.turing.persistence.model.sn.TurSNSite;
import com.viglet.turing.persistence.model.sn.locale.TurSNSiteLocale;
import com.viglet.turing.persistence.repository.sn.TurSNSiteRepository;
import com.viglet.turing.persistence.repository.sn.locale.TurSNSiteLocaleRepository;
import com.viglet.turing.plugins.se.TurSearchEnginePlugin;
import com.viglet.turing.plugins.se.TurSearchEnginePluginFactory;
import com.viglet.turing.sn.field.TurSNSiteFieldService;

/**
 * T1033 / §D.13 — the paging rule behind the export. What matters is which call
 * the engine gets (cursor when it has one, offset when it does not) and that the
 * loop terminates: a cursor it cannot advance would otherwise re-serve the same
 * page forever, which is worse than the quadratic read it replaced.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TurSNSiteContentPagingTest {

    private static final int PAGE = 500;

    @Mock
    private TurSNSiteRepository turSNSiteRepository;
    @Mock
    private TurSNSiteLocaleRepository turSNSiteLocaleRepository;
    @Mock
    private TurSearchEnginePluginFactory pluginFactory;
    @Mock
    private TurSNSiteFieldService turSNSiteFieldService;
    @Mock
    private TurSearchEnginePlugin plugin;

    private TurSNSiteContentExchangeService service;

    @BeforeEach
    void setUp() {
        service = new TurSNSiteContentExchangeService(turSNSiteRepository, turSNSiteLocaleRepository,
                pluginFactory, turSNSiteFieldService,
                org.mockito.Mockito.mock(com.viglet.turing.genai.TurSNGenAi.class),
                org.mockito.Mockito.mock(com.viglet.turing.genai.TurGenAiContextFactory.class),
                org.mockito.Mockito.mock(com.viglet.turing.genai.TurDefaultAgentResolver.class),
                new com.viglet.turing.properties.TurConfigProperties());

        TurSNSite site = new TurSNSite();
        site.setId("site-1");
        site.setName("Sample");
        TurSNSiteLocale locale = new TurSNSiteLocale();
        locale.setLanguage(Locale.US);

        when(turSNSiteRepository.findById("site-1")).thenReturn(Optional.of(site));
        when(turSNSiteLocaleRepository.findByTurSNSite(site)).thenReturn(List.of(locale));
        when(pluginFactory.getPluginForSite(site)).thenReturn(plugin);
        when(turSNSiteFieldService.toMap(site)).thenReturn(Map.of());
        when(plugin.getDocumentTotal(any())).thenReturn(0L);
    }

    private static List<Map<String, Object>> page(int size, int firstIndex) {
        List<Map<String, Object>> docs = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            Map<String, Object> doc = new LinkedHashMap<>();
            doc.put("id", "doc-%05d".formatted(firstIndex + i));
            docs.add(doc);
        }
        return docs;
    }

    /** Drains every locale of an opened export into one flat list of ids. */
    private List<String> drain() {
        var content = service.openContentExport("site-1", "task-1").orElseThrow();
        List<String> ids = new ArrayList<>();
        for (var locale : content.locales()) {
            while (locale.pages().hasNext()) {
                locale.pages().next().forEach(doc -> ids.add(doc.get("id").toString()));
            }
        }
        return ids;
    }

    @Test
    @DisplayName("An engine with a cursor is paged by cursor, never by offset")
    void cursorCapableEngineIsPagedByCursor() {
        when(plugin.supportsCursorPaging()).thenReturn(true);
        when(plugin.retrieveDocumentPageAfter(any(), any(), eq(null), eq(PAGE)))
                .thenReturn(page(PAGE, 0));
        when(plugin.retrieveDocumentPageAfter(any(), any(), eq("doc-00499"), eq(PAGE)))
                .thenReturn(page(3, PAGE));

        List<String> ids = drain();

        assertThat(ids).hasSize(PAGE + 3);
        assertThat(ids.get(0)).isEqualTo("doc-00000");
        assertThat(ids.get(PAGE + 2)).isEqualTo("doc-00502");
        verify(plugin, never()).retrieveDocumentPage(any(), any(), anyInt(), anyInt());
    }

    @Test
    @DisplayName("An engine without a cursor keeps the offset loop — slow, but still correct")
    void engineWithoutCursorFallsBackToOffset() {
        when(plugin.supportsCursorPaging()).thenReturn(false);
        when(plugin.retrieveDocumentPage(any(), any(), eq(0), eq(PAGE))).thenReturn(page(PAGE, 0));
        when(plugin.retrieveDocumentPage(any(), any(), eq(PAGE), eq(PAGE))).thenReturn(page(2, PAGE));

        List<String> ids = drain();

        assertThat(ids).hasSize(PAGE + 2);
        verify(plugin, never()).retrieveDocumentPageAfter(any(), any(), any(), anyInt());
    }

    @Test
    @DisplayName("A short first page ends the walk without a second engine call")
    void shortPageEndsTheWalk() {
        when(plugin.supportsCursorPaging()).thenReturn(true);
        when(plugin.retrieveDocumentPageAfter(any(), any(), eq(null), eq(PAGE)))
                .thenReturn(page(7, 0));

        assertThat(drain()).hasSize(7);
        verify(plugin).retrieveDocumentPageAfter(any(), any(), eq(null), eq(PAGE));
        verify(plugin, never()).retrieveDocumentPageAfter(any(), any(), eq("doc-00006"), anyInt());
    }

    @Test
    @DisplayName("An empty index yields no pages at all")
    void emptyIndexYieldsNothing() {
        when(plugin.supportsCursorPaging()).thenReturn(true);
        when(plugin.retrieveDocumentPageAfter(any(), any(), any(), anyInt())).thenReturn(List.of());

        assertThat(drain()).isEmpty();
    }

    @Test
    @DisplayName("A full page whose last document has no id ends the walk instead of looping forever")
    void unadvanceableCursorTerminates() {
        when(plugin.supportsCursorPaging()).thenReturn(true);
        List<Map<String, Object>> idless = page(PAGE, 0);
        idless.get(PAGE - 1).put("id", "");
        when(plugin.retrieveDocumentPageAfter(any(), any(), eq(null), eq(PAGE))).thenReturn(idless);

        // Terminating is the point: without the guard this call never returns.
        assertThat(drain()).hasSize(PAGE);
    }

    @Test
    @DisplayName("Nothing is read until the pages are pulled")
    void openingReadsNothing() {
        when(plugin.supportsCursorPaging()).thenReturn(true);

        service.openContentExport("site-1", "task-1");

        verify(plugin, never()).retrieveDocumentPageAfter(any(), any(), any(), anyInt());
        verify(plugin, never()).retrieveDocumentPage(any(), any(), anyInt(), anyInt());
    }

    @Test
    @DisplayName("An unknown site id opens no export")
    void unknownSiteOpensNothing() {
        when(turSNSiteRepository.findById("nope")).thenReturn(Optional.empty());

        assertThat(service.openContentExport("nope", "task-1")).isEmpty();
    }

    @Test
    @DisplayName("Export progress advances as pages are pulled, not when the export is opened")
    void progressFollowsThePages() {
        when(plugin.supportsCursorPaging()).thenReturn(true);
        when(plugin.getDocumentTotal(any())).thenReturn(503L);
        when(plugin.retrieveDocumentPageAfter(any(), any(), eq(null), eq(PAGE)))
                .thenReturn(page(PAGE, 0));
        when(plugin.retrieveDocumentPageAfter(any(), any(), eq("doc-00499"), eq(PAGE)))
                .thenReturn(page(3, PAGE));

        var content = service.openContentExport("site-1", "task-1").orElseThrow();
        assertThat(service.getProgress("task-1").orElseThrow().processedDocuments()).isZero();

        var pages = content.locales().get(0).pages();
        pages.next();
        assertThat(service.getProgress("task-1").orElseThrow().processedDocuments()).isEqualTo(PAGE);

        pages.next();
        assertThat(service.getProgress("task-1").orElseThrow().processedDocuments()).isEqualTo(503);
        assertThat(service.getProgress("task-1").orElseThrow().percentage()).isEqualTo(100);
    }
}
