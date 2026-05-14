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

import org.apache.solr.client.solrj.request.SolrQuery;
import org.apache.solr.client.solrj.response.FacetField;
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
 * Tests for TurSolrResultProcessor.
 *
 * @author Alexandre Oliveira
 * @since 2026.1.14
 */
@ExtendWith(MockitoExtension.class)
class TurSolrResultProcessorTest {

        @Mock
        private TurSNFieldProcess turSNFieldProcess;

        @Mock
        private TurSNSiteFieldExtRepository turSNSiteFieldExtRepository;

        @Mock
        private com.viglet.turing.persistence.repository.sn.field.TurSNSiteCustomFacetRepository turSNSiteCustomFacetRepository;

        @Test
        @SuppressWarnings("unchecked")
        void testSetFacetQueriesOrdersCustomFacetItemsByPosition() throws Exception {
                TurSolrResultProcessor processor = new TurSolrResultProcessor(turSNFieldProcess,
                                turSNSiteFieldExtRepository, turSNSiteCustomFacetRepository,
                                new com.viglet.turing.observability.TurSearchPipelineObservation(null));
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
                                "setFacetQueries", TurSNSite.class, java.util.Locale.class,
                                QueryResponse.class);
                method.setAccessible(true);
                List<TurSEFacetResult> facetResults = (List<TurSEFacetResult>) method.invoke(processor, site,
                                null, queryResponse);

                assertThat(facetResults).hasSize(1);
                TurSEFacetResult priceRangeResult = facetResults.getFirst();
                assertThat(priceRangeResult.getFacet()).isEqualTo("price_range");
                assertThat(priceRangeResult.getTurSEFacetResultAttr().keySet())
                                .containsExactly("0 - 100", "101 - 500", "501+");
        }

        @Test
        void testCreateTurSEResultAppliesHighlightAndRequiredFields() {
                TurSolrResultProcessor processor = new TurSolrResultProcessor(turSNFieldProcess,
                                turSNSiteFieldExtRepository, turSNSiteCustomFacetRepository,
                                new com.viglet.turing.observability.TurSearchPipelineObservation(null));
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
                                turSNSiteFieldExtRepository, turSNSiteCustomFacetRepository,
                                new com.viglet.turing.observability.TurSearchPipelineObservation(null));

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
                                turSNSiteFieldExtRepository, turSNSiteCustomFacetRepository,
                                new com.viglet.turing.observability.TurSearchPipelineObservation(null));

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
                                turSNSiteFieldExtRepository, turSNSiteCustomFacetRepository,
                                new com.viglet.turing.observability.TurSearchPipelineObservation(null));
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
                                turSNSiteFieldExtRepository, turSNSiteCustomFacetRepository,
                                new com.viglet.turing.observability.TurSearchPipelineObservation(null));

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
                                turSNSiteFieldExtRepository, turSNSiteCustomFacetRepository,
                                new com.viglet.turing.observability.TurSearchPipelineObservation(null));
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
        void testCreateTurSEResultWithNullHighlightDoesNotFail() {
                TurSolrResultProcessor processor = new TurSolrResultProcessor(turSNFieldProcess,
                                turSNSiteFieldExtRepository, turSNSiteCustomFacetRepository,
                                new com.viglet.turing.observability.TurSearchPipelineObservation(null));
                TurSNSiteFieldExt titleField = TurSNSiteFieldExt.builder()
                                .name("title")
                                .type(TurSEFieldType.TEXT)
                                .build();

                Map<String, TurSNSiteFieldExt> fieldExtMap = Map.of("title", titleField);
                Map<String, Object> requiredFields = Collections.emptyMap();
                SolrDocument document = new SolrDocument();
                document.addField("id", "1");
                document.addField("title", "plain");

                TurSEResult result = processor.createTurSEResult(fieldExtMap, requiredFields, document, null);

                assertThat(result.getFields()).containsEntry("title", "plain");
                assertThat(result.getFields()).containsEntry("id", "1");
        }

        @Test
        void testCreateTurSEResultWithEmptyHighlightDoesNotReplace() {
                TurSolrResultProcessor processor = new TurSolrResultProcessor(turSNFieldProcess,
                                turSNSiteFieldExtRepository, turSNSiteCustomFacetRepository,
                                new com.viglet.turing.observability.TurSearchPipelineObservation(null));
                TurSNSiteFieldExt titleField = TurSNSiteFieldExt.builder()
                                .name("title")
                                .type(TurSEFieldType.TEXT)
                                .build();

                Map<String, TurSNSiteFieldExt> fieldExtMap = Map.of("title", titleField);
                SolrDocument document = new SolrDocument();
                document.addField("id", "1");
                document.addField("title", "plain");

                Map<String, List<String>> hl = Collections.emptyMap();

                TurSEResult result = processor.createTurSEResult(fieldExtMap, Collections.emptyMap(), document, hl);

                assertThat(result.getFields()).containsEntry("title", "plain");
        }

        @Test
        void testCreateTurSEResultDoesNotHighlightNonTextFields() {
                TurSolrResultProcessor processor = new TurSolrResultProcessor(turSNFieldProcess,
                                turSNSiteFieldExtRepository, turSNSiteCustomFacetRepository,
                                new com.viglet.turing.observability.TurSearchPipelineObservation(null));
                TurSNSiteFieldExt intField = TurSNSiteFieldExt.builder()
                                .name("count")
                                .type(TurSEFieldType.INT)
                                .build();

                Map<String, TurSNSiteFieldExt> fieldExtMap = Map.of("count", intField);
                SolrDocument document = new SolrDocument();
                document.addField("count", 42);

                Map<String, List<String>> hl = Map.of("count", List.of("<em>42</em>"));

                TurSEResult result = processor.createTurSEResult(fieldExtMap, Collections.emptyMap(), document, hl);

                // INT field should not be highlighted
                assertThat(result.getFields()).containsEntry("count", 42);
        }

        @Test
        void testIsHLReturnsFalseForNullHl() {
                TurSNSite site = new TurSNSite();
                site.setHl(null);
                assertThat(TurSolrResultProcessor.isHL(site, List.of(
                                TurSNSiteFieldExt.builder().name("title").build()))).isFalse();
        }

        @Test
        void testIsHLReturnsFalseForZeroHl() {
                TurSNSite site = new TurSNSite();
                site.setHl(0);
                assertThat(TurSolrResultProcessor.isHL(site, List.of(
                                TurSNSiteFieldExt.builder().name("title").build()))).isFalse();
        }

        @Test
        void testIsHLReturnsFalseForEmptyFieldList() {
                TurSNSite site = new TurSNSite();
                site.setHl(1);
                assertThat(TurSolrResultProcessor.isHL(site, Collections.emptyList())).isFalse();
        }

        @Test
        void testIsHLReturnsTrueWhenEnabledAndFieldsPresent() {
                TurSNSite site = new TurSNSite();
                site.setHl(1);
                assertThat(TurSolrResultProcessor.isHL(site, List.of(
                                TurSNSiteFieldExt.builder().name("title").build()))).isTrue();
        }

        @Test
        void testTurSEResultsParametersWithZeroResults() {
                TurSolrResultProcessor processor = new TurSolrResultProcessor(turSNFieldProcess,
                                turSNSiteFieldExtRepository, turSNSiteCustomFacetRepository,
                                new com.viglet.turing.observability.TurSearchPipelineObservation(null));

                TurSNSearchParams searchParams = new TurSNSearchParams();
                searchParams.setRows(10);
                searchParams.setP(1);
                TurSEParameters parameters = new TurSEParameters(searchParams);

                SolrQuery query = new SolrQuery();
                query.setQuery("nonexistent");

                QueryResponse queryResponse = mock(QueryResponse.class);
                SolrDocumentList docs = new SolrDocumentList();
                docs.setNumFound(0);
                docs.setStart(0);
                when(queryResponse.getResults()).thenReturn(docs);
                when(queryResponse.getElapsedTime()).thenReturn(1L);
                when(queryResponse.getQTime()).thenReturn(0);

                TurSEResults seResults = TurSEResults.builder().build();
                processor.turSEResultsParameters(parameters, query, seResults, queryResponse);

                assertThat(seResults.getNumFound()).isZero();
                assertThat(seResults.getPageCount()).isZero();
        }

        @Test
        void testGetFieldExtMapHandlesDuplicateFieldNames() {
                TurSolrResultProcessor processor = new TurSolrResultProcessor(turSNFieldProcess,
                                turSNSiteFieldExtRepository, turSNSiteCustomFacetRepository,
                                new com.viglet.turing.observability.TurSearchPipelineObservation(null));
                TurSNSite site = new TurSNSite();

                TurSNSiteFieldExt f1 = TurSNSiteFieldExt.builder().name("title").type(TurSEFieldType.TEXT).build();
                TurSNSiteFieldExt f2 = TurSNSiteFieldExt.builder().name("title").type(TurSEFieldType.STRING).build();

                when(turSNSiteFieldExtRepository.findByTurSNSiteAndEnabled(site, 1))
                                .thenReturn(List.of(f1, f2));

                Map<String, TurSNSiteFieldExt> fieldMap = processor.getFieldExtMap(site);

                // Last one wins due to (a, b) -> b merge function
                assertThat(fieldMap).hasSize(1);
                assertThat(fieldMap.get("title").getType()).isEqualTo(TurSEFieldType.STRING);
        }

        @Test
        void testGetRequiredFieldsReturnsEmptyMapWhenNoneRequired() {
                TurSolrResultProcessor processor = new TurSolrResultProcessor(turSNFieldProcess,
                                turSNSiteFieldExtRepository, turSNSiteCustomFacetRepository,
                                new com.viglet.turing.observability.TurSearchPipelineObservation(null));
                TurSNSite site = new TurSNSite();

                when(turSNSiteFieldExtRepository.findByTurSNSiteAndRequiredAndEnabled(site, 1, 1))
                                .thenReturn(Collections.emptyList());

                Map<String, Object> requiredFields = processor.getRequiredFields(site);
                assertThat(requiredFields).isEmpty();
        }

        @Test
        void testGetHLReturnsNullWhenHighlightingMapIsNull() {
                TurSolrResultProcessor processor = new TurSolrResultProcessor(turSNFieldProcess,
                                turSNSiteFieldExtRepository, turSNSiteCustomFacetRepository,
                                new com.viglet.turing.observability.TurSearchPipelineObservation(null));

                TurSNSite site = new TurSNSite();
                site.setHl(1);
                TurSNSiteFieldExt hlField = TurSNSiteFieldExt.builder().name("title").build();

                QueryResponse response = mock(QueryResponse.class);
                when(response.getHighlighting()).thenReturn(null);

                SolrDocument document = new SolrDocument();
                document.addField("id", "1");

                Map<String, List<String>> hl = processor.getHL(site, List.of(hlField), response, document);
                assertThat(hl).isNull();
        }

        @Test
        void testCreateTurSEResultHighlightsStringField() {
                TurSolrResultProcessor processor = new TurSolrResultProcessor(turSNFieldProcess,
                                turSNSiteFieldExtRepository, turSNSiteCustomFacetRepository,
                                new com.viglet.turing.observability.TurSearchPipelineObservation(null));
                TurSNSiteFieldExt nameField = TurSNSiteFieldExt.builder()
                                .name("name")
                                .type(TurSEFieldType.STRING)
                                .build();

                Map<String, TurSNSiteFieldExt> fieldExtMap = Map.of("name", nameField);
                SolrDocument document = new SolrDocument();
                document.addField("name", "plain name");

                Map<String, List<String>> hl = Map.of("name", List.of("<b>highlighted</b>"));

                TurSEResult result = processor.createTurSEResult(fieldExtMap, Collections.emptyMap(), document, hl);
                assertThat(result.getFields()).containsEntry("name", "<b>highlighted</b>");
        }

        @Test
        void testCreateTurSEResultDoesNotHighlightBoolField() {
                TurSolrResultProcessor processor = new TurSolrResultProcessor(turSNFieldProcess,
                                turSNSiteFieldExtRepository, turSNSiteCustomFacetRepository,
                                new com.viglet.turing.observability.TurSearchPipelineObservation(null));
                TurSNSiteFieldExt boolField = TurSNSiteFieldExt.builder()
                                .name("active")
                                .type(TurSEFieldType.BOOL)
                                .build();

                Map<String, TurSNSiteFieldExt> fieldExtMap = Map.of("active", boolField);
                SolrDocument document = new SolrDocument();
                document.addField("active", true);

                Map<String, List<String>> hl = Map.of("active", List.of("<em>true</em>"));
                TurSEResult result = processor.createTurSEResult(fieldExtMap, Collections.emptyMap(), document, hl);
                assertThat(result.getFields()).containsEntry("active", true);
        }

        @Test
        void testCreateTurSEResultDoesNotHighlightDateField() {
                TurSolrResultProcessor processor = new TurSolrResultProcessor(turSNFieldProcess,
                                turSNSiteFieldExtRepository, turSNSiteCustomFacetRepository,
                                new com.viglet.turing.observability.TurSearchPipelineObservation(null));
                TurSNSiteFieldExt dateField = TurSNSiteFieldExt.builder()
                                .name("published")
                                .type(TurSEFieldType.DATE)
                                .build();

                Map<String, TurSNSiteFieldExt> fieldExtMap = Map.of("published", dateField);
                SolrDocument document = new SolrDocument();
                document.addField("published", "2026-01-01");

                Map<String, List<String>> hl = Map.of("published", List.of("<em>2026-01-01</em>"));
                TurSEResult result = processor.createTurSEResult(fieldExtMap, Collections.emptyMap(), document, hl);
                assertThat(result.getFields()).containsEntry("published", "2026-01-01");
        }

        @Test
        void testCreateTurSEResultAddsMultipleRequiredFields() {
                TurSolrResultProcessor processor = new TurSolrResultProcessor(turSNFieldProcess,
                                turSNSiteFieldExtRepository, turSNSiteCustomFacetRepository,
                                new com.viglet.turing.observability.TurSearchPipelineObservation(null));

                Map<String, TurSNSiteFieldExt> fieldExtMap = Collections.emptyMap();
                Map<String, Object> requiredFields = new HashMap<>();
                requiredFields.put("type", "article");
                requiredFields.put("source", "web");

                SolrDocument document = new SolrDocument();
                document.addField("id", "1");

                TurSEResult result = processor.createTurSEResult(fieldExtMap, requiredFields, document, null);
                assertThat(result.getFields()).containsEntry("type", "article");
                assertThat(result.getFields()).containsEntry("source", "web");
                assertThat(result.getFields()).containsEntry("id", "1");
        }

        @Test
        void testCreateTurSEResultDoesNotOverrideExistingWithRequired() {
                TurSolrResultProcessor processor = new TurSolrResultProcessor(turSNFieldProcess,
                                turSNSiteFieldExtRepository, turSNSiteCustomFacetRepository,
                                new com.viglet.turing.observability.TurSearchPipelineObservation(null));

                Map<String, Object> requiredFields = Map.of("type", "default-type");
                SolrDocument document = new SolrDocument();
                document.addField("type", "actual-type");

                TurSEResult result = processor.createTurSEResult(Collections.emptyMap(), requiredFields, document, null);
                assertThat(result.getFields()).containsEntry("type", "actual-type");
        }

        @Test
        void testTurSEResultsParametersWithNullResultsAndNullGroups() {
                TurSolrResultProcessor processor = new TurSolrResultProcessor(turSNFieldProcess,
                                turSNSiteFieldExtRepository, turSNSiteCustomFacetRepository,
                                new com.viglet.turing.observability.TurSearchPipelineObservation(null));

                TurSNSearchParams searchParams = new TurSNSearchParams();
                searchParams.setRows(10);
                searchParams.setP(1);
                TurSEParameters parameters = new TurSEParameters(searchParams);

                SolrQuery query = new SolrQuery().setQuery("test");
                QueryResponse queryResponse = mock(QueryResponse.class);
                when(queryResponse.getResults()).thenReturn(null);
                when(queryResponse.getGroupResponse()).thenReturn(null);
                when(queryResponse.getElapsedTime()).thenReturn(5L);
                when(queryResponse.getQTime()).thenReturn(2);

                TurSEResults seResults = TurSEResults.builder().build();
                processor.turSEResultsParameters(parameters, query, seResults, queryResponse);

                // numFound should remain default (0) since both results and group are null
                assertThat(seResults.getElapsedTime()).isEqualTo(5);
                assertThat(seResults.getQTime()).isEqualTo(2);
                assertThat(seResults.getQueryString()).isEqualTo("test");
        }

        @Test
        void testGetResultsWithNoMltAndNoFacet() {
                TurSolrResultProcessor processor = new TurSolrResultProcessor(turSNFieldProcess,
                                turSNSiteFieldExtRepository, turSNSiteCustomFacetRepository,
                                new com.viglet.turing.observability.TurSearchPipelineObservation(null));
                TurSNSite site = new TurSNSite();
                site.setMlt(0);
                site.setFacet(0);

                when(turSNSiteFieldExtRepository.findByTurSNSiteAndEnabled(site, 1))
                                .thenReturn(Collections.emptyList());
                when(turSNSiteFieldExtRepository.findByTurSNSiteAndRequiredAndEnabled(site, 1, 1))
                                .thenReturn(Collections.emptyList());

                SolrDocumentList docs = new SolrDocumentList();
                SolrDocument doc = new SolrDocument();
                doc.addField("id", "1");
                docs.add(doc);
                docs.setNumFound(1);

                QueryResponse queryResponse = mock(QueryResponse.class);
                when(queryResponse.getResults()).thenReturn(docs);
                when(queryResponse.getElapsedTime()).thenReturn(10L);
                when(queryResponse.getQTime()).thenReturn(3);

                TurSNSearchParams searchParams = new TurSNSearchParams();
                searchParams.setQ("test");
                searchParams.setRows(10);
                searchParams.setP(1);
                TurSEParameters parameters = new TurSEParameters(searchParams);
                SolrQuery query = new SolrQuery().setQuery("test");

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
                when(queryBuilder.hasGroup(parameters)).thenReturn(false);

                TurSEResults seResults = processor.getResults(mock(TurSolrInstance.class), site,
                                query, queryContext, queryResponse, queryBuilder);

                assertThat(seResults.getResults()).hasSize(1);
                assertThat(seResults.getSimilarResults()).isNull();
                assertThat(seResults.getGroups()).isNull();
        }

        @Test
        @SuppressWarnings("unchecked")
        void testSetFacetFieldsExcludesRangeOverlaps() throws Exception {
                Method method = TurSolrResultProcessor.class.getDeclaredMethod(
                                "setFacetFields", QueryResponse.class, List.class);
                method.setAccessible(true);

                QueryResponse queryResponse = mock(QueryResponse.class);
                FacetField categoryFacet = new FacetField("category");
                categoryFacet.add("books", 10);
                categoryFacet.add("movies", 5);
                FacetField dateFacet = new FacetField("publishDate");
                dateFacet.add("2024-01-01", 3);
                when(queryResponse.getFacetFields()).thenReturn(List.of(categoryFacet, dateFacet));

                TurSEFacetResult rangeResult = new TurSEFacetResult();
                rangeResult.setFacet("publishDate");
                List<TurSEFacetResult> rangeList = List.of(rangeResult);

                List<TurSEFacetResult> facetResults = (List<TurSEFacetResult>) method.invoke(null,
                                queryResponse, rangeList);

                assertThat(facetResults).hasSize(1);
                assertThat(facetResults.getFirst().getFacet()).isEqualTo("category");
                assertThat(facetResults.getFirst().getTurSEFacetResultAttr()).containsKey("books");
                assertThat(facetResults.getFirst().getTurSEFacetResultAttr()).containsKey("movies");
        }

        @Test
        @SuppressWarnings("unchecked")
        void testSetFacetFieldsIncludesAllWhenNoRangeOverlap() throws Exception {
                Method method = TurSolrResultProcessor.class.getDeclaredMethod(
                                "setFacetFields", QueryResponse.class, List.class);
                method.setAccessible(true);

                QueryResponse queryResponse = mock(QueryResponse.class);
                FacetField field1 = new FacetField("type");
                field1.add("article", 7);
                FacetField field2 = new FacetField("author");
                field2.add("john", 3);
                when(queryResponse.getFacetFields()).thenReturn(List.of(field1, field2));

                List<TurSEFacetResult> facetResults = (List<TurSEFacetResult>) method.invoke(null,
                                queryResponse, Collections.emptyList());

                assertThat(facetResults).hasSize(2);
        }

        @Test
        void testWasFacetConfiguredReturnsFalseWhenFacetDisabled() throws Exception {
                Method method = TurSolrResultProcessor.class.getDeclaredMethod(
                                "wasFacetConfigured", TurSNSite.class, List.class);
                method.setAccessible(true);

                TurSNSite site = new TurSNSite();
                site.setFacet(0);
                site.setItemsPerFacet(10);
                TurSNSiteFieldExt field = TurSNSiteFieldExt.builder().name("category").build();

                boolean result = (boolean) method.invoke(
                                new TurSolrResultProcessor(turSNFieldProcess,
                                                turSNSiteFieldExtRepository, turSNSiteCustomFacetRepository,
                                                new com.viglet.turing.observability.TurSearchPipelineObservation(null)),
                                site, List.of(field));

                assertThat(result).isFalse();
        }

        @Test
        void testWasFacetConfiguredReturnsFalseWhenItemsPerFacetNull() throws Exception {
                Method method = TurSolrResultProcessor.class.getDeclaredMethod(
                                "wasFacetConfigured", TurSNSite.class, List.class);
                method.setAccessible(true);

                TurSNSite site = new TurSNSite();
                site.setFacet(1);
                site.setItemsPerFacet(null);
                TurSNSiteFieldExt field = TurSNSiteFieldExt.builder().name("category").build();

                boolean result = (boolean) method.invoke(
                                new TurSolrResultProcessor(turSNFieldProcess,
                                                turSNSiteFieldExtRepository, turSNSiteCustomFacetRepository,
                                                new com.viglet.turing.observability.TurSearchPipelineObservation(null)),
                                site, List.of(field));

                assertThat(result).isFalse();
        }

        @Test
        void testWasFacetConfiguredReturnsFalseWhenFieldListEmpty() throws Exception {
                Method method = TurSolrResultProcessor.class.getDeclaredMethod(
                                "wasFacetConfigured", TurSNSite.class, List.class);
                method.setAccessible(true);

                TurSNSite site = new TurSNSite();
                site.setFacet(1);
                site.setItemsPerFacet(10);

                boolean result = (boolean) method.invoke(
                                new TurSolrResultProcessor(turSNFieldProcess,
                                                turSNSiteFieldExtRepository, turSNSiteCustomFacetRepository,
                                                new com.viglet.turing.observability.TurSearchPipelineObservation(null)),
                                site, Collections.emptyList());

                assertThat(result).isFalse();
        }

        @Test
        void testWasFacetConfiguredReturnsTrueWhenAllConditionsMet() throws Exception {
                Method method = TurSolrResultProcessor.class.getDeclaredMethod(
                                "wasFacetConfigured", TurSNSite.class, List.class);
                method.setAccessible(true);

                TurSNSite site = new TurSNSite();
                site.setFacet(1);
                site.setItemsPerFacet(10);
                TurSNSiteFieldExt field = TurSNSiteFieldExt.builder().name("category").build();

                boolean result = (boolean) method.invoke(
                                new TurSolrResultProcessor(turSNFieldProcess,
                                                turSNSiteFieldExtRepository, turSNSiteCustomFacetRepository,
                                                new com.viglet.turing.observability.TurSearchPipelineObservation(null)),
                                site, List.of(field));

                assertThat(result).isTrue();
        }

        @Test
        void testGetNumberOfPagesCalculation() throws Exception {
                Method method = TurSolrResultProcessor.class.getDeclaredMethod(
                                "getNumberOfPages", long.class, int.class);
                method.setAccessible(true);

                TurSolrResultProcessor processor = new TurSolrResultProcessor(turSNFieldProcess,
                                turSNSiteFieldExtRepository, turSNSiteCustomFacetRepository,
                                new com.viglet.turing.observability.TurSearchPipelineObservation(null));

                assertThat((int) method.invoke(processor, 0L, 10)).isZero();
                assertThat((int) method.invoke(processor, 1L, 10)).isEqualTo(1);
                assertThat((int) method.invoke(processor, 10L, 10)).isEqualTo(1);
                assertThat((int) method.invoke(processor, 11L, 10)).isEqualTo(2);
                assertThat((int) method.invoke(processor, 100L, 10)).isEqualTo(10);
                assertThat((int) method.invoke(processor, 99L, 10)).isEqualTo(10);
                assertThat((int) method.invoke(processor, 101L, 10)).isEqualTo(11);
        }

        @Test
        @SuppressWarnings("unchecked")
        void testSetFacetQueriesReturnsEmptyWhenFacetQueryIsNull() throws Exception {
                TurSolrResultProcessor processor = new TurSolrResultProcessor(turSNFieldProcess,
                                turSNSiteFieldExtRepository, turSNSiteCustomFacetRepository,
                                new com.viglet.turing.observability.TurSearchPipelineObservation(null));
                TurSNSite site = new TurSNSite();

                QueryResponse queryResponse = mock(QueryResponse.class);
                when(queryResponse.getFacetQuery()).thenReturn(null);

                Method method = TurSolrResultProcessor.class.getDeclaredMethod(
                                "setFacetQueries", TurSNSite.class, java.util.Locale.class,
                                QueryResponse.class);
                method.setAccessible(true);
                List<TurSEFacetResult> results = (List<TurSEFacetResult>) method.invoke(processor,
                                site, null, queryResponse);

                assertThat(results).isEmpty();
        }

        @Test
        @SuppressWarnings("unchecked")
        void testSetFacetQueriesReturnsEmptyWhenFacetQueryMapIsEmpty() throws Exception {
                TurSolrResultProcessor processor = new TurSolrResultProcessor(turSNFieldProcess,
                                turSNSiteFieldExtRepository, turSNSiteCustomFacetRepository,
                                new com.viglet.turing.observability.TurSearchPipelineObservation(null));
                TurSNSite site = new TurSNSite();

                QueryResponse queryResponse = mock(QueryResponse.class);
                when(queryResponse.getFacetQuery()).thenReturn(Collections.emptyMap());

                Method method = TurSolrResultProcessor.class.getDeclaredMethod(
                                "setFacetQueries", TurSNSite.class, java.util.Locale.class,
                                QueryResponse.class);
                method.setAccessible(true);
                List<TurSEFacetResult> results = (List<TurSEFacetResult>) method.invoke(processor,
                                site, null, queryResponse);

                assertThat(results).isEmpty();
        }

        @Test
        @SuppressWarnings("unchecked")
        void testSetFacetQueriesHandlesUnmatchedCustomFacetsAsFallback() throws Exception {
                TurSolrResultProcessor processor = new TurSolrResultProcessor(turSNFieldProcess,
                                turSNSiteFieldExtRepository, turSNSiteCustomFacetRepository,
                                new com.viglet.turing.observability.TurSearchPipelineObservation(null));
                TurSNSite site = new TurSNSite();

                when(turSNSiteFieldExtRepository.findByTurSNSiteAndEnabled(site, 1))
                                .thenReturn(Collections.emptyList());
                when(turSNSiteCustomFacetRepository.findByFieldExtsWithDetails(any()))
                                .thenReturn(Collections.emptyList());

                QueryResponse queryResponse = mock(QueryResponse.class);
                Map<String, Integer> facetQuery = new HashMap<>();
                facetQuery.put("unknown_facet::itemA", 5);
                facetQuery.put("unknown_facet::itemB", 3);
                when(queryResponse.getFacetQuery()).thenReturn(facetQuery);

                Method method = TurSolrResultProcessor.class.getDeclaredMethod(
                                "setFacetQueries", TurSNSite.class, java.util.Locale.class,
                                QueryResponse.class);
                method.setAccessible(true);
                List<TurSEFacetResult> results = (List<TurSEFacetResult>) method.invoke(processor,
                                site, null, queryResponse);

                assertThat(results).hasSize(1);
                assertThat(results.getFirst().getFacet()).isEqualTo("unknown_facet");
                assertThat(results.getFirst().getTurSEFacetResultAttr()).containsKey("itemA");
                assertThat(results.getFirst().getTurSEFacetResultAttr()).containsKey("itemB");
        }

        @Test
        void testIsHLAttributeReturnsTrueForTextWithHL() throws Exception {
                Method method = TurSolrResultProcessor.class.getDeclaredMethod(
                                "isHLAttribute", Map.class, Map.class, String.class);
                method.setAccessible(true);

                TurSNSiteFieldExt textField = TurSNSiteFieldExt.builder()
                                .name("title").type(TurSEFieldType.TEXT).build();
                Map<String, TurSNSiteFieldExt> fieldExtMap = Map.of("title", textField);
                Map<String, List<String>> hl = Map.of("title", List.of("<em>value</em>"));

                boolean result = (boolean) method.invoke(null, fieldExtMap, hl, "title");
                assertThat(result).isTrue();
        }

        @Test
        void testIsHLAttributeReturnsFalseForIntField() throws Exception {
                Method method = TurSolrResultProcessor.class.getDeclaredMethod(
                                "isHLAttribute", Map.class, Map.class, String.class);
                method.setAccessible(true);

                TurSNSiteFieldExt intField = TurSNSiteFieldExt.builder()
                                .name("count").type(TurSEFieldType.INT).build();
                Map<String, TurSNSiteFieldExt> fieldExtMap = Map.of("count", intField);
                Map<String, List<String>> hl = Map.of("count", List.of("<em>42</em>"));

                boolean result = (boolean) method.invoke(null, fieldExtMap, hl, "count");
                assertThat(result).isFalse();
        }

        @Test
        void testIsHLAttributeReturnsFalseWhenHLIsNull() throws Exception {
                Method method = TurSolrResultProcessor.class.getDeclaredMethod(
                                "isHLAttribute", Map.class, Map.class, String.class);
                method.setAccessible(true);

                TurSNSiteFieldExt textField = TurSNSiteFieldExt.builder()
                                .name("title").type(TurSEFieldType.TEXT).build();
                Map<String, TurSNSiteFieldExt> fieldExtMap = Map.of("title", textField);

                boolean result = (boolean) method.invoke(null, fieldExtMap, null, "title");
                assertThat(result).isFalse();
        }

        @Test
        void testIsHLAttributeReturnsFalseWhenFieldNotInMap() throws Exception {
                Method method = TurSolrResultProcessor.class.getDeclaredMethod(
                                "isHLAttribute", Map.class, Map.class, String.class);
                method.setAccessible(true);

                Map<String, TurSNSiteFieldExt> fieldExtMap = Collections.emptyMap();
                Map<String, List<String>> hl = Map.of("title", List.of("<em>value</em>"));

                boolean result = (boolean) method.invoke(null, fieldExtMap, hl, "title");
                assertThat(result).isFalse();
        }

        @Test
        void testIsHLAttributeReturnsTrueForStringField() throws Exception {
                Method method = TurSolrResultProcessor.class.getDeclaredMethod(
                                "isHLAttribute", Map.class, Map.class, String.class);
                method.setAccessible(true);

                TurSNSiteFieldExt stringField = TurSNSiteFieldExt.builder()
                                .name("name").type(TurSEFieldType.STRING).build();
                Map<String, TurSNSiteFieldExt> fieldExtMap = Map.of("name", stringField);
                Map<String, List<String>> hl = Map.of("name", List.of("<em>val</em>"));

                boolean result = (boolean) method.invoke(null, fieldExtMap, hl, "name");
                assertThat(result).isTrue();
        }

        @Test
        void testIsHLAttributeReturnsFalseForDoubleField() throws Exception {
                Method method = TurSolrResultProcessor.class.getDeclaredMethod(
                                "isHLAttribute", Map.class, Map.class, String.class);
                method.setAccessible(true);

                TurSNSiteFieldExt doubleField = TurSNSiteFieldExt.builder()
                                .name("price").type(TurSEFieldType.DOUBLE).build();
                Map<String, TurSNSiteFieldExt> fieldExtMap = Map.of("price", doubleField);
                Map<String, List<String>> hl = Map.of("price", List.of("<em>9.99</em>"));

                boolean result = (boolean) method.invoke(null, fieldExtMap, hl, "price");
                assertThat(result).isFalse();
        }

        @Test
        void testIsHLAttributeReturnsFalseForLongField() throws Exception {
                Method method = TurSolrResultProcessor.class.getDeclaredMethod(
                                "isHLAttribute", Map.class, Map.class, String.class);
                method.setAccessible(true);

                TurSNSiteFieldExt longField = TurSNSiteFieldExt.builder()
                                .name("timestamp").type(TurSEFieldType.LONG).build();
                Map<String, TurSNSiteFieldExt> fieldExtMap = Map.of("timestamp", longField);
                Map<String, List<String>> hl = Map.of("timestamp", List.of("<em>123456</em>"));

                boolean result = (boolean) method.invoke(null, fieldExtMap, hl, "timestamp");
                assertThat(result).isFalse();
        }

        @Test
        void testGetResultsEnrichesGroupsWithWildcardResponse() {
                TurSolrResultProcessor processor = new TurSolrResultProcessor(turSNFieldProcess,
                                turSNSiteFieldExtRepository, turSNSiteCustomFacetRepository,
                                new com.viglet.turing.observability.TurSearchPipelineObservation(null));
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
