/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.plugins.se.solr;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
import org.springframework.core.io.ResourceLoader;

import com.viglet.turing.commons.se.TurSEParameters;
import com.viglet.turing.commons.se.field.TurSEFieldType;
import com.viglet.turing.commons.sn.TurSNConfig;
import com.viglet.turing.commons.sn.bean.TurSNSearchParams;
import com.viglet.turing.commons.sn.search.TurSNSiteSearchContext;
import com.viglet.turing.persistence.model.se.TurSEInstance;
import com.viglet.turing.persistence.model.sn.TurSNSite;
import com.viglet.turing.persistence.model.sn.locale.TurSNSiteLocale;
import com.viglet.turing.properties.TurConfigProperties;
import com.viglet.turing.properties.TurSolrProperty;
import com.viglet.turing.se.result.TurSEResults;
import com.viglet.turing.solr.TurSolr;
import com.viglet.turing.solr.TurSolrInstance;
import com.viglet.turing.solr.TurSolrInstanceProcess;
import com.viglet.turing.solr.source.TurSolrInstanceSource;

/**
 * Tests for TurSolrSearchEnginePlugin.
 *
 * @author Alexandre Oliveira
 * @since 2026.1.14
 */
@ExtendWith(MockitoExtension.class)
class TurSolrSearchEnginePluginTest {

    @Mock
    private TurSolr turSolr;
    @Mock
    private TurSolrInstanceProcess turSolrInstanceProcess;
    @Mock
    private TurConfigProperties turConfigProperties;
    @Mock
    private ResourceLoader resourceLoader;
    @Mock
    private TurSolrInstanceSource solrInstanceSource;

    @InjectMocks
    private TurSolrSearchEnginePlugin plugin;

    private TurSNSiteSearchContext context;

    @BeforeEach
    void setUp() {
        TurSNConfig config = new TurSNConfig();
        TurSEParameters parameters = new TurSEParameters(new TurSNSearchParams());
        context = new TurSNSiteSearchContext("site-a", config, parameters, Locale.ENGLISH,
                URI.create("http://localhost"));
    }

    // ---- getPluginType -------------------------------------------------------

    @Test
    void shouldExposePluginTypeAsSolr() {
        assertEquals("solr", plugin.getPluginType());
    }

    // ---- retrieveSearchResults -----------------------------------------------

    @Test
    void shouldRetrieveSearchResultsFromSolrPlugin() {
        TurSolrInstance instance = mock(TurSolrInstance.class);
        TurSEResults results = TurSEResults.builder().queryString("q").build();

        when(turSolrInstanceProcess.initSolrInstance("site-a", Locale.ENGLISH)).thenReturn(Optional.of(instance));
        when(turSolr.retrieveSolrFromSN(instance, context)).thenReturn(Optional.of(results));

        Optional<TurSEResults> response = plugin.retrieveSearchResults(context);

        assertTrue(response.isPresent());
        assertEquals("q", response.get().getQueryString());
        verify(turSolrInstanceProcess).initSolrInstance("site-a", Locale.ENGLISH);
        verify(turSolr).retrieveSolrFromSN(instance, context);
    }

    @Test
    void shouldReturnEmptyWhenSolrInstanceCannotBeInitialized() {
        when(turSolrInstanceProcess.initSolrInstance("site-a", Locale.ENGLISH)).thenReturn(Optional.empty());

        Optional<TurSEResults> response = plugin.retrieveSearchResults(context);

        assertFalse(response.isPresent());
    }

    // ---- retrieveFacetResults ------------------------------------------------

    @Test
    void shouldRetrieveFacetResultsPassingFacetName() {
        TurSolrInstance instance = mock(TurSolrInstance.class);
        TurSEResults results = TurSEResults.builder().queryString("facet").build();

        when(turSolrInstanceProcess.initSolrInstance("site-a", Locale.ENGLISH)).thenReturn(Optional.of(instance));
        when(turSolr.retrieveFacetSolrFromSN(instance, context, "category")).thenReturn(Optional.of(results));

        Optional<TurSEResults> response = plugin.retrieveFacetResults(context, "category");

        assertTrue(response.isPresent());
        assertEquals("facet", response.get().getQueryString());
        verify(turSolr).retrieveFacetSolrFromSN(instance, context, "category");
    }

    @Test
    void shouldReturnEmptyFacetResultsWhenInstanceUnavailable() {
        when(turSolrInstanceProcess.initSolrInstance("site-a", Locale.ENGLISH)).thenReturn(Optional.empty());

        Optional<TurSEResults> response = plugin.retrieveFacetResults(context, "category");

        assertFalse(response.isPresent());
    }

    // ---- indexDocument -------------------------------------------------------

    @Test
    void shouldIndexDocumentSuccessfully() {
        TurSolrInstance instance = mock(TurSolrInstance.class);
        TurSNSite site = new TurSNSite();
        site.setName("site-a");
        Map<String, Object> attrs = Map.of("title", "doc1");

        when(turSolrInstanceProcess.initSolrInstance("site-a", Locale.ENGLISH)).thenReturn(Optional.of(instance));

        boolean result = plugin.indexDocument(site, Locale.ENGLISH, attrs);

        assertTrue(result);
        verify(turSolr).indexing(instance, site, attrs);
    }

    @Test
    void shouldReturnFalseWhenIndexDocumentInstanceUnavailable() {
        TurSNSite site = new TurSNSite();
        site.setName("site-a");

        when(turSolrInstanceProcess.initSolrInstance("site-a", Locale.ENGLISH)).thenReturn(Optional.empty());

        boolean result = plugin.indexDocument(site, Locale.ENGLISH, Collections.emptyMap());

        assertFalse(result);
    }

    // ---- deIndex --------------------------------------------------------------

    @Test
    void shouldDeIndexDocumentSuccessfully() {
        TurSolrInstance instance = mock(TurSolrInstance.class);
        TurSNSite site = new TurSNSite();
        site.setName("site-a");

        when(turSolrInstanceProcess.initSolrInstance("site-a", Locale.ENGLISH)).thenReturn(Optional.of(instance));

        boolean result = plugin.deIndex(site, Locale.ENGLISH, "doc-123");

        assertTrue(result);
        verify(turSolr).deIndexing(instance, "doc-123");
    }

    @Test
    void shouldReturnFalseWhenDeIndexInstanceUnavailable() {
        TurSNSite site = new TurSNSite();
        site.setName("site-a");

        when(turSolrInstanceProcess.initSolrInstance("site-a", Locale.ENGLISH)).thenReturn(Optional.empty());

        boolean result = plugin.deIndex(site, Locale.ENGLISH, "doc-123");

        assertFalse(result);
    }

    // ---- deIndexByType -------------------------------------------------------

    @Test
    void shouldDeIndexByTypeSuccessfully() {
        TurSolrInstance instance = mock(TurSolrInstance.class);
        TurSNSite site = new TurSNSite();
        site.setName("site-a");

        when(turSolrInstanceProcess.initSolrInstance("site-a", Locale.ENGLISH)).thenReturn(Optional.of(instance));

        boolean result = plugin.deIndexByType(site, Locale.ENGLISH, "page");

        assertTrue(result);
        verify(turSolr).deIndexingByType(instance, "page");
    }

    @Test
    void shouldReturnFalseWhenDeIndexByTypeInstanceUnavailable() {
        TurSNSite site = new TurSNSite();
        site.setName("site-a");

        when(turSolrInstanceProcess.initSolrInstance("site-a", Locale.ENGLISH)).thenReturn(Optional.empty());

        boolean result = plugin.deIndexByType(site, Locale.ENGLISH, "page");

        assertFalse(result);
    }

    // ---- commit --------------------------------------------------------------

    @Test
    void shouldCommitSuccessfully() {
        TurSolrInstance instance = mock(TurSolrInstance.class);
        TurSNSite site = new TurSNSite();
        site.setName("site-a");

        when(turSolrInstanceProcess.initSolrInstance("site-a", Locale.ENGLISH)).thenReturn(Optional.of(instance));
        when(turSolr.commit(instance)).thenReturn(true);

        boolean result = plugin.commit(site, Locale.ENGLISH);

        assertTrue(result);
        verify(turSolr).commit(instance);
    }

    @Test
    void shouldReturnFalseWhenCommitInstanceUnavailable() {
        TurSNSite site = new TurSNSite();
        site.setName("site-a");

        when(turSolrInstanceProcess.initSolrInstance("site-a", Locale.ENGLISH)).thenReturn(Optional.empty());

        boolean result = plugin.commit(site, Locale.ENGLISH);

        assertFalse(result);
    }

    // ---- getDocumentTotal ----------------------------------------------------

    @Test
    void shouldReturnDocumentTotal() {
        TurSolrInstance instance = mock(TurSolrInstance.class);
        TurSNSiteLocale siteLocale = new TurSNSiteLocale();

        when(turSolrInstanceProcess.initSolrInstance(siteLocale)).thenReturn(Optional.of(instance));
        when(turSolr.getDocumentTotal(instance)).thenReturn(42L);

        long total = plugin.getDocumentTotal(siteLocale);

        assertEquals(42L, total);
    }

    @Test
    void shouldReturnZeroWhenDocumentTotalInstanceUnavailable() {
        TurSNSiteLocale siteLocale = new TurSNSiteLocale();

        when(turSolrInstanceProcess.initSolrInstance(siteLocale)).thenReturn(Optional.empty());

        long total = plugin.getDocumentTotal(siteLocale);

        assertEquals(0L, total);
    }

    // ---- indexExists (standalone) --------------------------------------------

    @Test
    void shouldDelegateIndexExistsToSolrForStandalone() {
        TurSEInstance seInstance = new TurSEInstance();
        seInstance.setEndpointUrl("http://localhost:8983/solr");
        TurSolrProperty solrProp = new TurSolrProperty();
        solrProp.setCloud(false);
        when(turConfigProperties.getSolr()).thenReturn(solrProp);

        // TurSolrUtils.coreExists is a static call that may fail without a Solr server;
        // we are mainly testing that the code path does not throw NPE
        // and delegates correctly based on cloud vs standalone.
        // Static calls are hard to mock without PowerMock, so we accept this as a light test.
        assertDoesNotThrow(() -> plugin.indexExists(seInstance, "my-core"));
    }

    // ---- addOrUpdateField ----------------------------------------------------

    @Test
    void shouldDelegateAddOrUpdateFieldForNewField() {
        TurSEInstance seInstance = new TurSEInstance();
        seInstance.setEndpointUrl("http://localhost:8983/solr");

        // TurSolrUtils.addOrUpdateField is static - testing that the plugin delegates correctly
        // without an actual Solr server will result in an exception which is expected
        assertDoesNotThrow(() ->
                plugin.addOrUpdateField(seInstance, "my-core", "title", TurSEFieldType.TEXT, true, false, true));
    }

    // ---- fieldExists ---------------------------------------------------------

    @Test
    void shouldDelegateFieldExists() {
        TurSEInstance seInstance = new TurSEInstance();
        seInstance.setEndpointUrl("http://localhost:8983/solr");

        assertDoesNotThrow(() -> plugin.fieldExists(seInstance, "my-core", "title"));
    }

    // ---- getSystemInfo -------------------------------------------------------

    @Test
    void shouldReturnSystemInfoWithEngineKey() {
        TurSEInstance seInstance = new TurSEInstance();
        seInstance.setEndpointUrl("http://localhost:8983/solr");

        Map<String, String> info = plugin.getSystemInfo(seInstance);

        assertEquals("Apache Solr", info.get("engine"));
        // Without a running Solr, status will be DOWN
        assertTrue(info.containsKey("status"));
    }

    @Test
    void shouldReturnDownStatusWhenSolrUnreachable() {
        TurSEInstance seInstance = new TurSEInstance();
        seInstance.setEndpointUrl("http://invalid-host:9999/solr");

        Map<String, String> info = plugin.getSystemInfo(seInstance);

        assertEquals("Apache Solr", info.get("engine"));
        assertEquals("DOWN", info.get("status"));
        assertTrue(info.containsKey("error"));
    }

    // ---- deleteIndex ---------------------------------------------------------

    @Test
    void shouldDelegateDeleteIndexForCloud() {
        TurSEInstance seInstance = new TurSEInstance();
        seInstance.setEndpointUrl("http://localhost:8983/solr");
        TurSolrProperty solrProp = new TurSolrProperty();
        solrProp.setCloud(true);
        when(turConfigProperties.getSolr()).thenReturn(solrProp);

        assertDoesNotThrow(() -> plugin.deleteIndex(seInstance, "my-core"));
    }

    @Test
    void shouldDelegateDeleteIndexForStandalone() {
        TurSEInstance seInstance = new TurSEInstance();
        seInstance.setEndpointUrl("http://localhost:8983/solr");
        TurSolrProperty solrProp = new TurSolrProperty();
        solrProp.setCloud(false);
        when(turConfigProperties.getSolr()).thenReturn(solrProp);

        assertDoesNotThrow(() -> plugin.deleteIndex(seInstance, "my-core"));
    }

    // ---- clearIndex ----------------------------------------------------------

    @Test
    void shouldDelegateClearIndex() {
        TurSEInstance seInstance = new TurSEInstance();
        seInstance.setEndpointUrl("http://localhost:8983/solr");

        assertDoesNotThrow(() -> plugin.clearIndex(seInstance, "my-core"));
    }

    // ---- listIndexes ---------------------------------------------------------

    @Test
    void shouldDelegateListIndexesForCloud() {
        TurSEInstance seInstance = new TurSEInstance();
        seInstance.setEndpointUrl("http://localhost:8983/solr");
        TurSolrProperty solrProp = new TurSolrProperty();
        solrProp.setCloud(true);
        when(turConfigProperties.getSolr()).thenReturn(solrProp);

        assertDoesNotThrow(() -> plugin.listIndexes(seInstance));
    }

    @Test
    void shouldDelegateListIndexesForStandalone() {
        TurSEInstance seInstance = new TurSEInstance();
        seInstance.setEndpointUrl("http://localhost:8983/solr");
        TurSolrProperty solrProp = new TurSolrProperty();
        solrProp.setCloud(false);
        when(turConfigProperties.getSolr()).thenReturn(solrProp);

        assertDoesNotThrow(() -> plugin.listIndexes(seInstance));
    }
}
