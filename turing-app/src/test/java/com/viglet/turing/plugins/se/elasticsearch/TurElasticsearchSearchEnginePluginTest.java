/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.plugins.se.elasticsearch;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.util.Collections;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.viglet.turing.commons.se.TurSEParameters;
import com.viglet.turing.commons.se.field.TurSEFieldType;
import com.viglet.turing.commons.sn.TurSNConfig;
import com.viglet.turing.commons.sn.bean.TurSNSearchParams;
import com.viglet.turing.commons.sn.search.TurSNSiteSearchContext;
import com.viglet.turing.elasticsearch.TurElasticsearch;
import com.viglet.turing.elasticsearch.TurElasticsearchInstance;
import com.viglet.turing.elasticsearch.TurElasticsearchInstanceProcess;
import com.viglet.turing.persistence.model.se.TurSEInstance;
import com.viglet.turing.persistence.model.sn.TurSNSite;
import com.viglet.turing.persistence.model.sn.locale.TurSNSiteLocale;
import com.viglet.turing.persistence.repository.sn.field.TurSNSiteFieldExtRepository;
import com.viglet.turing.se.result.TurSEResults;

/**
 * Tests for TurElasticsearchSearchEnginePlugin.
 *
 * @author Alexandre Oliveira
 * @since 2026.1.14
 */
@ExtendWith(MockitoExtension.class)
class TurElasticsearchSearchEnginePluginTest {

    @Mock
    private TurElasticsearch turElasticsearch;
    @Mock
    private TurElasticsearchInstanceProcess turElasticsearchInstanceProcess;
    @Mock
    private TurSNSiteFieldExtRepository turSNSiteFieldExtRepository;

    @InjectMocks
    private TurElasticsearchSearchEnginePlugin plugin;

    private TurSNSiteSearchContext context;

    @BeforeEach
    void setUp() {
        TurSNConfig config = new TurSNConfig();
        TurSEParameters parameters = new TurSEParameters(new TurSNSearchParams());
        context = new TurSNSiteSearchContext("site-es", config, parameters, Locale.ENGLISH,
                URI.create("http://localhost"));
    }

    // ---- getPluginType -------------------------------------------------------

    @Test
    void shouldExposePluginTypeAsElasticsearch() {
        assertEquals("elasticsearch", plugin.getPluginType());
    }

    // ---- retrieveSearchResults -----------------------------------------------

    @Test
    void shouldRetrieveSearchResultsFromElasticsearchPlugin() {
        TurElasticsearchInstance instance = mock(TurElasticsearchInstance.class);
        TurSEResults results = TurSEResults.builder().queryString("q-es").build();

        when(turElasticsearchInstanceProcess.initElasticsearchInstance("site-es", Locale.ENGLISH))
                .thenReturn(Optional.of(instance));
        when(turElasticsearch.retrieveElasticsearchFromSN(instance, context)).thenReturn(Optional.of(results));

        Optional<TurSEResults> response = plugin.retrieveSearchResults(context);

        assertTrue(response.isPresent());
        assertEquals("q-es", response.get().getQueryString());
        verify(turElasticsearch).retrieveElasticsearchFromSN(instance, context);
    }

    @Test
    void shouldReturnEmptyWhenElasticsearchInstanceCannotBeInitialized() {
        when(turElasticsearchInstanceProcess.initElasticsearchInstance("site-es", Locale.ENGLISH))
                .thenReturn(Optional.empty());

        Optional<TurSEResults> response = plugin.retrieveSearchResults(context);

        assertFalse(response.isPresent());
    }

    // ---- retrieveFacetResults ------------------------------------------------

    @Test
    void shouldRetrieveFacetResultsFromElasticsearchPlugin() {
        TurElasticsearchInstance instance = mock(TurElasticsearchInstance.class);
        TurSEResults results = TurSEResults.builder().queryString("facet-es").build();

        when(turElasticsearchInstanceProcess.initElasticsearchInstance("site-es", Locale.ENGLISH))
                .thenReturn(Optional.of(instance));
        when(turElasticsearch.retrieveFacetElasticsearchFromSN(instance, context, "author"))
                .thenReturn(Optional.of(results));

        Optional<TurSEResults> response = plugin.retrieveFacetResults(context, "author");

        assertTrue(response.isPresent());
        assertEquals("facet-es", response.get().getQueryString());
        verify(turElasticsearch).retrieveFacetElasticsearchFromSN(instance, context, "author");
    }

    @Test
    void shouldReturnEmptyFacetResultsWhenInstanceUnavailable() {
        when(turElasticsearchInstanceProcess.initElasticsearchInstance("site-es", Locale.ENGLISH))
                .thenReturn(Optional.empty());

        Optional<TurSEResults> response = plugin.retrieveFacetResults(context, "author");

        assertFalse(response.isPresent());
    }

    // ---- indexDocument -------------------------------------------------------

    @Test
    void shouldIndexDocumentSuccessfully() {
        TurElasticsearchInstance instance = mock(TurElasticsearchInstance.class);
        TurSNSite site = new TurSNSite();
        site.setName("site-es");
        Map<String, Object> attrs = Map.of("title", "doc1");

        when(turElasticsearchInstanceProcess.initElasticsearchInstance("site-es", Locale.ENGLISH))
                .thenReturn(Optional.of(instance));

        boolean result = plugin.indexDocument(site, Locale.ENGLISH, attrs);

        assertTrue(result);
        verify(turElasticsearch).indexing(instance, site, attrs);
    }

    @Test
    void shouldReturnFalseWhenIndexDocumentInstanceUnavailable() {
        TurSNSite site = new TurSNSite();
        site.setName("site-es");

        when(turElasticsearchInstanceProcess.initElasticsearchInstance("site-es", Locale.ENGLISH))
                .thenReturn(Optional.empty());

        boolean result = plugin.indexDocument(site, Locale.ENGLISH, Collections.emptyMap());

        assertFalse(result);
    }

    // ---- deIndex --------------------------------------------------------------

    @Test
    void shouldDeIndexDocumentSuccessfully() {
        TurElasticsearchInstance instance = mock(TurElasticsearchInstance.class);
        TurSNSite site = new TurSNSite();
        site.setName("site-es");

        when(turElasticsearchInstanceProcess.initElasticsearchInstance("site-es", Locale.ENGLISH))
                .thenReturn(Optional.of(instance));

        boolean result = plugin.deIndex(site, Locale.ENGLISH, "doc-456");

        assertTrue(result);
        verify(turElasticsearch).deIndexing(instance, "doc-456");
    }

    @Test
    void shouldReturnFalseWhenDeIndexInstanceUnavailable() {
        TurSNSite site = new TurSNSite();
        site.setName("site-es");

        when(turElasticsearchInstanceProcess.initElasticsearchInstance("site-es", Locale.ENGLISH))
                .thenReturn(Optional.empty());

        boolean result = plugin.deIndex(site, Locale.ENGLISH, "doc-456");

        assertFalse(result);
    }

    // ---- deIndexByType -------------------------------------------------------

    @Test
    void shouldDeIndexByTypeSuccessfully() {
        TurElasticsearchInstance instance = mock(TurElasticsearchInstance.class);
        TurSNSite site = new TurSNSite();
        site.setName("site-es");

        when(turElasticsearchInstanceProcess.initElasticsearchInstance("site-es", Locale.ENGLISH))
                .thenReturn(Optional.of(instance));

        boolean result = plugin.deIndexByType(site, Locale.ENGLISH, "article");

        assertTrue(result);
        verify(turElasticsearch).deIndexingByType(instance, "article");
    }

    @Test
    void shouldReturnFalseWhenDeIndexByTypeInstanceUnavailable() {
        TurSNSite site = new TurSNSite();
        site.setName("site-es");

        when(turElasticsearchInstanceProcess.initElasticsearchInstance("site-es", Locale.ENGLISH))
                .thenReturn(Optional.empty());

        boolean result = plugin.deIndexByType(site, Locale.ENGLISH, "article");

        assertFalse(result);
    }

    // ---- commit --------------------------------------------------------------

    @Test
    void shouldReturnTrueForCommit() {
        TurSNSite site = new TurSNSite();
        site.setName("site-es");

        // Elasticsearch commits are synchronous — always returns true
        boolean result = plugin.commit(site, Locale.ENGLISH);

        assertTrue(result);
    }

    // ---- getDocumentTotal ----------------------------------------------------

    @Test
    void shouldReturnDocumentTotal() {
        TurElasticsearchInstance instance = mock(TurElasticsearchInstance.class);
        TurSNSiteLocale siteLocale = new TurSNSiteLocale();

        when(turElasticsearchInstanceProcess.initElasticsearchInstance(siteLocale))
                .thenReturn(Optional.of(instance));
        when(turElasticsearch.getDocumentTotal(instance)).thenReturn(99L);

        long total = plugin.getDocumentTotal(siteLocale);

        assertEquals(99L, total);
    }

    @Test
    void shouldReturnZeroWhenDocumentTotalInstanceUnavailable() {
        TurSNSiteLocale siteLocale = new TurSNSiteLocale();

        when(turElasticsearchInstanceProcess.initElasticsearchInstance(siteLocale))
                .thenReturn(Optional.empty());

        long total = plugin.getDocumentTotal(siteLocale);

        assertEquals(0L, total);
    }

    // ---- indexExists ---------------------------------------------------------

    @Test
    void shouldAlwaysReturnTrueForIndexExists() {
        TurSEInstance seInstance = new TurSEInstance();
        seInstance.setEndpointUrl("http://localhost:9200");

        assertTrue(plugin.indexExists(seInstance, "my-index"));
    }

    // ---- Schema management (schema-less no-ops) ------------------------------

    @Test
    void shouldNoOpForAddOrUpdateField() {
        TurSEInstance seInstance = new TurSEInstance();
        // Schema-less — should not throw
        assertDoesNotThrow(() ->
                plugin.addOrUpdateField(seInstance, "idx", "title", TurSEFieldType.TEXT, true, false, true));
    }

    @Test
    void shouldNoOpForDeleteField() {
        TurSEInstance seInstance = new TurSEInstance();
        assertDoesNotThrow(() ->
                plugin.deleteField(seInstance, "idx", "title", TurSEFieldType.TEXT));
    }

    @Test
    void shouldReturnFalseForFieldExists() {
        TurSEInstance seInstance = new TurSEInstance();
        assertFalse(plugin.fieldExists(seInstance, "idx", "title"));
    }

    // ---- getSystemInfo -------------------------------------------------------

    @Test
    void shouldReturnSystemInfoWithEngineKey() {
        TurSEInstance seInstance = new TurSEInstance();
        seInstance.setEndpointUrl("http://localhost:9200");

        Map<String, String> info = plugin.getSystemInfo(seInstance);

        assertEquals("Elasticsearch", info.get("engine"));
        assertNotNull(info.get("status"));
    }

    @Test
    void shouldReturnDownStatusWhenElasticsearchUnreachable() {
        TurSEInstance seInstance = new TurSEInstance();
        seInstance.setEndpointUrl("http://invalid-host:9999");

        Map<String, String> info = plugin.getSystemInfo(seInstance);

        assertEquals("Elasticsearch", info.get("engine"));
        assertEquals("DOWN", info.get("status"));
        assertTrue(info.containsKey("error"));
    }

    // ---- createIndex ---------------------------------------------------------

    @Test
    void shouldDelegateCreateIndexWithFieldTypes() {
        TurSEInstance seInstance = new TurSEInstance();
        seInstance.setEndpointUrl("http://localhost:9200");
        TurSNSiteLocale siteLocale = new TurSNSiteLocale();
        siteLocale.setLanguage(Locale.ENGLISH);

        Map<String, TurSEFieldType> fieldTypes = Map.of("title", TurSEFieldType.TEXT);

        assertDoesNotThrow(() ->
                plugin.createIndex(seInstance, siteLocale, "my-index", fieldTypes));
    }

    @Test
    void shouldBuildFieldTypesFromSiteWhenEmpty() {
        TurSEInstance seInstance = new TurSEInstance();
        seInstance.setEndpointUrl("http://localhost:9200");
        TurSNSiteLocale siteLocale = new TurSNSiteLocale();
        siteLocale.setLanguage(Locale.ENGLISH);
        siteLocale.setTurSNSite(null);

        assertDoesNotThrow(() ->
                plugin.createIndex(seInstance, siteLocale, "my-index", Collections.emptyMap()));
    }

    // ---- deleteIndex ---------------------------------------------------------

    @Test
    void shouldDelegateDeleteIndex() {
        TurSEInstance seInstance = new TurSEInstance();
        seInstance.setEndpointUrl("http://localhost:9200");

        assertDoesNotThrow(() -> plugin.deleteIndex(seInstance, "my-index"));
    }

    // ---- clearIndex ----------------------------------------------------------

    @Test
    void shouldDelegateClearIndex() {
        TurSEInstance seInstance = new TurSEInstance();
        seInstance.setEndpointUrl("http://localhost:9200");

        assertDoesNotThrow(() -> plugin.clearIndex(seInstance, "my-index"));
    }

    // ---- listIndexes ---------------------------------------------------------

    @Test
    void shouldDelegateListIndexes() {
        TurSEInstance seInstance = new TurSEInstance();
        seInstance.setEndpointUrl("http://localhost:9200");

        assertDoesNotThrow(() -> plugin.listIndexes(seInstance));
    }
}
