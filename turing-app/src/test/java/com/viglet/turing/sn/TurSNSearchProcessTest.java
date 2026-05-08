/*
 * Copyright (C) 2016-2026 the original author or authors.
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
import java.util.HashMap;
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
import com.viglet.turing.domain.sn.TurSNSiteMetricAccessRepositoryPort;
import com.viglet.turing.domain.sn.TurSNSiteMetricAccessTermDomain;
import com.viglet.turing.persistence.model.sn.TurSNSite;
import com.viglet.turing.persistence.model.sn.field.TurSNSiteFacetFieldEnum;
import com.viglet.turing.persistence.model.sn.field.TurSNSiteFieldExt;
import com.viglet.turing.persistence.model.sn.field.TurSNSiteFieldExtFacet;
import com.viglet.turing.persistence.model.sn.locale.TurSNSiteLocale;
import com.viglet.turing.persistence.model.sn.spotlight.TurSNSiteSpotlightDocument;
import com.viglet.turing.persistence.repository.sn.field.TurSNSiteFieldExtRepository;
import com.viglet.turing.persistence.repository.sn.metric.TurSNSiteMetricAccessRepository;
import com.viglet.turing.plugins.se.TurSearchEnginePlugin;
import com.viglet.turing.plugins.se.TurSearchEnginePluginFactory;
import com.viglet.turing.se.facet.TurSEFacetResult;
import com.viglet.turing.se.facet.TurSEFacetResultAttr;
import com.viglet.turing.se.result.TurSEGroup;
import com.viglet.turing.se.result.TurSEResult;
import com.viglet.turing.se.result.TurSEResults;
import com.viglet.turing.sn.facet.TurSNFacetDefinitionFactory;
import com.viglet.turing.sn.snapshot.TurSNSiteSearchSnapshot;
import com.viglet.turing.sn.snapshot.TurSNSiteSearchSnapshotService;
import com.viglet.turing.sn.spotlight.TurSNSpotlightProcess;
import com.viglet.turing.solr.TurSolrInstance;
import com.viglet.turing.solr.TurSolrInstanceProcess;
import com.viglet.turing.solr.TurSolrQueryBuilder;

/**
 * Unit tests for TurSNSearchProcess. Site configuration now flows through the
 * {@link TurSNSiteSearchSnapshotService} mock — tests assemble a snapshot per
 * scenario instead of stubbing each repository call.
 *
 * @author Alexandre Oliveira
 * @since 2026.2.4
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TurSNSearchProcessTest {

        @Mock
        private TurSNSiteSearchSnapshotService snapshotService;

        @Mock
        private TurSolrInstanceProcess turSolrInstanceProcess;

        @Mock
        private TurSNSpotlightProcess turSNSpotlightProcess;

        @Mock
        private TurSNSiteMetricAccessRepository turSNSiteMetricAccessRepository;

        @Mock
        private TurSNSiteMetricAccessRepositoryPort turSNSiteMetricAccessRepositoryPort;

        @Mock
        private TurSearchEnginePluginFactory searchEnginePluginFactory;

        @Mock
        private TurSolrQueryBuilder turSolrQueryBuilder;

        @Mock
        private com.viglet.turing.persistence.repository.sn.field.TurSNSiteCustomFacetRepository turSNSiteCustomFacetRepository;

        @Mock
        private TurSNSiteFieldExtRepository turSNSiteFieldExtRepository;

        private com.viglet.turing.sn.facet.TurSNFacetRenderer turSNFacetRenderer() {
                return new com.viglet.turing.sn.facet.TurSNFacetRenderer(
                                searchEnginePluginFactory, turSolrQueryBuilder);
        }

        private TurSNFacetDefinitionFactory turSNFacetDefinitionFactory() {
                return new TurSNFacetDefinitionFactory(turSNSiteCustomFacetRepository);
        }

        private com.viglet.turing.sn.pagination.TurSNPaginationBuilder turSNPaginationBuilder() {
                return new com.viglet.turing.sn.pagination.TurSNPaginationBuilder();
        }

        private com.viglet.turing.sn.document.TurSNDocumentResponse turSNDocumentResponse() {
                return new com.viglet.turing.sn.document.TurSNDocumentResponse(
                                turSNSiteFieldExtRepository, turSNSpotlightProcess);
        }

        private com.viglet.turing.sn.querycontext.TurSNQueryContextBuilder turSNQueryContextBuilder() {
                return new com.viglet.turing.sn.querycontext.TurSNQueryContextBuilder();
        }

        private TurSNSearchProcess process(boolean metricsEnabled) {
                return new TurSNSearchProcess(snapshotService,
                                turSolrInstanceProcess, turSNSpotlightProcess,
                                turSNSiteMetricAccessRepository,
                                turSNSiteMetricAccessRepositoryPort,
                                metricsEnabled, searchEnginePluginFactory, turSolrQueryBuilder,
                                turSNFacetDefinitionFactory(), turSNFacetRenderer(),
                                turSNPaginationBuilder(), turSNDocumentResponse(),
                                turSNQueryContextBuilder(),
                                new com.viglet.turing.observability.TurSearchPipelineObservation(null));
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

        /** Build a snapshot tailored to a scenario. */
        private static TurSNSiteSearchSnapshot snapshot(TurSNSite site, Locale locale,
                        List<TurSNSiteFieldExt> enabledFields,
                        Map<String, Set<TurSNSiteFieldExtFacet>> facetLabels,
                        List<TurSNSiteLocale> allLocales) {
                return new TurSNSiteSearchSnapshot(site, locale, "solr", false,
                                enabledFields, Set.of(),
                                Map.of(), facetLabels, allLocales, List.of());
        }

        private static TurSNSite site(String id, String name) {
                TurSNSite s = new TurSNSite();
                s.setId(id);
                s.setName(name);
                return s;
        }

        private static TurSNSiteLocale snLocale(String id, Locale language, int position) {
                TurSNSiteLocale l = new TurSNSiteLocale();
                l.setId(id);
                l.setLanguage(language);
                l.setCore("core");
                l.setPosition(position);
                return l;
        }

        @Test
        void testGetSNSiteReturnsOptional() {
                TurSNSite site = site("id", "site");
                when(snapshotService.getSnapshot("site", null)).thenReturn(Optional.of(
                                snapshot(site, null, List.of(), Map.of(), List.of())));

                assertThat(process(false).getSNSite("site")).contains(site);
        }

        @Test
        void testGetSNSiteReturnsEmptyWhenMissing() {
                when(snapshotService.getSnapshot("site", null)).thenReturn(Optional.empty());

                assertThat(process(false).getSNSite("site")).isEmpty();
        }

        @Test
        void testExistsByTurSNSiteAndLanguage() {
                TurSNSite site = site("site-id", "site");
                when(snapshotService.getSnapshot("site", Locale.US)).thenReturn(Optional.of(
                                snapshot(site, Locale.US, List.of(), Map.of(),
                                                List.of(snLocale("loc-en", Locale.US, 0)))));

                assertThat(process(false).existsByTurSNSiteAndLanguage("site", Locale.US)).isTrue();
        }

        @Test
        void testExistsByTurSNSiteAndLanguageReturnsFalseWhenSiteMissing() {
                when(snapshotService.getSnapshot("site", Locale.US)).thenReturn(Optional.empty());

                assertThat(process(false).existsByTurSNSiteAndLanguage("site", Locale.US)).isFalse();
        }

        @Test
        void testExistsByTurSNSiteAndLanguageReturnsFalseWhenLocaleNotConfigured() {
                TurSNSite site = site("site-id", "site");
                when(snapshotService.getSnapshot("site", Locale.JAPAN)).thenReturn(Optional.of(
                                snapshot(site, Locale.JAPAN, List.of(), Map.of(),
                                                List.of(snLocale("loc-en", Locale.US, 0)))));

                assertThat(process(false).existsByTurSNSiteAndLanguage("site", Locale.JAPAN)).isFalse();
        }

        @Test
        void testLatestSearchesReturnsTerms() {
                TurSNSite site = site("site-id", "site");
                when(snapshotService.getSnapshot("site", null)).thenReturn(Optional.of(
                                snapshot(site, null, List.of(), Map.of(), List.of())));
                TurSNSiteMetricAccessTermDomain term1 = TurSNSiteMetricAccessTermDomain
                                .latest("one", Instant.now());
                TurSNSiteMetricAccessTermDomain term2 = TurSNSiteMetricAccessTermDomain
                                .latest("two", Instant.now());
                when(turSNSiteMetricAccessRepositoryPort.findLatestSearches(eq("site-id"), eq("en"),
                                eq("user"), any(PageRequest.class)))
                                .thenReturn(List.of(term1, term2));

                List<String> results = process(false).latestSearches("site", "en", "user", 10);

                assertThat(results).containsExactly("one", "two");
        }

        @Test
        void testLatestSearchesReturnsEmptyWhenSiteMissing() {
                when(snapshotService.getSnapshot("site", null)).thenReturn(Optional.empty());

                assertThat(process(false).latestSearches("site", "en", "user", 10)).isEmpty();
        }

        @Test
        void testSearchReturnsEmptyWhenPluginReturnsEmpty() {
                TurSearchEnginePlugin plugin = org.mockito.Mockito.mock(TurSearchEnginePlugin.class);
                TurSNSite site = site("site-id", "site");
                when(snapshotService.getSnapshot("site", Locale.US)).thenReturn(Optional.of(
                                snapshot(site, Locale.US, List.of(), Map.of(), List.of())));
                when(searchEnginePluginFactory.getPlugin("solr")).thenReturn(plugin);
                when(plugin.retrieveSearchResults(any())).thenReturn(Optional.empty());

                assertThat(process(false).search(context("java")).getResults()).isNull();
        }

        @Test
        void testSearchListReturnsEmptyWhenPluginReturnsEmpty() {
                TurSearchEnginePlugin plugin = org.mockito.Mockito.mock(TurSearchEnginePlugin.class);
                TurSNSite site = site("site-id", "site");
                when(snapshotService.getSnapshot("site", Locale.US)).thenReturn(Optional.of(
                                snapshot(site, Locale.US, List.of(), Map.of(), List.of())));
                when(searchEnginePluginFactory.getPlugin("solr")).thenReturn(plugin);
                when(plugin.retrieveSearchResults(any())).thenReturn(Optional.empty());

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
                TurSNSite site = site("site-id", "site");
                when(snapshotService.getSnapshot("site", null)).thenReturn(Optional.of(
                                snapshot(site, null, List.of(), Map.of(),
                                                List.of(snLocale("loc-en", Locale.US, 0),
                                                                snLocale("loc-pt", Locale.of("pt", "BR"), 1)))));

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
                TurSNSearchProcess processDisabled = process(false);

                TurSNSearchParams searchParamsWildcard = new TurSNSearchParams();
                searchParamsWildcard.setQ("*");
                TurSNSitePostParamsBean post = new TurSNSitePostParamsBean();
                post.setPopulateMetrics(true);
                TurSNSiteSearchContext contextWildcard = new TurSNSiteSearchContext("site", new TurSNConfig(),
                                new TurSEParameters(searchParamsWildcard, post), Locale.US,
                                URI.create("http://localhost/search?q=*"), post);

                processDisabled.populateMetrics(new TurSNSite(), contextWildcard, 0);

                verify(turSNSiteMetricAccessRepository, never()).save(any());

                TurSNSearchProcess processEnabled = process(true);

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

                TurSNSite site = site("site-id", "site");
                site.setFacet(1);
                site.setFacetType(TurSNSiteFacetFieldEnum.AND);
                site.setFacetItemType(TurSNSiteFacetFieldEnum.OR);
                site.setItemsPerFacet(10);
                site.setSpotlightWithResults(0);

                TurSNSiteFieldExtFacet facetLocale = new TurSNSiteFieldExtFacet();
                facetLocale.setLocale(Locale.US);
                facetLocale.setLabel("Category");

                TurSNSiteFieldExt facetField = TurSNSiteFieldExt.builder()
                                .id("field-id")
                                .name("category")
                                .facetName("Category")
                                .snType(TurSNFieldType.SE)
                                .type(com.viglet.turing.commons.se.field.TurSEFieldType.STRING)
                                .enabled(1)
                                .facet(1)
                                .build();

                Map<String, Set<TurSNSiteFieldExtFacet>> labels = new HashMap<>();
                labels.put("field-id", Set.of(facetLocale));
                when(snapshotService.getSnapshot("site", Locale.US)).thenReturn(Optional.of(
                                snapshot(site, Locale.US, List.of(facetField), labels, List.of())));
                when(searchEnginePluginFactory.getPlugin("solr")).thenReturn(plugin);
                when(turSNSiteFieldExtRepository.findByTurSNSiteAndFacetAndEnabled(site, 1, 1))
                                .thenReturn(List.of(facetField));
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
                TurSNSite site = site("site-id", "site");
                site.setFacet(0);
                site.setSpotlightWithResults(0);

                when(snapshotService.getSnapshot("site", Locale.US)).thenReturn(Optional.of(
                                snapshot(site, Locale.US, List.of(), Map.of(), List.of())));
                when(searchEnginePluginFactory.getPlugin("solr")).thenReturn(plugin);
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
                TurSNSite site = site("site-id", "site");
                when(snapshotService.getSnapshot("site", Locale.US)).thenReturn(Optional.of(
                                snapshot(site, Locale.US, List.of(), Map.of(), List.of())));
                when(searchEnginePluginFactory.getPlugin("solr")).thenReturn(plugin);

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
                TurSNSite site = site("site-id", "site");
                when(snapshotService.getSnapshot("site", Locale.US)).thenReturn(Optional.of(
                                snapshot(site, Locale.US, List.of(), Map.of(), List.of())));
                when(searchEnginePluginFactory.getPlugin("solr")).thenReturn(plugin);

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
                TurSNSite site = site("site-id", "site");
                site.setFacet(0);
                site.setSpotlightWithResults(1);

                when(snapshotService.getSnapshot("site", Locale.US)).thenReturn(Optional.of(
                                snapshot(site, Locale.US, List.of(), Map.of(), List.of())));
                when(searchEnginePluginFactory.getPlugin("solr")).thenReturn(plugin);
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
