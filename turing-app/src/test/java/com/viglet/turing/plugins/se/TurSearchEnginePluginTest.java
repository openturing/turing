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

package com.viglet.turing.plugins.se;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.viglet.turing.commons.se.TurSEParameters;
import com.viglet.turing.commons.se.field.TurSEFieldType;
import com.viglet.turing.commons.sn.TurSNConfig;
import com.viglet.turing.commons.sn.bean.TurSNSearchParams;
import com.viglet.turing.commons.sn.search.TurSNSiteSearchContext;
import com.viglet.turing.persistence.model.se.TurSEInstance;
import com.viglet.turing.persistence.model.sn.TurSNSite;
import com.viglet.turing.persistence.model.sn.locale.TurSNSiteLocale;
import com.viglet.turing.se.result.TurSEResults;
import com.viglet.turing.solr.bean.TurSECoreInfo;

/**
 * Unit tests for TurSearchEnginePlugin interface.
 *
 * @author Alexandre Oliveira
 * @since 2025.1.10
 */
class TurSearchEnginePluginTest {

    private TurSearchEnginePlugin plugin;
    private TurSNSiteSearchContext context;

    @BeforeEach
    void setUp() {
        plugin = new TestSearchEnginePlugin();

        TurSNConfig config = new TurSNConfig();
        TurSNSearchParams searchParams = new TurSNSearchParams();
        TurSEParameters parameters = new TurSEParameters(searchParams);
        context = new TurSNSiteSearchContext(
                "testSite",
                config,
                parameters,
                Locale.ENGLISH,
                URI.create("http://localhost:8080"));
    }

    @Test
    void testRetrieveSearchResults() {
        Optional<TurSEResults> results = plugin.retrieveSearchResults(context);

        assertThat(results).isPresent();
        assertThat(results.get().getQueryString()).isEqualTo("test query");
    }

    @Test
    void testRetrieveFacetResults() {
        Optional<TurSEResults> results = plugin.retrieveFacetResults(context, "category");

        assertThat(results).isPresent();
        assertThat(results.get().getQueryString()).isEqualTo("facet:category");
    }

    @Test
    void testGetPluginType() {
        String pluginType = plugin.getPluginType();

        assertThat(pluginType).isEqualTo("test");
    }

    @Test
    void testRetrieveFacetResultsWithNullFacetName() {
        TestSearchEnginePlugin testPlugin = new TestSearchEnginePlugin();

        Optional<TurSEResults> results = testPlugin.retrieveFacetResults(context, null);

        assertThat(results).isPresent();
        assertThat(results.get().getQueryString()).isEqualTo("facet:null");
    }

    @Test
    void testRetrieveFacetResultsWithEmptyFacetName() {
        Optional<TurSEResults> results = plugin.retrieveFacetResults(context, "");

        assertThat(results).isPresent();
        assertThat(results.get().getQueryString()).isEqualTo("facet:");
    }

    @Test
    void testPluginReturnsEmptyOptional() {
        TurSearchEnginePlugin emptyPlugin = new EmptySearchEnginePlugin();

        Optional<TurSEResults> searchResults = emptyPlugin.retrieveSearchResults(context);
        Optional<TurSEResults> facetResults = emptyPlugin.retrieveFacetResults(context, "test");

        assertThat(searchResults).isEmpty();
        assertThat(facetResults).isEmpty();
    }

    @Test
    void testDifferentPluginTypes() {
        TurSearchEnginePlugin solrPlugin = new SolrLikePlugin();
        TurSearchEnginePlugin esPlugin = new ElasticsearchLikePlugin();

        assertThat(solrPlugin.getPluginType()).isEqualTo("solr");
        assertThat(esPlugin.getPluginType()).isEqualTo("elasticsearch");
    }

    @Test
    void testSearchContextSiteName() {
        assertThat(context.getSiteName()).isEqualTo("testSite");
    }

    @Test
    void testSearchContextLocale() {
        assertThat(context.getLocale()).isEqualTo(Locale.ENGLISH);
    }

    private static class TestSearchEnginePlugin implements TurSearchEnginePlugin {
        @Override
        public Optional<TurSEResults> retrieveSearchResults(TurSNSiteSearchContext context) {
            TurSEResults results = TurSEResults.builder()
                    .queryString("test query")
                    .numFound(10)
                    .start(0)
                    .limit(10)
                    .pageCount(1)
                    .currentPage(1)
                    .qTime(100)
                    .elapsedTime(100L)
                    .build();
            return Optional.of(results);
        }

        @Override
        public Optional<TurSEResults> retrieveFacetResults(TurSNSiteSearchContext context, String facetName) {
            TurSEResults results = TurSEResults.builder()
                    .queryString("facet:" + facetName)
                    .numFound(5)
                    .start(0)
                    .limit(10)
                    .pageCount(1)
                    .currentPage(1)
                    .qTime(50)
                    .elapsedTime(50L)
                    .build();
            return Optional.of(results);
        }

        @Override
        public String getPluginType() {
            return "test";
        }

        // Test stub methods - not implemented for unit testing purposes
        @Override
        public void createIndex(TurSEInstance s, TurSNSiteLocale l, String n, Map<String, TurSEFieldType> f) {
            // Stub: not needed for this unit test
        }

        @Override
        public void deleteIndex(TurSEInstance s, String n) {
            // Stub: not needed for this unit test
        }

        @Override
        public void clearIndex(TurSEInstance s, String n) {
            // Stub: not needed for this unit test
        }

        @Override
        public boolean indexExists(TurSEInstance s, String n) {
            return false;
        }

        @Override
        public List<TurSECoreInfo> listIndexes(TurSEInstance s) {
            return Collections.emptyList();
        }

        @Override
        public void addOrUpdateField(TurSEInstance s, String i, String f, TurSEFieldType t, boolean st, boolean m,
                boolean n) {
            // Stub: not needed for this unit test
        }

        @Override
        public void deleteField(TurSEInstance s, String i, String f, TurSEFieldType t) {
            // Stub: not needed for this unit test
        }

        @Override
        public boolean fieldExists(TurSEInstance s, String i, String f) {
            return false;
        }

        @Override
        public boolean indexDocument(TurSNSite site, Locale l, Map<String, Object> a) {
            return false;
        }

        @Override
        public boolean deIndex(TurSNSite site, Locale l, String id) {
            return false;
        }

        @Override
        public boolean deIndexByType(TurSNSite site, Locale l, String t) {
            return false;
        }

        @Override
        public boolean commit(TurSNSite site, Locale l) {
            return false;
        }

        @Override
        public long getDocumentTotal(TurSNSiteLocale locale) {
            return 0L;
        }
    }

    private static class EmptySearchEnginePlugin implements TurSearchEnginePlugin {
        @Override
        public Optional<TurSEResults> retrieveSearchResults(TurSNSiteSearchContext context) {
            return Optional.empty();
        }

        @Override
        public Optional<TurSEResults> retrieveFacetResults(TurSNSiteSearchContext context, String facetName) {
            return Optional.empty();
        }

        @Override
        public String getPluginType() {
            return "empty";
        }

        // Test stub methods - not implemented for unit testing purposes
        @Override
        public void createIndex(TurSEInstance s, TurSNSiteLocale l, String n, Map<String, TurSEFieldType> f) {
            // Stub: not needed for this unit test
        }

        @Override
        public void deleteIndex(TurSEInstance s, String n) {
            // Stub: not needed for this unit test
        }

        @Override
        public void clearIndex(TurSEInstance s, String n) {
            // Stub: not needed for this unit test
        }

        @Override
        public boolean indexExists(TurSEInstance s, String n) {
            return false;
        }

        @Override
        public List<TurSECoreInfo> listIndexes(TurSEInstance s) {
            return Collections.emptyList();
        }

        @Override
        public void addOrUpdateField(TurSEInstance s, String i, String f, TurSEFieldType t, boolean st, boolean m,
                boolean n) {
            // Stub: not needed for this unit test
        }

        @Override
        public void deleteField(TurSEInstance s, String i, String f, TurSEFieldType t) {
            // Stub: not needed for this unit test
        }

        @Override
        public boolean fieldExists(TurSEInstance s, String i, String f) {
            return false;
        }

        @Override
        public boolean indexDocument(TurSNSite site, Locale l, Map<String, Object> a) {
            return false;
        }

        @Override
        public boolean deIndex(TurSNSite site, Locale l, String id) {
            return false;
        }

        @Override
        public boolean deIndexByType(TurSNSite site, Locale l, String t) {
            return false;
        }

        @Override
        public boolean commit(TurSNSite site, Locale l) {
            return false;
        }

        @Override
        public long getDocumentTotal(TurSNSiteLocale locale) {
            return 0L;
        }
    }

    private static class SolrLikePlugin implements TurSearchEnginePlugin {
        @Override
        public Optional<TurSEResults> retrieveSearchResults(TurSNSiteSearchContext context) {
            return Optional.empty();
        }

        @Override
        public Optional<TurSEResults> retrieveFacetResults(TurSNSiteSearchContext context, String facetName) {
            return Optional.empty();
        }

        @Override
        public String getPluginType() {
            return "solr";
        }

        // Test stub methods - not implemented for unit testing purposes
        @Override
        public void createIndex(TurSEInstance s, TurSNSiteLocale l, String n, Map<String, TurSEFieldType> f) {
            // Not implemented: test stub only tests search and facet retrieval methods
        }

        @Override
        public void deleteIndex(TurSEInstance s, String n) {
            // Not implemented: test stub only tests search and facet retrieval methods
        }

        @Override
        public void clearIndex(TurSEInstance s, String n) {
            // Not implemented: test stub only tests search and facet retrieval methods
        }

        @Override
        public boolean indexExists(TurSEInstance s, String n) {
            // Not implemented: test stub only tests search and facet retrieval methods
            return false;
        }

        @Override
        public List<TurSECoreInfo> listIndexes(TurSEInstance s) {
            // Not implemented: test stub only tests search and facet retrieval methods
            return Collections.emptyList();
        }

        @Override
        public void addOrUpdateField(TurSEInstance s, String i, String f, TurSEFieldType t, boolean st, boolean m,
                boolean n) {
            // Not implemented: test stub only tests search and facet retrieval methods
        }

        @Override
        public void deleteField(TurSEInstance s, String i, String f, TurSEFieldType t) {
            // Not implemented: test stub only tests search and facet retrieval methods
        }

        @Override
        public boolean fieldExists(TurSEInstance s, String i, String f) {
            // Not implemented: test stub only tests search and facet retrieval methods
            return false;
        }

        @Override
        public boolean indexDocument(TurSNSite site, Locale l, Map<String, Object> a) {
            // Not implemented: test stub only tests search and facet retrieval methods
            return false;
        }

        @Override
        public boolean deIndex(TurSNSite site, Locale l, String id) {
            // Not implemented: test stub only tests search and facet retrieval methods
            return false;
        }

        @Override
        public boolean deIndexByType(TurSNSite site, Locale l, String t) {
            // Not implemented: test stub only tests search and facet retrieval methods
            return false;
        }

        @Override
        public boolean commit(TurSNSite site, Locale l) {
            // Not implemented: test stub only tests search and facet retrieval methods
            return false;
        }

        @Override
        public long getDocumentTotal(TurSNSiteLocale locale) {
            // Not implemented: test stub only tests search and facet retrieval methods
            return 0L;
        }
    }

    private static class ElasticsearchLikePlugin implements TurSearchEnginePlugin {
        @Override
        public Optional<TurSEResults> retrieveSearchResults(TurSNSiteSearchContext context) {
            return Optional.empty();
        }

        @Override
        public Optional<TurSEResults> retrieveFacetResults(TurSNSiteSearchContext context, String facetName) {
            return Optional.empty();
        }

        @Override
        public String getPluginType() {
            return "elasticsearch";
        }

        // Test stub methods - not implemented for unit testing purposes
        @Override
        public void createIndex(TurSEInstance s, TurSNSiteLocale l, String n, Map<String, TurSEFieldType> f) {
            // Not implemented: test stub only tests search and facet retrieval methods
        }

        @Override
        public void deleteIndex(TurSEInstance s, String n) {
            // Not implemented: test stub only tests search and facet retrieval methods
        }

        @Override
        public void clearIndex(TurSEInstance s, String n) {
            // Not implemented: test stub only tests search and facet retrieval methods
        }

        @Override
        public boolean indexExists(TurSEInstance s, String n) {
            // Not implemented: test stub only tests search and facet retrieval methods
            return false;
        }

        @Override
        public List<TurSECoreInfo> listIndexes(TurSEInstance s) {
            // Not implemented: test stub only tests search and facet retrieval methods
            return Collections.emptyList();
        }

        @Override
        public void addOrUpdateField(TurSEInstance s, String i, String f, TurSEFieldType t, boolean st, boolean m,
                boolean n) {
            // Not implemented: test stub only tests search and facet retrieval methods
        }

        @Override
        public void deleteField(TurSEInstance s, String i, String f, TurSEFieldType t) {
            // Not implemented: test stub only tests search and facet retrieval methods
        }

        @Override
        public boolean fieldExists(TurSEInstance s, String i, String f) {
            return false;
        }

        @Override
        public boolean indexDocument(TurSNSite site, Locale l, Map<String, Object> a) {
            return false;
        }

        @Override
        public boolean deIndex(TurSNSite site, Locale l, String id) {
            return false;
        }

        @Override
        public boolean deIndexByType(TurSNSite site, Locale l, String t) {
            return false;
        }

        @Override
        public boolean commit(TurSNSite site, Locale l) {
            return false;
        }

        @Override
        public long getDocumentTotal(TurSNSiteLocale locale) {
            return 0L;
        }
    }
}
