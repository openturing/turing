/*
 * Copyright (C) 2016-2022 the original author or authors.
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

import com.viglet.turing.plugins.se.elasticsearch.TurElasticsearchSearchEnginePlugin;
import com.viglet.turing.plugins.se.solr.TurSolrSearchEnginePlugin;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Test for TurSearchEnginePluginFactory
 *
 * @author Alexandre Oliveira
 * @since 2025.4.4
 */
class TurSearchEnginePluginFactoryTest {

    @Test
    void testGetDefaultPlugin_Solr() {
        TurSolrSearchEnginePlugin solrPlugin = new TurSolrSearchEnginePlugin(null, null);
        TurElasticsearchSearchEnginePlugin elasticsearchPlugin = new TurElasticsearchSearchEnginePlugin(null, null);
        
        TurSearchEnginePluginFactory factory = new TurSearchEnginePluginFactory(
                Arrays.asList(solrPlugin, elasticsearchPlugin),
                "solr"
        );

        TurSearchEnginePlugin plugin = factory.getDefaultPlugin();
        assertNotNull(plugin);
        assertEquals("solr", plugin.getPluginType());
        assertSame(solrPlugin, plugin);
    }

    @Test
    void testGetDefaultPlugin_Elasticsearch() {
        TurSolrSearchEnginePlugin solrPlugin = new TurSolrSearchEnginePlugin(null, null);
        TurElasticsearchSearchEnginePlugin elasticsearchPlugin = new TurElasticsearchSearchEnginePlugin(null, null);
        
        TurSearchEnginePluginFactory factory = new TurSearchEnginePluginFactory(
                Arrays.asList(solrPlugin, elasticsearchPlugin),
                "elasticsearch"
        );

        TurSearchEnginePlugin plugin = factory.getDefaultPlugin();
        assertNotNull(plugin);
        assertEquals("elasticsearch", plugin.getPluginType());
        assertSame(elasticsearchPlugin, plugin);
    }

    @Test
    void testGetPlugin_SpecificType() {
        TurSolrSearchEnginePlugin solrPlugin = new TurSolrSearchEnginePlugin(null, null);
        TurElasticsearchSearchEnginePlugin elasticsearchPlugin = new TurElasticsearchSearchEnginePlugin(null, null);
        
        TurSearchEnginePluginFactory factory = new TurSearchEnginePluginFactory(
                Arrays.asList(solrPlugin, elasticsearchPlugin),
                "solr"
        );

        TurSearchEnginePlugin solr = factory.getPlugin("solr");
        assertNotNull(solr);
        assertEquals("solr", solr.getPluginType());

        TurSearchEnginePlugin elasticsearch = factory.getPlugin("elasticsearch");
        assertNotNull(elasticsearch);
        assertEquals("elasticsearch", elasticsearch.getPluginType());
    }

    @Test
    void testGetPlugin_InvalidType() {
        TurSolrSearchEnginePlugin solrPlugin = new TurSolrSearchEnginePlugin(null, null);
        
        TurSearchEnginePluginFactory factory = new TurSearchEnginePluginFactory(
                Arrays.asList(solrPlugin),
                "solr"
        );

        assertThrows(IllegalStateException.class, () -> factory.getPlugin("invalid"));
    }

    @Test
    void testGetDefaultEngineType() {
        TurSolrSearchEnginePlugin solrPlugin = new TurSolrSearchEnginePlugin(null, null);
        
        TurSearchEnginePluginFactory factory = new TurSearchEnginePluginFactory(
                Arrays.asList(solrPlugin),
                "solr"
        );

        assertEquals("solr", factory.getDefaultEngineType());
    }

    @Test
    void testGetPlugin_CaseInsensitive() {
        TurSolrSearchEnginePlugin solrPlugin = new TurSolrSearchEnginePlugin(null, null);
        TurElasticsearchSearchEnginePlugin elasticsearchPlugin = new TurElasticsearchSearchEnginePlugin(null, null);
        
        TurSearchEnginePluginFactory factory = new TurSearchEnginePluginFactory(
                Arrays.asList(solrPlugin, elasticsearchPlugin),
                "solr"
        );

        TurSearchEnginePlugin plugin1 = factory.getPlugin("SOLR");
        TurSearchEnginePlugin plugin2 = factory.getPlugin("Solr");
        TurSearchEnginePlugin plugin3 = factory.getPlugin("solr");

        assertEquals(solrPlugin, plugin1);
        assertEquals(solrPlugin, plugin2);
        assertEquals(solrPlugin, plugin3);
    }

    @Test
    void testGetDefaultPlugin_InvalidDefaultThrowsException() {
        TurSolrSearchEnginePlugin solrPlugin = new TurSolrSearchEnginePlugin(null, null);
        
        TurSearchEnginePluginFactory factory = new TurSearchEnginePluginFactory(
                Arrays.asList(solrPlugin),
                "nonexistent"
        );

        assertThrows(IllegalStateException.class, factory::getDefaultPlugin);
    }

    @Test
    void testFactoryWithEmptyPluginList() {
        TurSearchEnginePluginFactory factory = new TurSearchEnginePluginFactory(
                Collections.emptyList(),
                "solr"
        );

        assertThrows(IllegalStateException.class, factory::getDefaultPlugin);
    }

    @Test
    void testMultipleCallsReturnSameInstance() {
        TurSolrSearchEnginePlugin solrPlugin = new TurSolrSearchEnginePlugin(null, null);
        TurElasticsearchSearchEnginePlugin elasticsearchPlugin = new TurElasticsearchSearchEnginePlugin(null, null);
        
        TurSearchEnginePluginFactory factory = new TurSearchEnginePluginFactory(
                Arrays.asList(solrPlugin, elasticsearchPlugin),
                "solr"
        );

        TurSearchEnginePlugin plugin1 = factory.getPlugin("solr");
        TurSearchEnginePlugin plugin2 = factory.getPlugin("solr");

        assertSame(plugin1, plugin2);
    }

    @Test
    void testExceptionMessageContainsAvailablePlugins() {
        TurSolrSearchEnginePlugin solrPlugin = new TurSolrSearchEnginePlugin(null, null);
        TurElasticsearchSearchEnginePlugin elasticsearchPlugin = new TurElasticsearchSearchEnginePlugin(null, null);
        
        TurSearchEnginePluginFactory factory = new TurSearchEnginePluginFactory(
                Arrays.asList(solrPlugin, elasticsearchPlugin),
                "solr"
        );

        assertThatThrownBy(() -> factory.getPlugin("unknown"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Search engine plugin 'unknown' not found")
            .hasMessageContaining("Available plugins:");
    }

    @Test
    void testFactoryWithSinglePlugin() {
        TurSolrSearchEnginePlugin solrPlugin = new TurSolrSearchEnginePlugin(null, null);
        
        TurSearchEnginePluginFactory factory = new TurSearchEnginePluginFactory(
                Collections.singletonList(solrPlugin),
                "solr"
        );

        TurSearchEnginePlugin plugin = factory.getDefaultPlugin();

        assertNotNull(plugin);
        assertEquals("solr", plugin.getPluginType());
    }

    @Test
    void testGetPlugin_EmptyStringThrowsException() {
        TurSolrSearchEnginePlugin solrPlugin = new TurSolrSearchEnginePlugin(null, null);
        
        TurSearchEnginePluginFactory factory = new TurSearchEnginePluginFactory(
                Arrays.asList(solrPlugin),
                "solr"
        );

        assertThrows(IllegalStateException.class, () -> factory.getPlugin(""));
    }

    @Test
    void testElasticsearchAsDefault() {
        TurSolrSearchEnginePlugin solrPlugin = new TurSolrSearchEnginePlugin(null, null);
        TurElasticsearchSearchEnginePlugin elasticsearchPlugin = new TurElasticsearchSearchEnginePlugin(null, null);
        
        TurSearchEnginePluginFactory factory = new TurSearchEnginePluginFactory(
                Arrays.asList(solrPlugin, elasticsearchPlugin),
                "elasticsearch"
        );

        TurSearchEnginePlugin defaultPlugin = factory.getDefaultPlugin();

        assertEquals(elasticsearchPlugin, defaultPlugin);
        assertEquals("elasticsearch", factory.getDefaultEngineType());
    }
}
