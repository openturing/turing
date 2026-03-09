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

package com.viglet.turing.sn;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.PageRequest;

import com.viglet.turing.commons.se.TurSEParameters;
import com.viglet.turing.commons.se.result.spellcheck.TurSESpellCheckResult;
import com.viglet.turing.commons.sn.TurSNConfig;
import com.viglet.turing.commons.sn.bean.TurSNSearchParams;
import com.viglet.turing.commons.sn.bean.TurSNSitePostParamsBean;
import com.viglet.turing.commons.sn.search.TurSNSiteSearchContext;
import com.viglet.turing.persistence.model.sn.TurSNSite;
import com.viglet.turing.persistence.model.sn.field.TurSNSiteFacetFieldEnum;
import com.viglet.turing.persistence.model.sn.field.TurSNSiteFieldExt;
import com.viglet.turing.persistence.model.sn.field.TurSNSiteFieldExtFacet;
import com.viglet.turing.persistence.model.sn.locale.TurSNSiteLocale;
import com.viglet.turing.persistence.model.sn.spotlight.TurSNSiteSpotlightDocument;
import com.viglet.turing.persistence.repository.sn.TurSNSiteRepository;
import com.viglet.turing.persistence.repository.sn.field.TurSNSiteFieldExtFacetRepository;
import com.viglet.turing.persistence.repository.sn.field.TurSNSiteFieldExtRepository;
import com.viglet.turing.persistence.repository.sn.locale.TurSNSiteLocaleRepository;
import com.viglet.turing.persistence.repository.sn.metric.TurSNSiteMetricAccessRepository;
import com.viglet.turing.persistence.repository.sn.metric.TurSNSiteMetricAccessTerm;
import com.viglet.turing.plugins.se.TurSearchEnginePlugin;
import com.viglet.turing.plugins.se.TurSearchEnginePluginFactory;
import com.viglet.turing.se.facet.TurSEFacetResult;
import com.viglet.turing.se.facet.TurSEFacetResultAttr;
import com.viglet.turing.se.result.TurSEGroup;
import com.viglet.turing.se.result.TurSEResult;
import com.viglet.turing.se.result.TurSEResults;
import com.viglet.turing.sn.facet.TurSNFacetDefinitionFactory;
import com.viglet.turing.sn.spotlight.TurSNSpotlightProcess;
import com.viglet.turing.solr.TurSolrInstance;
import com.viglet.turing.solr.TurSolrInstanceProcess;
import com.viglet.turing.solr.TurSolrQueryBuilder;

/**
 * Unit tests for TurSNSearchProcess.
 *
 * @author Alexandre Oliveira
 * @since 2026.1.10
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TurSNSearchProcessTest {

        @Mock
        private TurSNSiteFieldExtRepository turSNSiteFieldExtRepository;

        @Mock
        private TurSNSiteFieldExtFacetRepository turSNSiteFieldExtFacetRepository;

        @Mock
        private TurSNSiteRepository turSNSiteRepository;

        @Mock
        private TurSNSiteLocaleRepository turSNSiteLocaleRepository;

        @Mock
        private TurSolrInstanceProcess turSolrInstanceProcess;

        @Mock
        private TurSNSpotlightProcess turSNSpotlightProcess;

        @Mock
        private TurSNSiteMetricAccessRepository turSNSiteMetricAccessRepository;

        @Mock
        private TurSearchEnginePluginFactory searchEnginePluginFactory;

        @Mock
        private TurSolrQueryBuilder turSolrQueryBuilder;

        private final TurSNFacetDefinitionFactory turSNFacetDefinitionFactory = new TurSNFacetDefinitionFactory();

        private TurSNSearchProcess process(boolean metricsEnabled) {
                return new TurSNSearchProcess(turSNSiteFieldExtRepository,
                                turSNSiteFieldExtFacetRepository, turSNSiteRepository,
                                turSNSiteLocaleRepository,
                                turSolrInstanceProcess, turSNSpotlightProcess,
                                turSNSiteMetricAccessRepository,
                                metricsEnabled, searchEnginePluginFactory, turSolrQueryBuilder,
                                turSNFacetDefinitionFactory);
        }

        private TurSNSiteSearchContext context(String query) {
                TurSNSearchParams searchParams = new TurSNSearchParams();
                searchParams.setQ(query);
                searchParams.setLocale(Locale.US);
                return new TurSNSiteSearchContext("site", new TurSNConfig(),
                                new TurSEParameters(searchParams, new TurSNSitePostParamsBean()),
                                Locale.US,
                                URI.create("http://localhost/search?q=" + query));
        }

        @Test
        void testGetSNSiteReturnsOptional() {
                TurSNSite site = new TurSNSite();
                site.setName("site");
                when(turSNSiteRepository.findByName("site")).thenReturn(Optional.of(site));

                assertThat(process(false).getSNSite("site")).contains(site);
        }

        @Test
        void testGetSNSiteReturnsEmptyWhenMissing() {
                when(turSNSiteRepository.findByName("site")).thenReturn(Optional.empty());

                assertThat(process(false).getSNSite("site")).isEmpty();
        }

        @Test
        void testExistsByTurSNSiteAndLanguage() {
                TurSNSite site = new TurSNSite();
                when(turSNSiteRepository.findByName("site")).thenReturn(Optional.of(site));
                when(turSNSiteLocaleRepository.existsByTurSNSiteAndLanguage(site, Locale.US))
                                .thenReturn(true);

                assertThat(process(false).existsByTurSNSiteAndLanguage("site", Locale.US)).isTrue();
        }

        @Test
        void testExistsByTurSNSiteAndLanguageReturnsFalseWhenSiteMissing() {
                when(turSNSiteRepository.findByName("site")).thenReturn(Optional.empty());

                assertThat(process(false).existsByTurSNSiteAndLanguage("site", Locale.US)).isFalse();
        }

        @Test
        void testLatestSearchesReturnsTerms() {
                TurSNSite site = new TurSNSite();
                when(turSNSiteRepository.findByName("site")).thenReturn(Optional.of(site));
                TurSNSiteMetricAccessTerm term1 = new TurSNSiteMetricAccessTerm("one", Instant.now());
                TurSNSiteMetricAccessTerm term2 = new TurSNSiteMetricAccessTerm("two", Instant.now());
                when(turSNSiteMetricAccessRepository.findLatestSearches(eq(site), eq("en"), eq("user"),
                                any(PageRequest.class))).thenReturn(List.of(term1, term2));

                List<String> results = process(false).latestSearches("site", "en", "user", 10);

                assertThat(results).containsExactly("one", "two");
        }

        @Test
        void testLatestSearchesReturnsEmptyWhenSiteMissing() {
                when(turSNSiteRepository.findByName("site")).thenReturn(Optional.empty());

                assertThat(process(false).latestSearches("site", "en", "user", 10)).isEmpty();
        }

        @Test
        void testSearchReturnsEmptyWhenPluginReturnsEmpty() {
                TurSearchEnginePlugin plugin = org.mockito.Mockito.mock(TurSearchEnginePlugin.class);
                when(searchEnginePluginFactory.getDefaultPlugin()).thenReturn(plugin);
                when(plugin.retrieveSearchResults(any())).thenReturn(Optional.empty());

                assertThat(process(false).search(context("java")).getResults()).isNull();
        }

        @Test
        void testSearchReturnsEmptyWhenSolrInstanceMissing() {
                TurSearchEnginePlugin plugin = org.mockito.Mockito.mock(TurSearchEnginePlugin.class);
                when(searchEnginePluginFactory.getDefaultPlugin()).thenReturn(plugin);
                when(plugin.retrieveSearchResults(any())).thenReturn(Optional.of(TurSEResults.builder().build()));
                when(turSolrInstanceProcess.initSolrInstance("site", Locale.US)).thenReturn(Optional.empty());

                assertThat(process(false).search(context("java")).getWidget()).isNull();
        }

        @Test
        void testSearchListReturnsEmptyWhenPluginReturnsEmpty() {
                TurSearchEnginePlugin plugin = org.mockito.Mockito.mock(TurSearchEnginePlugin.class);
                when(searchEnginePluginFactory.getDefaultPlugin()).thenReturn(plugin);
                when(plugin.retrieveSearchResults(any())).thenReturn(Optional.empty());

                assertThat(process(false).searchList(context("java"))).isEmpty();
        }

        @Test
        void testSearchListReturnsEmptyWhenSolrInstanceMissing() {
                TurSearchEnginePlugin plugin = org.mockito.Mockito.mock(TurSearchEnginePlugin.class);
                when(searchEnginePluginFactory.getDefaultPlugin()).thenReturn(plugin);
                when(plugin.retrieveSearchResults(any())).thenReturn(Optional.of(TurSEResults.builder().build()));
                when(turSolrInstanceProcess.initSolrInstance("site", Locale.US)).thenReturn(Optional.empty());

                assertThat(process(false).searchList(context("java"))).isEmpty();
        }

        @Test
        void testRequestTargetingRulesFormatsValues() {
                List<String> result = process(true).requestTargetingRules(
                                List.of("segment:vip", "price:[10 TO 20]", "rawRule"));

                assertThat(result)
                                .contains("segment:\"vip\"")
                                .contains("rawRule")
                                .doesNotContain("price:[10 TO 20]");
        }

        @Test
        void testRequestTargetingRulesReturnsEmptyForNull() {
                assertThat(process(true).requestTargetingRules(null)).isEmpty();
        }

        @Test
        void testResponseLocalesBuildsLocaleLinks() {
                TurSNSite site = new TurSNSite();

                TurSNSiteLocale localeEn = new TurSNSiteLocale();
                localeEn.setLanguage(Locale.US);
                TurSNSiteLocale localePt = new TurSNSiteLocale();
                localePt.setLanguage(Locale.of("pt", "BR"));

                when(turSNSiteLocaleRepository.findByTurSNSite(any(), eq(site)))
                                .thenReturn(List.of(localeEn, localePt));

                var locales = process(true).responseLocales(site, URI.create("http://localhost/search?q=test"));

                assertThat(locales).hasSize(2);
                assertThat(locales.get(0).getLink()).contains("locale=");
                assertThat(locales.get(1).getLink()).contains("locale=");
        }

        @Test
        void testPopulateMetricsSavesWhenEnabledAndQueryIsNotWildcard() {
                TurSNSearchParams searchParams = new TurSNSearchParams();
                searchParams.setQ("java");
                searchParams.setLocale(Locale.US);
                TurSNSitePostParamsBean post = new TurSNSitePostParamsBean();
                post.setUserId("user-1");
                post.setTargetingRules(List.of("segment:vip"));
                post.setPopulateMetrics(true);

                TurSNSiteSearchContext context = new TurSNSiteSearchContext("site", new TurSNConfig(),
                                new TurSEParameters(searchParams, post), Locale.US,
                                URI.create("http://localhost/search?q=java"), post);

                process(true).populateMetrics(new TurSNSite(), context, 3);

                verify(turSNSiteMetricAccessRepository).save(any());
        }

        @Test
        void testPopulateMetricsSkipsWhenDisabledOrWildcard() {
                TurSNSearchProcess processDisabled = new TurSNSearchProcess(turSNSiteFieldExtRepository,
                                turSNSiteFieldExtFacetRepository, turSNSiteRepository, turSNSiteLocaleRepository,
                                turSolrInstanceProcess, turSNSpotlightProcess, turSNSiteMetricAccessRepository,
                                false, searchEnginePluginFactory, turSolrQueryBuilder,
                                turSNFacetDefinitionFactory);

                TurSNSearchParams searchParamsWildcard = new TurSNSearchParams();
                searchParamsWildcard.setQ("*");
                TurSNSitePostParamsBean post = new TurSNSitePostParamsBean();
                post.setPopulateMetrics(true);
                TurSNSiteSearchContext contextWildcard = new TurSNSiteSearchContext("site", new TurSNConfig(),
                                new TurSEParameters(searchParamsWildcard, post), Locale.US,
                                URI.create("http://localhost/search?q=*"), post);

                processDisabled.populateMetrics(new TurSNSite(), contextWildcard, 0);

                verify(turSNSiteMetricAccessRepository, never()).save(any());

                TurSNSearchProcess processEnabled = new TurSNSearchProcess(turSNSiteFieldExtRepository,
                                turSNSiteFieldExtFacetRepository, turSNSiteRepository, turSNSiteLocaleRepository,
                                turSolrInstanceProcess, turSNSpotlightProcess, turSNSiteMetricAccessRepository,
                                true, searchEnginePluginFactory, turSolrQueryBuilder,
                                turSNFacetDefinitionFactory);

                TurSNSearchParams searchParams = new TurSNSearchParams();
                searchParams.setQ("java");
                TurSNSitePostParamsBean postNoMetrics = new TurSNSitePostParamsBean();
                postNoMetrics.setPopulateMetrics(false);
                TurSNSiteSearchContext contextNoMetrics = new TurSNSiteSearchContext("site", new TurSNConfig(),
                                new TurSEParameters(searchParams, postNoMetrics), Locale.US,
                                URI.create("http://localhost/search?q=java"), postNoMetrics);

                processEnabled.populateMetrics(new TurSNSite(), contextNoMetrics, 0);

                verify(turSNSiteMetricAccessRepository, never()).save(any());
        }

        @Test
        void testSearchBuildsResultsWidgetAndPagination() {
                TurSearchEnginePlugin plugin = org.mockito.Mockito.mock(TurSearchEnginePlugin.class);
                when(searchEnginePluginFactory.getDefaultPlugin()).thenReturn(plugin);

                TurSNSite site = new TurSNSite();
                site.setId("site-id");
                site.setName("site");
                site.setFacet(1);
                site.setFacetType(TurSNSiteFacetFieldEnum.AND);
                site.setFacetItemType(TurSNSiteFacetFieldEnum.OR);
                site.setItemsPerFacet(10);
                site.setSpotlightWithResults(0);

                TurSNSiteFieldExtFacet facetLocale = new TurSNSiteFieldExtFacet();
                facetLocale.setLocale(Locale.US);
                facetLocale.setLabel("Category");

                TurSNSiteFieldExt facetField = TurSNSiteFieldExt.builder()
                                .name("category")
                                .facetName("Category")
                                .snType(TurSNFieldType.SE)
                                .type(com.viglet.turing.commons.se.field.TurSEFieldType.STRING)
                                .enabled(1)
                                .facet(1)
                                .build();

                when(turSNSiteRepository.findByName("site")).thenReturn(Optional.of(site));
                when(turSNSiteFieldExtRepository.findByTurSNSiteAndFacetAndEnabled(site, 1, 1))
                                .thenReturn(List.of(facetField));
                when(turSNSiteFieldExtRepository.findByTurSNSiteAndEnabled(site, 1))
                                .thenReturn(List.of(facetField));
                when(turSNSiteFieldExtFacetRepository.findByTurSNSiteFieldExtAndLocale(facetField, Locale.US))
                                .thenReturn(Set.of(facetLocale));
                when(turSNSiteLocaleRepository.findByTurSNSite(any(), eq(site))).thenReturn(List.of());
                when(turSolrQueryBuilder.hasGroup(any())).thenReturn(false);
                when(turSolrQueryBuilder.getFacetFieldsInFilterQuery(any())).thenReturn(List.of("category"));
                when(turSolrInstanceProcess.initSolrInstance("site", Locale.US))
                                .thenReturn(Optional.of(org.mockito.Mockito.mock(TurSolrInstance.class)));

                TurSEFacetResult facetResult = new TurSEFacetResult();
                facetResult.setFacet("category");
                facetResult.add("books", new TurSEFacetResultAttr("books", 4));

                TurSEResult seResult = TurSEResult.builder().fields(Map.of("title", "Java Book")).build();
                TurSEResults seResults = TurSEResults.builder()
                                .results(List.of(seResult))
                                .facetResults(List.of(facetResult))
                                .spellCheck(new TurSESpellCheckResult())
                                .numFound(40)
                                .start(10)
                                .limit(10)
                                .currentPage(2)
                                .pageCount(4)
                                .queryString("java")
                                .elapsedTime(12L)
                                .build();
                when(plugin.retrieveSearchResults(any())).thenReturn(Optional.of(seResults));

                TurSNSearchParams params = new TurSNSearchParams();
                params.setQ("java");
                params.setP(2);
                params.setRows(10);
                params.setFq(List.of("category:books"));
                TurSNSitePostParamsBean post = new TurSNSitePostParamsBean();
                TurSNSiteSearchContext searchContext = new TurSNSiteSearchContext("site", new TurSNConfig(),
                                new TurSEParameters(params, post), Locale.US,
                                URI.create("http://localhost/search?q=java&fq=category:books"), post);

                var bean = process(false).search(searchContext);

                assertThat(bean.getResults()).isNotNull();
                assertThat(bean.getResults().getDocument()).hasSize(1);
                assertThat(bean.getWidget()).isNotNull();
                assertThat(bean.getWidget().getFacet()).isNotEmpty();
                assertThat(bean.getWidget().getFacetToRemove()).isNotNull();
                assertThat(bean.getPagination()).isNotEmpty();
                assertThat(bean.getQueryContext()).isNotNull();
        }

        @Test
        void testSearchBuildsGroupedResponseWhenGroupEnabled() {
                TurSearchEnginePlugin plugin = org.mockito.Mockito.mock(TurSearchEnginePlugin.class);
                when(searchEnginePluginFactory.getDefaultPlugin()).thenReturn(plugin);

                TurSNSite site = new TurSNSite();
                site.setId("site-id");
                site.setName("site");
                site.setFacet(0);
                site.setSpotlightWithResults(0);

                when(turSNSiteRepository.findByName("site")).thenReturn(Optional.of(site));
                when(turSNSiteFieldExtRepository.findByTurSNSiteAndFacetAndEnabled(site, 1, 1))
                                .thenReturn(List.of());
                when(turSNSiteFieldExtRepository.findByTurSNSiteAndEnabled(site, 1))
                                .thenReturn(List.of());
                when(turSNSiteLocaleRepository.findByTurSNSite(any(), eq(site))).thenReturn(List.of());
                when(turSolrQueryBuilder.hasGroup(any())).thenReturn(true);
                when(turSolrQueryBuilder.getFacetFieldsInFilterQuery(any())).thenReturn(List.of());
                when(turSolrInstanceProcess.initSolrInstance("site", Locale.US))
                                .thenReturn(Optional.of(org.mockito.Mockito.mock(TurSolrInstance.class)));

                TurSEResult groupResult = TurSEResult.builder().fields(Map.of("title", "Grouped Doc")).build();
                TurSEGroup group = TurSEGroup.builder()
                                .name("type:article")
                                .results(List.of(groupResult))
                                .numFound(2)
                                .start(0)
                                .limit(10)
                                .currentPage(1)
                                .pageCount(1)
                                .build();
                TurSEResults seResults = TurSEResults.builder()
                                .groups(List.of(group))
                                .spellCheck(new TurSESpellCheckResult())
                                .queryString("java")
                                .currentPage(1)
                                .pageCount(1)
                                .limit(10)
                                .numFound(2)
                                .build();
                when(plugin.retrieveSearchResults(any())).thenReturn(Optional.of(seResults));

                TurSNSearchParams params = new TurSNSearchParams();
                params.setQ("java");
                params.setRows(10);
                params.setGroup("type");
                TurSNSiteSearchContext searchContext = new TurSNSiteSearchContext("site", new TurSNConfig(),
                                new TurSEParameters(params, new TurSNSitePostParamsBean()), Locale.US,
                                URI.create("http://localhost/search?q=java&group=type"));

                var bean = process(false).search(searchContext);

                assertThat(bean.getGroups()).hasSize(1);
                assertThat(bean.getGroups().getFirst().getName()).isEqualTo("type:article");
                assertThat(bean.getGroups().getFirst().getResults().getDocument()).hasSize(1);
                assertThat(bean.getWidget()).isNotNull();
        }

        @Test
        void testSearchListReturnsMappedObjectsForMultipleFields() {
                TurSearchEnginePlugin plugin = org.mockito.Mockito.mock(TurSearchEnginePlugin.class);
                when(searchEnginePluginFactory.getDefaultPlugin()).thenReturn(plugin);

                TurSNSite site = new TurSNSite();
                site.setName("site");
                when(turSNSiteRepository.findByName("site")).thenReturn(Optional.of(site));
                when(turSolrInstanceProcess.initSolrInstance("site", Locale.US))
                                .thenReturn(Optional.of(org.mockito.Mockito.mock(TurSolrInstance.class)));

                TurSEResult r1 = TurSEResult.builder().fields(Map.of("title", "Java", "url", "/java")).build();
                TurSEResult r2 = TurSEResult.builder().fields(Map.of("title", "Spring", "url", "/spring"))
                                .build();
                TurSEResults seResults = TurSEResults.builder()
                                .results(List.of(r1, r2))
                                .numFound(2)
                                .build();
                when(plugin.retrieveSearchResults(any())).thenReturn(Optional.of(seResults));

                TurSNSearchParams params = new TurSNSearchParams();
                params.setQ("java");
                params.setFl(List.of("title", "url"));
                TurSNSiteSearchContext searchContext = new TurSNSiteSearchContext("site", new TurSNConfig(),
                                new TurSEParameters(params, new TurSNSitePostParamsBean()), Locale.US,
                                URI.create("http://localhost/search?q=java"));

                var list = process(false).searchList(searchContext);

                assertThat(list).hasSize(2);
                assertThat(list.getFirst()).isInstanceOf(Map.class);
                @SuppressWarnings("unchecked")
                Map<String, Object> first = (Map<String, Object>) list.getFirst();
                assertThat(first)
                                .containsEntry("title", "Java")
                                .containsEntry("url", "/java");
        }

        @Test
        void testSearchListUsesDefaultSingleFieldAndSkipsNullValues() {
                TurSearchEnginePlugin plugin = org.mockito.Mockito.mock(TurSearchEnginePlugin.class);
                when(searchEnginePluginFactory.getDefaultPlugin()).thenReturn(plugin);

                TurSNSite site = new TurSNSite();
                site.setName("site");
                when(turSNSiteRepository.findByName("site")).thenReturn(Optional.of(site));
                when(turSolrInstanceProcess.initSolrInstance("site", Locale.US))
                                .thenReturn(Optional.of(org.mockito.Mockito.mock(TurSolrInstance.class)));

                TurSEResult withTitle = TurSEResult.builder().fields(Map.of("title", "OnlyTitle")).build();
                TurSEResult withoutTitle = TurSEResult.builder().fields(Map.of("url", "/x")).build();
                TurSEResults seResults = TurSEResults.builder().results(List.of(withTitle, withoutTitle)).build();
                when(plugin.retrieveSearchResults(any())).thenReturn(Optional.of(seResults));

                TurSNSearchParams params = new TurSNSearchParams();
                params.setQ("java");
                TurSNSiteSearchContext context = new TurSNSiteSearchContext("site", new TurSNConfig(),
                                new TurSEParameters(params, new TurSNSitePostParamsBean()), Locale.US,
                                URI.create("http://localhost/search?q=java"));

                var list = process(false).searchList(context);

                assertThat(list).containsExactly("OnlyTitle");
        }

        @Test
        void testSearchWidgetIncludesSpotlights() {
                TurSearchEnginePlugin plugin = org.mockito.Mockito.mock(TurSearchEnginePlugin.class);
                when(searchEnginePluginFactory.getDefaultPlugin()).thenReturn(plugin);

                TurSNSite site = new TurSNSite();
                site.setId("site-id");
                site.setName("site");
                site.setFacet(0);
                site.setSpotlightWithResults(1);

                when(turSNSiteRepository.findByName("site")).thenReturn(Optional.of(site));
                when(turSNSiteFieldExtRepository.findByTurSNSiteAndFacetAndEnabled(site, 1, 1)).thenReturn(List.of());
                when(turSNSiteFieldExtRepository.findByTurSNSiteAndEnabled(site, 1)).thenReturn(List.of());
                when(turSNSiteLocaleRepository.findByTurSNSite(any(), eq(site))).thenReturn(List.of());
                when(turSolrQueryBuilder.hasGroup(any())).thenReturn(false);
                when(turSolrQueryBuilder.getFacetFieldsInFilterQuery(any())).thenReturn(List.of());
                when(turSolrInstanceProcess.initSolrInstance("site", Locale.US))
                                .thenReturn(Optional.of(org.mockito.Mockito.mock(TurSolrInstance.class)));

                TurSNSiteSpotlightDocument spotlight = new TurSNSiteSpotlightDocument();
                spotlight.setId("sp1");
                spotlight.setPosition(1);
                spotlight.setTitle("Spotlight");
                spotlight.setContent("Content");
                spotlight.setLink("/spotlight");
                spotlight.setReferenceId("ref-1");
                spotlight.setType("doc");
                when(turSNSpotlightProcess.getSpotlightsFromQuery(any(), eq(site)))
                                .thenReturn(Map.of(1, List.of(spotlight)));

                TurSEResults seResults = TurSEResults.builder()
                                .results(List.of(TurSEResult.builder().fields(Map.of("title", "Doc")).build()))
                                .spellCheck(new TurSESpellCheckResult())
                                .numFound(1)
                                .start(0)
                                .limit(10)
                                .currentPage(1)
                                .pageCount(1)
                                .queryString("java")
                                .build();
                when(plugin.retrieveSearchResults(any())).thenReturn(Optional.of(seResults));

                TurSNSearchParams params = new TurSNSearchParams();
                params.setQ("java");
                TurSNSiteSearchContext context = new TurSNSiteSearchContext("site", new TurSNConfig(),
                                new TurSEParameters(params, new TurSNSitePostParamsBean()), Locale.US,
                                URI.create("http://localhost/search?q=java"));

                var bean = process(false).search(context);

                assertThat(bean.getWidget().getSpotlights()).hasSize(1);
                assertThat(bean.getWidget().getSpotlights().getFirst().getTitle()).isEqualTo("Spotlight");
        }

        @Test
        void testSearchFacetAndOrUsesRetrieveFacetResults() {
                TurSearchEnginePlugin plugin = org.mockito.Mockito.mock(TurSearchEnginePlugin.class);
                when(searchEnginePluginFactory.getDefaultPlugin()).thenReturn(plugin);

                TurSNSite site = new TurSNSite();
                site.setId("site-id");
                site.setName("site");
                site.setFacet(1);
                site.setFacetType(TurSNSiteFacetFieldEnum.AND);
                site.setFacetItemType(TurSNSiteFacetFieldEnum.OR);
                site.setItemsPerFacet(10);
                site.setSpotlightWithResults(0);

                TurSNSiteFieldExt facetField = TurSNSiteFieldExt.builder()
                                .name("category")
                                .facetName("Category")
                                .snType(TurSNFieldType.SE)
                                .type(com.viglet.turing.commons.se.field.TurSEFieldType.STRING)
                                .enabled(1)
                                .facet(1)
                                .build();

                when(turSNSiteRepository.findByName("site")).thenReturn(Optional.of(site));
                when(turSNSiteFieldExtRepository.findByTurSNSiteAndFacetAndEnabled(site, 1, 1))
                                .thenReturn(List.of(facetField));
                when(turSNSiteFieldExtRepository.findByTurSNSiteAndEnabled(site, 1))
                                .thenReturn(List.of(facetField));
                when(turSNSiteFieldExtFacetRepository.findByTurSNSiteFieldExtAndLocale(facetField, Locale.US))
                                .thenReturn(Set.of());
                when(turSNSiteLocaleRepository.findByTurSNSite(any(), eq(site))).thenReturn(List.of());
                when(turSolrQueryBuilder.hasGroup(any())).thenReturn(false);
                when(turSolrQueryBuilder.getFacetFieldsInFilterQuery(any())).thenReturn(List.of("category"));
                when(turSolrQueryBuilder.getFqFields(any())).thenReturn(List.of("category"));
                when(turSolrInstanceProcess.initSolrInstance("site", Locale.US))
                                .thenReturn(Optional.of(org.mockito.Mockito.mock(TurSolrInstance.class)));

                TurSEFacetResult baseFacet = new TurSEFacetResult();
                baseFacet.setFacet("category");
                baseFacet.add("books", new TurSEFacetResultAttr("books", 0));

                TurSEFacetResult refreshedFacet = new TurSEFacetResult();
                refreshedFacet.setFacet("category");
                refreshedFacet.add("books", new TurSEFacetResultAttr("books", 7));
                TurSEResults refreshed = TurSEResults.builder().facetResults(List.of(refreshedFacet)).build();
                when(plugin.retrieveFacetResults(any(), eq("category"))).thenReturn(Optional.of(refreshed));

                TurSEResults searchResults = TurSEResults.builder()
                                .results(List.of(TurSEResult.builder().fields(Map.of("title", "Doc")).build()))
                                .facetResults(List.of(baseFacet))
                                .spellCheck(new TurSESpellCheckResult())
                                .numFound(1)
                                .start(0)
                                .limit(10)
                                .currentPage(1)
                                .pageCount(1)
                                .queryString("java")
                                .build();
                when(plugin.retrieveSearchResults(any())).thenReturn(Optional.of(searchResults));

                TurSNSearchParams params = new TurSNSearchParams();
                params.setQ("java");
                params.setFq(List.of("category:books"));
                TurSNSiteSearchContext context = new TurSNSiteSearchContext("site", new TurSNConfig(),
                                new TurSEParameters(params, new TurSNSitePostParamsBean()), Locale.US,
                                URI.create("http://localhost/search?q=java&fq=category:books"));

                var bean = process(false).search(context);

                assertThat(bean.getWidget().getFacet()).isNotEmpty();
                verify(plugin).retrieveFacetResults(any(), eq("category"));
        }

        @Test
        void testSearchSecondaryFacetAndFacetItemAndLinks() {
                TurSearchEnginePlugin plugin = org.mockito.Mockito.mock(TurSearchEnginePlugin.class);
                when(searchEnginePluginFactory.getDefaultPlugin()).thenReturn(plugin);

                TurSNSite site = new TurSNSite();
                site.setId("site-id");
                site.setName("site");
                site.setFacet(1);
                site.setFacetType(TurSNSiteFacetFieldEnum.AND);
                site.setFacetItemType(TurSNSiteFacetFieldEnum.OR);
                site.setItemsPerFacet(10);
                site.setSpotlightWithResults(0);

                TurSNSiteFieldExt facetField = TurSNSiteFieldExt.builder()
                                .name("category")
                                .facetName("Category")
                                .snType(TurSNFieldType.SE)
                                .type(com.viglet.turing.commons.se.field.TurSEFieldType.STRING)
                                .facetItemType(TurSNSiteFacetFieldEnum.AND)
                                .secondaryFacet(true)
                                .enabled(1)
                                .facet(1)
                                .build();

                when(turSNSiteRepository.findByName("site")).thenReturn(Optional.of(site));
                when(turSNSiteFieldExtRepository.findByTurSNSiteAndFacetAndEnabled(site, 1, 1))
                                .thenReturn(List.of(facetField));
                when(turSNSiteFieldExtRepository.findByTurSNSiteAndEnabled(site, 1))
                                .thenReturn(List.of(facetField));
                when(turSNSiteFieldExtFacetRepository.findByTurSNSiteFieldExtAndLocale(facetField, Locale.US))
                                .thenReturn(Set.of());
                when(turSNSiteLocaleRepository.findByTurSNSite(any(), eq(site))).thenReturn(List.of());
                when(turSolrQueryBuilder.hasGroup(any())).thenReturn(false);
                when(turSolrQueryBuilder.getFacetFieldsInFilterQuery(any())).thenReturn(List.of("category"));
                when(turSolrInstanceProcess.initSolrInstance("site", Locale.US))
                                .thenReturn(Optional.of(org.mockito.Mockito.mock(TurSolrInstance.class)));

                TurSEFacetResult facet = new TurSEFacetResult();
                facet.setFacet("category");
                facet.add("books", new TurSEFacetResultAttr("books", 5));
                facet.add("music", new TurSEFacetResultAttr("music", 1));

                TurSEResults seResults = TurSEResults.builder()
                                .results(List.of(TurSEResult.builder().fields(Map.of("title", "Doc")).build()))
                                .facetResults(List.of(facet))
                                .spellCheck(new TurSESpellCheckResult())
                                .numFound(1)
                                .start(0)
                                .limit(10)
                                .currentPage(1)
                                .pageCount(1)
                                .queryString("java")
                                .build();
                when(plugin.retrieveSearchResults(any())).thenReturn(Optional.of(seResults));

                TurSNSearchParams params = new TurSNSearchParams();
                params.setQ("java");
                params.setFq(List.of("category:books"));
                TurSNSiteSearchContext context = new TurSNSiteSearchContext("site", new TurSNConfig(),
                                new TurSEParameters(params, new TurSNSitePostParamsBean()), Locale.US,
                                URI.create("http://localhost/search?q=java&fq=category:books"));

                var bean = process(false).search(context);

                assertThat(bean.getWidget().getSecondaryFacet()).isNotEmpty();
                var items = bean.getWidget().getSecondaryFacet().getFirst().getFacets();
                assertThat(items).hasSizeGreaterThanOrEqualTo(2);
                assertThat(items.stream().map(i -> i.getLink()).toList())
                                .allMatch(link -> link.contains("/search") && link.contains("fq"));
        }

        @Test
        void testSearchUsesDefaultFacetTypesWhenNullAndHidesSelectedFacetForNullItemType() {
                TurSearchEnginePlugin plugin = org.mockito.Mockito.mock(TurSearchEnginePlugin.class);
                when(searchEnginePluginFactory.getDefaultPlugin()).thenReturn(plugin);

                TurSNSite site = new TurSNSite();
                site.setId("site-id");
                site.setName("site");
                site.setFacet(1);
                site.setFacetType(null);
                site.setFacetItemType(null);
                site.setItemsPerFacet(10);
                site.setSpotlightWithResults(0);

                TurSNSiteFieldExt facetField = TurSNSiteFieldExt.builder()
                                .name("entity")
                                .facetName("Entity")
                                .snType(TurSNFieldType.NER)
                                .type(com.viglet.turing.commons.se.field.TurSEFieldType.STRING)
                                .enabled(1)
                                .facet(1)
                                .build();

                when(turSNSiteRepository.findByName("site")).thenReturn(Optional.of(site));
                when(turSNSiteFieldExtRepository.findByTurSNSiteAndFacetAndEnabled(site, 1, 1))
                                .thenReturn(List.of(facetField));
                when(turSNSiteFieldExtRepository.findByTurSNSiteAndEnabled(site, 1))
                                .thenReturn(List.of(facetField));
                when(turSNSiteFieldExtFacetRepository.findByTurSNSiteFieldExtAndLocale(facetField, Locale.US))
                                .thenReturn(Set.of());
                when(turSNSiteLocaleRepository.findByTurSNSite(any(), eq(site))).thenReturn(List.of());
                when(turSolrQueryBuilder.hasGroup(any())).thenReturn(false);
                when(turSolrQueryBuilder.getFacetFieldsInFilterQuery(any())).thenReturn(List.of("entity"));
                when(turSolrInstanceProcess.initSolrInstance("site", Locale.US))
                                .thenReturn(Optional.of(org.mockito.Mockito.mock(TurSolrInstance.class)));

                TurSEFacetResult facet = new TurSEFacetResult();
                facet.setFacet("entity");
                facet.add("person", new TurSEFacetResultAttr("person", 1));

                TurSEResults seResults = TurSEResults.builder()
                                .results(List.of(TurSEResult.builder().fields(Map.of("title", "Doc")).build()))
                                .facetResults(List.of(facet))
                                .spellCheck(new TurSESpellCheckResult())
                                .numFound(1)
                                .start(0)
                                .limit(10)
                                .currentPage(1)
                                .pageCount(1)
                                .queryString("java")
                                .build();
                when(plugin.retrieveSearchResults(any())).thenReturn(Optional.of(seResults));

                TurSNSearchParams params = new TurSNSearchParams();
                params.setQ("java");
                params.setFq(List.of("entity:person"));
                TurSNSiteSearchContext context = new TurSNSiteSearchContext("site", new TurSNConfig(),
                                new TurSEParameters(params, new TurSNSitePostParamsBean()), Locale.US,
                                URI.create("http://localhost/search?q=java&fq=entity:person"));

                var bean = process(false).search(context);

                assertThat(bean.getWidget().getFacet()).isEmpty();
                assertThat(bean.getQueryContext().getFacetType()).isEqualTo("AND");
                assertThat(bean.getQueryContext().getFacetItemType()).isEqualTo("AND");
        }

        @Test
        void testSearchShowAllFacetItemsCreatesUnselectedOrLink() {
                TurSearchEnginePlugin plugin = org.mockito.Mockito.mock(TurSearchEnginePlugin.class);
                when(searchEnginePluginFactory.getDefaultPlugin()).thenReturn(plugin);

                TurSNSite site = new TurSNSite();
                site.setId("site-id");
                site.setName("site");
                site.setFacet(1);
                site.setFacetType(TurSNSiteFacetFieldEnum.OR);
                site.setFacetItemType(TurSNSiteFacetFieldEnum.OR);
                site.setItemsPerFacet(10);
                site.setSpotlightWithResults(0);

                TurSNSiteFieldExt facetField = TurSNSiteFieldExt.builder()
                                .name("entity")
                                .facetName("Entity")
                                .snType(TurSNFieldType.NER)
                                .type(com.viglet.turing.commons.se.field.TurSEFieldType.STRING)
                                .showAllFacetItems(true)
                                .enabled(1)
                                .facet(1)
                                .build();

                when(turSNSiteRepository.findByName("site")).thenReturn(Optional.of(site));
                when(turSNSiteFieldExtRepository.findByTurSNSiteAndFacetAndEnabled(site, 1, 1))
                                .thenReturn(List.of(facetField));
                when(turSNSiteFieldExtRepository.findByTurSNSiteAndEnabled(site, 1))
                                .thenReturn(List.of(facetField));
                when(turSNSiteFieldExtFacetRepository.findByTurSNSiteFieldExtAndLocale(facetField, Locale.US))
                                .thenReturn(Set.of());
                when(turSNSiteLocaleRepository.findByTurSNSite(any(), eq(site))).thenReturn(List.of());
                when(turSolrQueryBuilder.hasGroup(any())).thenReturn(false);
                when(turSolrQueryBuilder.getFacetFieldsInFilterQuery(any())).thenReturn(List.of("entity"));
                when(turSolrInstanceProcess.initSolrInstance("site", Locale.US))
                                .thenReturn(Optional.of(org.mockito.Mockito.mock(TurSolrInstance.class)));

                TurSEFacetResult facet = new TurSEFacetResult();
                facet.setFacet("entity");
                facet.add("zero", new TurSEFacetResultAttr("zero", 0));

                TurSEResults seResults = TurSEResults.builder()
                                .results(List.of(TurSEResult.builder().fields(Map.of("title", "Doc")).build()))
                                .facetResults(List.of(facet))
                                .spellCheck(new TurSESpellCheckResult())
                                .numFound(1)
                                .start(0)
                                .limit(10)
                                .currentPage(1)
                                .pageCount(1)
                                .queryString("java")
                                .build();
                when(plugin.retrieveSearchResults(any())).thenReturn(Optional.of(seResults));

                TurSNSearchParams params = new TurSNSearchParams();
                params.setQ("java");
                TurSNSiteSearchContext context = new TurSNSiteSearchContext("site", new TurSNConfig(),
                                new TurSEParameters(params, new TurSNSitePostParamsBean()), Locale.US,
                                URI.create("http://localhost/search?q=java"));

                var bean = process(false).search(context);

                assertThat(bean.getWidget().getFacet()).isNotEmpty();
                var firstItem = bean.getWidget().getFacet().getFirst().getFacets().getFirst();
                assertThat(firstItem.isSelected()).isFalse();
                assertThat(firstItem.getLink()).contains("fq[]=entity:zero");
        }

        @Test
        void testSearchFacetToRemoveUsesQuotedFilterValueLabel() {
                TurSearchEnginePlugin plugin = org.mockito.Mockito.mock(TurSearchEnginePlugin.class);
                when(searchEnginePluginFactory.getDefaultPlugin()).thenReturn(plugin);

                TurSNSite site = new TurSNSite();
                site.setId("site-id");
                site.setName("site");
                site.setFacet(1);
                site.setFacetType(TurSNSiteFacetFieldEnum.AND);
                site.setFacetItemType(TurSNSiteFacetFieldEnum.OR);
                site.setItemsPerFacet(10);
                site.setSpotlightWithResults(0);

                TurSNSiteFieldExt facetField = TurSNSiteFieldExt.builder()
                                .name("category")
                                .facetName("Category")
                                .snType(TurSNFieldType.SE)
                                .type(com.viglet.turing.commons.se.field.TurSEFieldType.STRING)
                                .enabled(1)
                                .facet(1)
                                .build();

                when(turSNSiteRepository.findByName("site")).thenReturn(Optional.of(site));
                when(turSNSiteFieldExtRepository.findByTurSNSiteAndFacetAndEnabled(site, 1, 1))
                                .thenReturn(List.of(facetField));
                when(turSNSiteFieldExtRepository.findByTurSNSiteAndEnabled(site, 1))
                                .thenReturn(List.of(facetField));
                when(turSNSiteFieldExtFacetRepository.findByTurSNSiteFieldExtAndLocale(facetField, Locale.US))
                                .thenReturn(Set.of());
                when(turSNSiteLocaleRepository.findByTurSNSite(any(), eq(site))).thenReturn(List.of());
                when(turSolrQueryBuilder.hasGroup(any())).thenReturn(false);
                when(turSolrQueryBuilder.getFacetFieldsInFilterQuery(any())).thenReturn(List.of("category"));
                when(turSolrInstanceProcess.initSolrInstance("site", Locale.US))
                                .thenReturn(Optional.of(org.mockito.Mockito.mock(TurSolrInstance.class)));

                TurSEResults seResults = TurSEResults.builder()
                                .results(List.of(TurSEResult.builder().fields(Map.of("title", "Doc")).build()))
                                .spellCheck(new TurSESpellCheckResult())
                                .numFound(1)
                                .start(0)
                                .limit(10)
                                .currentPage(1)
                                .pageCount(1)
                                .queryString("java")
                                .build();
                when(plugin.retrieveSearchResults(any())).thenReturn(Optional.of(seResults));

                TurSNSearchParams params = new TurSNSearchParams();
                params.setQ("java");
                params.setFq(List.of("category:\"books\""));
                TurSNSiteSearchContext context = new TurSNSiteSearchContext("site", new TurSNConfig(),
                                new TurSEParameters(params, new TurSNSitePostParamsBean()), Locale.US,
                                URI.create("http://localhost/search?q=java&fq=category:%22books%22"));

                var bean = process(false).search(context);

                assertThat(bean.getWidget().getFacetToRemove()).isNotNull();
                assertThat(bean.getWidget().getFacetToRemove().getFacets()).isNotEmpty();
                assertThat(bean.getWidget().getFacetToRemove().getFacets().getFirst().getLabel())
                                .isEqualTo("books");
        }

        @Test
        void testSearchAndOrShowsSelectedFacetWithZeroCount() {
                TurSearchEnginePlugin plugin = org.mockito.Mockito.mock(TurSearchEnginePlugin.class);
                when(searchEnginePluginFactory.getDefaultPlugin()).thenReturn(plugin);

                TurSNSite site = new TurSNSite();
                site.setId("site-id");
                site.setName("site");
                site.setFacet(1);
                site.setFacetType(TurSNSiteFacetFieldEnum.AND);
                site.setFacetItemType(TurSNSiteFacetFieldEnum.OR);
                site.setItemsPerFacet(10);
                site.setSpotlightWithResults(0);

                TurSNSiteFieldExt facetField = TurSNSiteFieldExt.builder()
                                .name("category")
                                .facetName("Category")
                                .snType(TurSNFieldType.SE)
                                .type(com.viglet.turing.commons.se.field.TurSEFieldType.STRING)
                                .enabled(1)
                                .facet(1)
                                .build();

                when(turSNSiteRepository.findByName("site")).thenReturn(Optional.of(site));
                when(turSNSiteFieldExtRepository.findByTurSNSiteAndFacetAndEnabled(site, 1, 1))
                                .thenReturn(List.of(facetField));
                when(turSNSiteFieldExtRepository.findByTurSNSiteAndEnabled(site, 1))
                                .thenReturn(List.of(facetField));
                when(turSNSiteFieldExtFacetRepository.findByTurSNSiteFieldExtAndLocale(facetField, Locale.US))
                                .thenReturn(Set.of());
                when(turSNSiteLocaleRepository.findByTurSNSite(any(), eq(site))).thenReturn(List.of());
                when(turSolrQueryBuilder.hasGroup(any())).thenReturn(false);
                when(turSolrQueryBuilder.getFacetFieldsInFilterQuery(any())).thenReturn(List.of("category"));
                when(turSolrQueryBuilder.getFqFields(any())).thenReturn(List.of());
                when(turSolrInstanceProcess.initSolrInstance("site", Locale.US))
                                .thenReturn(Optional.of(org.mockito.Mockito.mock(TurSolrInstance.class)));

                TurSEFacetResult facet = new TurSEFacetResult();
                facet.setFacet("category");
                facet.add("books", new TurSEFacetResultAttr("books", 0));

                TurSEResults seResults = TurSEResults.builder()
                                .results(List.of(TurSEResult.builder().fields(Map.of("title", "Doc")).build()))
                                .facetResults(List.of(facet))
                                .spellCheck(new TurSESpellCheckResult())
                                .numFound(1)
                                .start(0)
                                .limit(10)
                                .currentPage(1)
                                .pageCount(1)
                                .queryString("java")
                                .build();
                when(plugin.retrieveSearchResults(any())).thenReturn(Optional.of(seResults));

                TurSNSearchParams params = new TurSNSearchParams();
                params.setQ("java");
                params.setFq(List.of("category:books"));
                TurSNSiteSearchContext context = new TurSNSiteSearchContext("site", new TurSNConfig(),
                                new TurSEParameters(params, new TurSNSitePostParamsBean()), Locale.US,
                                URI.create("http://localhost/search?q=java&fq=category:books"));

                var bean = process(false).search(context);

                assertThat(bean.getWidget().getFacet()).isNotEmpty();
                var item = bean.getWidget().getFacet().getFirst().getFacets().getFirst();
                assertThat(item.isSelected()).isTrue();
                assertThat(item.getCount()).isZero();
        }

        @Test
        void testSearchHidesSelectedFacetWhenFacetItemTypeAnd() {
                TurSearchEnginePlugin plugin = org.mockito.Mockito.mock(TurSearchEnginePlugin.class);
                when(searchEnginePluginFactory.getDefaultPlugin()).thenReturn(plugin);

                TurSNSite site = new TurSNSite();
                site.setId("site-id");
                site.setName("site");
                site.setFacet(1);
                site.setFacetType(TurSNSiteFacetFieldEnum.AND);
                site.setFacetItemType(TurSNSiteFacetFieldEnum.AND);
                site.setItemsPerFacet(10);
                site.setSpotlightWithResults(0);

                TurSNSiteFieldExt facetField = TurSNSiteFieldExt.builder()
                                .name("category")
                                .facetName("Category")
                                .snType(TurSNFieldType.SE)
                                .type(com.viglet.turing.commons.se.field.TurSEFieldType.STRING)
                                .enabled(1)
                                .facet(1)
                                .build();

                when(turSNSiteRepository.findByName("site")).thenReturn(Optional.of(site));
                when(turSNSiteFieldExtRepository.findByTurSNSiteAndFacetAndEnabled(site, 1, 1))
                                .thenReturn(List.of(facetField));
                when(turSNSiteFieldExtRepository.findByTurSNSiteAndEnabled(site, 1))
                                .thenReturn(List.of(facetField));
                when(turSNSiteFieldExtFacetRepository.findByTurSNSiteFieldExtAndLocale(facetField, Locale.US))
                                .thenReturn(Set.of());
                when(turSNSiteLocaleRepository.findByTurSNSite(any(), eq(site))).thenReturn(List.of());
                when(turSolrQueryBuilder.hasGroup(any())).thenReturn(false);
                when(turSolrQueryBuilder.getFacetFieldsInFilterQuery(any())).thenReturn(List.of("category"));
                when(turSolrInstanceProcess.initSolrInstance("site", Locale.US))
                                .thenReturn(Optional.of(org.mockito.Mockito.mock(TurSolrInstance.class)));

                TurSEFacetResult facet = new TurSEFacetResult();
                facet.setFacet("category");
                facet.add("books", new TurSEFacetResultAttr("books", 10));

                TurSEResults seResults = TurSEResults.builder()
                                .results(List.of(TurSEResult.builder().fields(Map.of("title", "Doc")).build()))
                                .facetResults(List.of(facet))
                                .spellCheck(new TurSESpellCheckResult())
                                .numFound(1)
                                .start(0)
                                .limit(10)
                                .currentPage(1)
                                .pageCount(1)
                                .queryString("java")
                                .build();
                when(plugin.retrieveSearchResults(any())).thenReturn(Optional.of(seResults));

                TurSNSearchParams params = new TurSNSearchParams();
                params.setQ("java");
                params.setFq(List.of("category:books"));
                TurSNSiteSearchContext context = new TurSNSiteSearchContext("site", new TurSNConfig(),
                                new TurSEParameters(params, new TurSNSitePostParamsBean()), Locale.US,
                                URI.create("http://localhost/search?q=java&fq=category:books"));

                var bean = process(false).search(context);

                assertThat(bean.getWidget().getFacet()).isEmpty();
        }

        @Test
        void testHasMltReturnsFalseWhenSimilarResultsNull() throws Exception {
                TurSNSite site = new TurSNSite();
                site.setMlt(1);

                TurSEResults seResults = TurSEResults.builder()
                                .similarResults(null)
                                .build();
                var process = process(false);
                var method = TurSNSearchProcess.class.getDeclaredMethod("hasMLT", TurSNSite.class,
                                TurSEResults.class);
                method.setAccessible(true);

                boolean hasMlt = (boolean) method.invoke(process, site, seResults);

                assertThat(hasMlt).isFalse();
        }
}
