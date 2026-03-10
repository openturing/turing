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
package com.viglet.turing.plugins.se;

import com.viglet.turing.persistence.model.se.TurSEInstance;
import com.viglet.turing.persistence.model.se.TurSEVendor;
import com.viglet.turing.persistence.model.sn.TurSNSite;
import com.viglet.turing.plugins.se.elasticsearch.TurElasticsearchSearchEnginePlugin;
import com.viglet.turing.plugins.se.solr.TurSolrSearchEnginePlugin;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Test for TurSearchEnginePluginFactory
 *
 * @author Alexandre Oliveira
 * @since 2025.4.4
 */
class TurSearchEnginePluginFactoryTest {

    private TurSNSite siteWithPlugin(String pluginType) {
        TurSEVendor vendor = new TurSEVendor();
        vendor.setId(pluginType.toUpperCase());
        vendor.setPlugin(pluginType);
        TurSEInstance instance = new TurSEInstance();
        instance.setTurSEVendor(vendor);
        TurSNSite site = new TurSNSite();
        site.setTurSEInstance(instance);
        return site;
    }

    private TurSolrSearchEnginePlugin mockSolrPlugin() {
        TurSolrSearchEnginePlugin plugin = mock(TurSolrSearchEnginePlugin.class);
        when(plugin.getPluginType()).thenReturn("solr");
        return plugin;
    }

    private TurElasticsearchSearchEnginePlugin mockElasticsearchPlugin() {
        TurElasticsearchSearchEnginePlugin plugin = mock(TurElasticsearchSearchEnginePlugin.class);
        when(plugin.getPluginType()).thenReturn("elasticsearch");
        return plugin;
    }

    @Test
    void testGetPluginForSite_Solr() {
        TurSolrSearchEnginePlugin solrPlugin = mockSolrPlugin();
        TurSearchEnginePluginFactory factory = new TurSearchEnginePluginFactory(
                Collections.singletonList(solrPlugin));

        TurSearchEnginePlugin plugin = factory.getPluginForSite(siteWithPlugin("solr"));
        assertNotNull(plugin);
        assertEquals("solr", plugin.getPluginType());
        assertSame(solrPlugin, plugin);
    }

    @Test
    void testGetPluginForSite_Elasticsearch() {
        TurSolrSearchEnginePlugin solrPlugin = mockSolrPlugin();
        TurElasticsearchSearchEnginePlugin elasticsearchPlugin = mockElasticsearchPlugin();
        TurSearchEnginePluginFactory factory = new TurSearchEnginePluginFactory(
                Arrays.asList(solrPlugin, elasticsearchPlugin));

        TurSearchEnginePlugin plugin = factory.getPluginForSite(siteWithPlugin("elasticsearch"));
        assertNotNull(plugin);
        assertEquals("elasticsearch", plugin.getPluginType());
        assertSame(elasticsearchPlugin, plugin);
    }

    @Test
    void testGetPlugin_SpecificType() {
        TurSolrSearchEnginePlugin solrPlugin = mockSolrPlugin();
        TurElasticsearchSearchEnginePlugin elasticsearchPlugin = mockElasticsearchPlugin();
        TurSearchEnginePluginFactory factory = new TurSearchEnginePluginFactory(
                Arrays.asList(solrPlugin, elasticsearchPlugin));

        assertEquals("solr", factory.getPlugin("solr").getPluginType());
        assertEquals("elasticsearch", factory.getPlugin("elasticsearch").getPluginType());
    }

    @Test
    void testGetPlugin_InvalidFallsBackToSolr() {
        TurSolrSearchEnginePlugin solrPlugin = mockSolrPlugin();
        TurSearchEnginePluginFactory factory = new TurSearchEnginePluginFactory(
                Collections.singletonList(solrPlugin));

        // Falls back to solr when unknown type is requested
        TurSearchEnginePlugin plugin = factory.getPlugin("invalid");
        assertSame(solrPlugin, plugin);
    }

    @Test
    void testGetPlugin_InvalidWithNoSolrFallbackThrows() {
        TurElasticsearchSearchEnginePlugin elasticsearchPlugin = mockElasticsearchPlugin();
        TurSearchEnginePluginFactory factory = new TurSearchEnginePluginFactory(
                Collections.singletonList(elasticsearchPlugin));

        assertThrows(IllegalStateException.class, () -> factory.getPlugin("invalid"));
    }

    @Test
    void testGetPlugin_CaseInsensitive() {
        TurSolrSearchEnginePlugin solrPlugin = mockSolrPlugin();
        TurSearchEnginePluginFactory factory = new TurSearchEnginePluginFactory(
                Collections.singletonList(solrPlugin));

        assertSame(solrPlugin, factory.getPlugin("SOLR"));
        assertSame(solrPlugin, factory.getPlugin("Solr"));
        assertSame(solrPlugin, factory.getPlugin("solr"));
    }

    @Test
    void testMultipleCallsReturnSameInstance() {
        TurSolrSearchEnginePlugin solrPlugin = mockSolrPlugin();
        TurSearchEnginePluginFactory factory = new TurSearchEnginePluginFactory(
                Collections.singletonList(solrPlugin));

        assertSame(factory.getPlugin("solr"), factory.getPlugin("solr"));
    }

    @Test
    void testGetPluginForSite_NullSiteFallsBackToSolr() {
        TurSolrSearchEnginePlugin solrPlugin = mockSolrPlugin();
        TurSearchEnginePluginFactory factory = new TurSearchEnginePluginFactory(
                Collections.singletonList(solrPlugin));

        TurSearchEnginePlugin plugin = factory.getPluginForSite(null);
        assertSame(solrPlugin, plugin);
    }

    @Test
    void testGetPluginForSite_VendorByIdWhenPluginBlank() {
        TurSolrSearchEnginePlugin solrPlugin = mockSolrPlugin();
        TurSearchEnginePluginFactory factory = new TurSearchEnginePluginFactory(
                Collections.singletonList(solrPlugin));

        TurSEVendor vendor = new TurSEVendor();
        vendor.setId("SOLR");
        vendor.setPlugin(""); // blank plugin — should fall back to id
        TurSEInstance instance = new TurSEInstance();
        instance.setTurSEVendor(vendor);
        TurSNSite site = new TurSNSite();
        site.setTurSEInstance(instance);

        TurSearchEnginePlugin plugin = factory.getPluginForSite(site);
        assertSame(solrPlugin, plugin);
    }
}
