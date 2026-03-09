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

package com.viglet.turing.solr;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.response.GroupCommand;
import org.apache.solr.client.solrj.response.GroupResponse;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.SolrDocumentList;
import org.apache.solr.common.util.SimpleOrderedMap;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import com.viglet.turing.commons.se.TurSEParameters;
import com.viglet.turing.commons.se.field.TurSEFieldType;
import com.viglet.turing.commons.sn.bean.TurSNSearchParams;
import com.viglet.turing.persistence.model.sn.TurSNSite;
import com.viglet.turing.persistence.model.sn.field.TurSNSiteCustomFacet;
import com.viglet.turing.persistence.model.sn.field.TurSNSiteCustomFacetItem;
import com.viglet.turing.persistence.model.sn.field.TurSNSiteFieldExt;
import com.viglet.turing.persistence.repository.sn.field.TurSNSiteFieldExtRepository;
import com.viglet.turing.se.facet.TurSEFacetResult;
import com.viglet.turing.se.result.TurSEGroup;
import com.viglet.turing.se.result.TurSEResult;
import com.viglet.turing.se.result.TurSEResults;
import com.viglet.turing.sn.TurSNFieldProcess;

/**
 * Unit tests for TurSolrResultProcessor.
 *
 * @author Alexandre Oliveira
 * @since 2026.1.10
 */
@ExtendWith(MockitoExtension.class)
class TurSolrResultProcessorTest {

        @Mock
        private TurSNFieldProcess turSNFieldProcess;

        @Mock
        private TurSNSiteFieldExtRepository turSNSiteFieldExtRepository;

        @Test
        @SuppressWarnings("unchecked")
        void testSetFacetQueriesOrdersCustomFacetItemsByPosition() throws Exception {
                TurSolrResultProcessor processor = new TurSolrResultProcessor(turSNFieldProcess,
                                turSNSiteFieldExtRepository);
                TurSNSite site = new TurSNSite();

                TurSNSiteCustomFacetItem itemPosition3 = TurSNSiteCustomFacetItem.builder()
                                .label("501+")
                                .position(3)
                                .rangeStart(new BigDecimal("501"))
                                .build();
                TurSNSiteCustomFacetItem itemPosition1 = TurSNSiteCustomFacetItem.builder()
                                .label("0 - 100")
                                .position(1)
                                .rangeStart(BigDecimal.ZERO)
                                .rangeEnd(new BigDecimal("100"))
                                .build();
                TurSNSiteCustomFacetItem itemPosition2 = TurSNSiteCustomFacetItem.builder()
                                .label("101 - 500")
                                .position(2)
                                .rangeStart(new BigDecimal("101"))
                                .rangeEnd(new BigDecimal("500"))
                                .build();

                TurSNSiteCustomFacet customFacet = TurSNSiteCustomFacet.builder()
                                .name("price_range")
                                .items(new HashSet<>(List.of(itemPosition3, itemPosition1, itemPosition2)))
                                .build();

                TurSNSiteFieldExt fieldExt = TurSNSiteFieldExt.builder()
                                .name("id")
                                .customFacets(new HashSet<>(List.of(customFacet)))
                                .build();

                when(turSNSiteFieldExtRepository.findByTurSNSiteAndEnabled(site, 1))
                                .thenReturn(List.of(fieldExt));

                QueryResponse queryResponse = mock(QueryResponse.class);
                Map<String, Integer> facetQuery = new HashMap<>();
                facetQuery.put("price_range::501+", 4);
                facetQuery.put("price_range::0 - 100", 2);
                facetQuery.put("price_range::101 - 500", 3);
                when(queryResponse.getFacetQuery()).thenReturn(facetQuery);

                Method method = TurSolrResultProcessor.class.getDeclaredMethod(
                                "setFacetQueries", TurSNSite.class, QueryResponse.class);
                method.setAccessible(true);
                List<TurSEFacetResult> facetResults = (List<TurSEFacetResult>) method.invoke(processor, site,
                                queryResponse);

                assertThat(facetResults).hasSize(1);
                TurSEFacetResult priceRangeResult = facetResults.getFirst();
                assertThat(priceRangeResult.getFacet()).isEqualTo("price_range");
                assertThat(priceRangeResult.getTurSEFacetResultAttr().keySet())
                                .containsExactly("0 - 100", "101 - 500", "501+");
        }

        @Test
        void testCreateTurSEResultAppliesHighlightAndRequiredFields() {
                TurSolrResultProcessor processor = new TurSolrResultProcessor(turSNFieldProcess,
                                turSNSiteFieldExtRepository);
                TurSNSiteFieldExt titleField = TurSNSiteFieldExt.builder()
                                .name("title")
                                .type(TurSEFieldType.TEXT)
                                .build();

                Map<String, TurSNSiteFieldExt> fieldExtMap = Map.of("title", titleField);
                Map<String, Object> requiredFields = Map.of("type", "doc");
                SolrDocument document = new SolrDocument();
                document.addField("id", "1");
                document.addField("title", "plain");

                Map<String, List<String>> highlight = Map.of("title", List.of("<em>highlight</em>"));

                TurSEResult result = processor.createTurSEResult(fieldExtMap, requiredFields, document,
                                highlight);

                assertThat(result.getFields()).containsEntry("type", "doc");
                assertThat(result.getFields()).containsEntry("title", "<em>highlight</em>");
                assertThat(result.getFields()).containsEntry("id", "1");
        }

        @Test
        void testTurSEResultsParametersFromResults() {
                TurSolrResultProcessor processor = new TurSolrResultProcessor(turSNFieldProcess,
                                turSNSiteFieldExtRepository);

                TurSNSearchParams searchParams = new TurSNSearchParams();
                searchParams.setRows(10);
                searchParams.setP(2);
                searchParams.setSort("relevance");
                TurSEParameters parameters = new TurSEParameters(searchParams);

                SolrQuery query = new SolrQuery();
                query.setQuery("java");

                QueryResponse queryResponse = mock(QueryResponse.class);
                SolrDocumentList docs = new SolrDocumentList();
                docs.setNumFound(25);
                docs.setStart(10);
                when(queryResponse.getResults()).thenReturn(docs);
                when(queryResponse.getElapsedTime()).thenReturn(15L);
                when(queryResponse.getQTime()).thenReturn(6);

                TurSEResults seResults = TurSEResults.builder().build();
                processor.turSEResultsParameters(parameters, query, seResults, queryResponse);

                assertThat(seResults.getNumFound()).isEqualTo(25);
                assertThat(seResults.getStart()).isEqualTo(10);
                assertThat(seResults.getPageCount()).isEqualTo(3);
                assertThat(seResults.getCurrentPage()).isEqualTo(2);
                assertThat(seResults.getQueryString()).isEqualTo("java");
        }

        @Test
        void testTurSEResultsParametersFromGroupResponse() {
                TurSolrResultProcessor processor = new TurSolrResultProcessor(turSNFieldProcess,
                                turSNSiteFieldExtRepository);

                TurSNSearchParams searchParams = new TurSNSearchParams();
                searchParams.setRows(5);
                searchParams.setP(1);
                TurSEParameters parameters = new TurSEParameters(searchParams);
                SolrQuery query = new SolrQuery();
                query.setQuery("grouped");

                QueryResponse queryResponse = mock(QueryResponse.class);
                when(queryResponse.getResults()).thenReturn(null);
                GroupResponse groupResponse = mock(GroupResponse.class);
                GroupCommand g1 = mock(GroupCommand.class);
                GroupCommand g2 = mock(GroupCommand.class);
                when(g1.getMatches()).thenReturn(7);
                when(g2.getMatches()).thenReturn(3);
                when(groupResponse.getValues()).thenReturn(List.of(g1, g2));
                when(queryResponse.getGroupResponse()).thenReturn(groupResponse);
                when(queryResponse.getElapsedTime()).thenReturn(20L);
                when(queryResponse.getQTime()).thenReturn(9);

                TurSEResults seResults = TurSEResults.builder().build();
                processor.turSEResultsParameters(parameters, query, seResults, queryResponse);

                assertThat(seResults.getNumFound()).isEqualTo(10);
                assertThat(seResults.getPageCount()).isEqualTo(2);
        }

        @Test
        void testGetFieldExtMapAndRequiredFields() {
                TurSolrResultProcessor processor = new TurSolrResultProcessor(turSNFieldProcess,
                                turSNSiteFieldExtRepository);
                TurSNSite site = new TurSNSite();

                TurSNSiteFieldExt f1 = TurSNSiteFieldExt.builder().name("title").required(0).build();
                TurSNSiteFieldExt f2 = TurSNSiteFieldExt.builder().name("title").required(1)
                                .defaultValue("default-title").build();
                TurSNSiteFieldExt f3 = TurSNSiteFieldExt.builder().name("type").required(1)
                                .defaultValue("doc").build();

                when(turSNSiteFieldExtRepository.findByTurSNSiteAndEnabled(site, 1))
                                .thenReturn(List.of(f1, f2, f3));
                when(turSNSiteFieldExtRepository.findByTurSNSiteAndRequiredAndEnabled(site, 1, 1))
                                .thenReturn(List.of(f2, f3));

                Map<String, TurSNSiteFieldExt> fieldMap = processor.getFieldExtMap(site);
                Map<String, Object> requiredMap = processor.getRequiredFields(site);

                assertThat(fieldMap).containsKeys("title", "type");
                assertThat(requiredMap)
                                .containsEntry("title", "default-title")
                                .containsEntry("type", "doc");
        }

        @Test
        void testGetHLAndIsHLBranches() {
                TurSolrResultProcessor processor = new TurSolrResultProcessor(turSNFieldProcess,
                                turSNSiteFieldExtRepository);

                TurSNSite site = new TurSNSite();
                site.setHl(1);
                TurSNSiteFieldExt hlField = TurSNSiteFieldExt.builder().name("title").build();

                QueryResponse response = mock(QueryResponse.class);
                Map<String, Map<String, List<String>>> highlightMap = new HashMap<>();
                highlightMap.put("1", Map.of("title", List.of("<em>Title</em>")));
                when(response.getHighlighting()).thenReturn(highlightMap);

                SolrDocument document = new SolrDocument();
                document.addField("id", "1");

                Map<String, List<String>> hl = processor.getHL(site, List.of(hlField), response, document);
                assertThat(hl).containsKey("title");
                assertThat(TurSolrResultProcessor.isHL(site, List.of(hlField))).isTrue();

                site.setHl(0);
                assertThat(TurSolrResultProcessor.isHL(site, List.of(hlField))).isFalse();
                assertThat(processor.getHL(site, List.of(hlField), response, document)).isNull();
                assertThat(processor.getHL(site, Collections.emptyList(), response, document)).isNull();
        }

        @Test
        void testGetResultsProcessesMltAndGroups() {
                TurSolrResultProcessor processor = new TurSolrResultProcessor(turSNFieldProcess,
                                turSNSiteFieldExtRepository);
                TurSNSite site = new TurSNSite();
                site.setMlt(1);
                site.setFacet(0);

                TurSNSiteFieldExt idField = TurSNSiteFieldExt.builder().name("id").build();
                when(turSNSiteFieldExtRepository.findByTurSNSiteAndEnabled(site, 1))
                                .thenReturn(List.of(idField));
                when(turSNSiteFieldExtRepository.findByTurSNSiteAndRequiredAndEnabled(site, 1, 1))
                                .thenReturn(Collections.emptyList());

                SolrDocument doc = new SolrDocument();
                doc.addField("id", "1");
                SolrDocumentList results = new SolrDocumentList();
                results.add(doc);
                results.setNumFound(1);

                SolrDocument similarDoc = new SolrDocument();
                similarDoc.addField("id", "S1");
                similarDoc.addField("title", "Similar");
                similarDoc.addField("type", "doc");
                similarDoc.addField("url", "/doc");
                SolrDocumentList similarDocs = new SolrDocumentList();
                similarDocs.add(similarDoc);

                SimpleOrderedMap<Object> mltMap = new SimpleOrderedMap<>();
                mltMap.add("1", similarDocs);
                SimpleOrderedMap<Object> responseData = new SimpleOrderedMap<>();
                responseData.add("moreLikeThis", mltMap);

                QueryResponse queryResponse = mock(QueryResponse.class);
                when(queryResponse.getResults()).thenReturn(results);
                when(queryResponse.getResponse()).thenReturn(responseData);
                when(queryResponse.getElapsedTime()).thenReturn(10L);
                when(queryResponse.getQTime()).thenReturn(3);

                TurSNSearchParams searchParams = new TurSNSearchParams();
                searchParams.setQ("java");
                searchParams.setRows(10);
                searchParams.setP(1);
                TurSEParameters parameters = new TurSEParameters(searchParams);
                SolrQuery query = new SolrQuery().setQuery("java");

                TurSolrQueryContext queryContext = TurSolrQueryContext.builder()
                                .query(query)
                                .turSEParameters(parameters)
                                .mltFieldExtList(List.of(TurSNSiteFieldExt.builder().name("body").build()))
                                .facetFieldExtList(Collections.emptyList())
                                .hlFieldExtList(Collections.emptyList())
                                .spellCheckResult(null)
                                .queryToRenderFacet(false)
                                .build();

                TurSolrQueryBuilder queryBuilder = mock(TurSolrQueryBuilder.class);
                when(queryBuilder.hasGroup(parameters)).thenReturn(false);

                TurSEResults seResults = processor.getResults(mock(TurSolrInstance.class), site,
                                query, queryContext, queryResponse, queryBuilder);

                assertThat(seResults.getResults()).hasSize(1);
                assertThat(seResults.getSimilarResults()).hasSize(1);
                assertThat(seResults.getGroups()).isNull();
        }

        @Test
        void testGetResultsEnrichesGroupsWithWildcardResponse() {
                TurSolrResultProcessor processor = new TurSolrResultProcessor(turSNFieldProcess,
                                turSNSiteFieldExtRepository);
                TurSNSite site = new TurSNSite();
                site.setMlt(0);
                site.setFacet(0);
                site.setWildcardNoResults(1);

                when(turSNSiteFieldExtRepository.findByTurSNSiteAndEnabled(site, 1))
                                .thenReturn(Collections.emptyList());
                when(turSNSiteFieldExtRepository.findByTurSNSiteAndRequiredAndEnabled(site, 1, 1))
                                .thenReturn(Collections.emptyList());

                SolrDocumentList emptyResults = new SolrDocumentList();
                emptyResults.setNumFound(0);
                SolrDocumentList wildcardResults = new SolrDocumentList();
                SolrDocument wildcardDoc = new SolrDocument();
                wildcardDoc.addField("id", "2");
                wildcardResults.add(wildcardDoc);
                wildcardResults.setNumFound(1);

                org.apache.solr.client.solrj.response.Group originalGroup = mock(
                                org.apache.solr.client.solrj.response.Group.class);
                when(originalGroup.getGroupValue()).thenReturn("g1");
                when(originalGroup.getResult()).thenReturn(emptyResults);

                GroupCommand originalCommand = mock(GroupCommand.class);
                when(originalCommand.getValues()).thenReturn(List.of(originalGroup));
                GroupResponse originalGroupResponse = mock(GroupResponse.class);
                when(originalGroupResponse.getValues()).thenReturn(List.of(originalCommand));

                QueryResponse originalResponse = mock(QueryResponse.class);
                when(originalResponse.getResults()).thenReturn(emptyResults);
                when(originalResponse.getGroupResponse()).thenReturn(originalGroupResponse);
                when(originalResponse.getElapsedTime()).thenReturn(5L);
                when(originalResponse.getQTime()).thenReturn(2);

                org.apache.solr.client.solrj.response.Group wildcardGroup = mock(
                                org.apache.solr.client.solrj.response.Group.class);
                when(wildcardGroup.getGroupValue()).thenReturn("g1");
                when(wildcardGroup.getResult()).thenReturn(wildcardResults);
                GroupCommand wildcardCommand = mock(GroupCommand.class);
                when(wildcardCommand.getValues()).thenReturn(List.of(wildcardGroup));
                GroupResponse wildcardGroupResponse = mock(GroupResponse.class);
                when(wildcardGroupResponse.getValues()).thenReturn(List.of(wildcardCommand));
                QueryResponse wildcardResponse = mock(QueryResponse.class);
                when(wildcardResponse.getGroupResponse()).thenReturn(wildcardGroupResponse);

                TurSNSearchParams searchParams = new TurSNSearchParams();
                searchParams.setQ("java");
                searchParams.setRows(10);
                searchParams.setP(1);
                TurSEParameters parameters = new TurSEParameters(searchParams);
                SolrQuery query = new SolrQuery().setQuery("java");
                TurSolrQueryContext queryContext = TurSolrQueryContext.builder()
                                .query(query)
                                .turSEParameters(parameters)
                                .mltFieldExtList(Collections.emptyList())
                                .facetFieldExtList(Collections.emptyList())
                                .hlFieldExtList(Collections.emptyList())
                                .spellCheckResult(null)
                                .queryToRenderFacet(false)
                                .build();

                TurSolrQueryBuilder queryBuilder = mock(TurSolrQueryBuilder.class);
                when(queryBuilder.hasGroup(parameters)).thenReturn(true);

                try (MockedStatic<TurSolr> mockedTurSolr = mockStatic(TurSolr.class)) {
                        mockedTurSolr.when(() -> TurSolr.enabledWildcardNoResults(site)).thenReturn(true);
                        mockedTurSolr.when(() -> TurSolr.isNotQueryExpression(query)).thenReturn(true);
                        mockedTurSolr.when(() -> TurSolr.addAWildcardInQuery(any(SolrQuery.class)))
                                        .thenAnswer(invocation -> null);
                        mockedTurSolr.when(() -> TurSolr.executeSolrQuery(any(TurSolrInstance.class),
                                        any(SolrQuery.class)))
                                        .thenReturn(Optional.of(wildcardResponse));

                        TurSEResults seResults = processor.getResults(mock(TurSolrInstance.class), site,
                                        query, queryContext, originalResponse, queryBuilder);

                        assertThat(seResults.getGroups()).hasSize(1);
                        TurSEGroup group = seResults.getGroups().getFirst();
                        assertThat(group.getName()).isEqualTo("g1");
                        assertThat(group.getResults()).hasSize(1);
                }
        }
}
