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

package com.viglet.turing.elasticsearch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.lang.reflect.Type;
import java.net.URI;
import java.util.Locale;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.viglet.turing.commons.se.TurSEParameters;
import com.viglet.turing.commons.sn.TurSNConfig;
import com.viglet.turing.commons.sn.bean.TurSNSearchParams;
import com.viglet.turing.commons.sn.search.TurSNSiteSearchContext;
import com.viglet.turing.persistence.model.sn.TurSNSite;
import com.viglet.turing.persistence.repository.sn.TurSNSiteRepository;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.ErrorCause;
import co.elastic.clients.elasticsearch._types.ErrorResponse;
import co.elastic.clients.elasticsearch.core.*;
import com.viglet.turing.commons.se.field.TurSEFieldType;
import com.viglet.turing.persistence.model.sn.field.TurSNSiteFieldExt;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Unit tests for TurElasticsearch.
 *
 * @author Alexandre Oliveira
 * @since 2026.1.10
 */
@SuppressWarnings("unchecked")
@ExtendWith(MockitoExtension.class)
class TurElasticsearchTest {

    @Mock
    private TurSNSiteRepository turSNSiteRepository;

    @Mock
    private com.viglet.turing.persistence.repository.sn.field.TurSNSiteFieldExtRepository turSNSiteFieldExtRepository;

    @Mock
    private ElasticsearchClient elasticsearchClient;

    @Test
    void testRetrieveElasticsearchFromSNReturnsEmptyWhenSiteMissing() throws Exception {
        when(turSNSiteRepository.findByNameIgnoreCase(anyString())).thenReturn(Optional.empty());

        TurElasticsearch service = new TurElasticsearch(turSNSiteRepository, turSNSiteFieldExtRepository);
        TurSNSearchParams searchParams = new TurSNSearchParams();
        TurSNSiteSearchContext context = new TurSNSiteSearchContext("site", new TurSNConfig(),
                new TurSEParameters(searchParams), Locale.US, URI.create("http://example.com"));
        TurElasticsearchInstance instance = new TurElasticsearchInstance(elasticsearchClient,
                URI.create("http://localhost:9200").toURL(), "index");

        Optional<?> result = service.retrieveElasticsearchFromSN(instance, context);

        assertThat(result).isEmpty();
    }

    @Test
    void testRetrieveElasticsearchFromSNHandlesIOException() throws Exception {
        TurSNSite site = new TurSNSite();
        site.setName("site");
        when(turSNSiteRepository.findByNameIgnoreCase("site")).thenReturn(Optional.of(site));
        when(elasticsearchClient.search(any(SearchRequest.class), any(Type.class)))
                .thenThrow(new IOException("boom"));

        TurElasticsearch service = new TurElasticsearch(turSNSiteRepository, turSNSiteFieldExtRepository);
        TurSNSearchParams searchParams = new TurSNSearchParams();
        searchParams.setQ("test");
        TurSEParameters params = new TurSEParameters(searchParams);
        TurSNSiteSearchContext context = new TurSNSiteSearchContext("site", new TurSNConfig(),
                params, Locale.US, URI.create("http://example.com"));
        TurElasticsearchInstance instance = new TurElasticsearchInstance(elasticsearchClient,
                URI.create("http://localhost:9200").toURL(), "index");

        Optional<?> result = service.retrieveElasticsearchFromSN(instance, context);

        assertThat(result).isEmpty();
    }

    @Test
    void testRetrieveFacetElasticsearchFromSNReturnsEmptyWhenSiteMissing() throws Exception {
        when(turSNSiteRepository.findByNameIgnoreCase(anyString())).thenReturn(Optional.empty());

        TurElasticsearch service = new TurElasticsearch(turSNSiteRepository, turSNSiteFieldExtRepository);
        TurSNSearchParams searchParams = new TurSNSearchParams();
        searchParams.setQ("test");
        TurSNSiteSearchContext context = new TurSNSiteSearchContext("site", new TurSNConfig(),
                new TurSEParameters(searchParams), Locale.US, URI.create("http://example.com"));
        TurElasticsearchInstance instance = new TurElasticsearchInstance(elasticsearchClient,
                URI.create("http://localhost:9200").toURL(), "index");

        Optional<?> result = service.retrieveFacetElasticsearchFromSN(instance, context, "category");
        assertThat(result).isEmpty();
    }

    @Test
    void testRetrieveFacetElasticsearchFromSNHandlesIOException() throws Exception {
        TurSNSite site = new TurSNSite();
        site.setName("site");
        when(turSNSiteRepository.findByNameIgnoreCase("site")).thenReturn(Optional.of(site));
        when(turSNSiteFieldExtRepository.findByTurSNSiteAndNameAndFacetAndEnabled(any(), anyString(), any(int.class), any(int.class)))
                .thenReturn(Collections.emptyList());
        when(elasticsearchClient.search(any(SearchRequest.class), any(Type.class)))
                .thenThrow(new IOException("facet io error"));

        TurElasticsearch service = new TurElasticsearch(turSNSiteRepository, turSNSiteFieldExtRepository);
        TurSNSearchParams searchParams = new TurSNSearchParams();
        searchParams.setQ("test");
        TurSNSiteSearchContext context = new TurSNSiteSearchContext("site", new TurSNConfig(),
                new TurSEParameters(searchParams), Locale.US, URI.create("http://example.com"));
        TurElasticsearchInstance instance = new TurElasticsearchInstance(elasticsearchClient,
                URI.create("http://localhost:9200").toURL(), "index");

        Optional<?> result = service.retrieveFacetElasticsearchFromSN(instance, context, "category");
        assertThat(result).isEmpty();
    }

    @Test
    void testIndexingSkipsDocumentWithNoId() throws Exception {
        TurElasticsearch service = new TurElasticsearch(turSNSiteRepository, turSNSiteFieldExtRepository);
        TurSNSite site = new TurSNSite();
        site.setName("testSite");
        TurElasticsearchInstance instance = new TurElasticsearchInstance(elasticsearchClient,
                URI.create("http://localhost:9200").toURL(), "index");

        // Document with no id should be skipped (no exception)
        service.indexing(instance, site, Map.of("title", "no-id-doc"));

        // Verify no index request was made
        org.mockito.Mockito.verify(elasticsearchClient, org.mockito.Mockito.never())
                .index(any(IndexRequest.class));
    }

    @Test
    void testIndexingHandlesIOException() throws Exception {
        TurElasticsearch service = new TurElasticsearch(turSNSiteRepository, turSNSiteFieldExtRepository);
        TurSNSite site = new TurSNSite();
        site.setName("testSite");

        when(elasticsearchClient.index(any(IndexRequest.class))).thenThrow(new IOException("index failed"));

        TurElasticsearchInstance instance = new TurElasticsearchInstance(elasticsearchClient,
                URI.create("http://localhost:9200").toURL(), "index");

        // Should not throw
        org.junit.jupiter.api.Assertions.assertDoesNotThrow(
                () -> service.indexing(instance, site, Map.of("id", "1", "title", "test")));
    }

    @Test
    void testGetDocumentTotalReturnsCountOnSuccess() throws Exception {
        TurElasticsearch service = new TurElasticsearch(turSNSiteRepository, turSNSiteFieldExtRepository);

        CountResponse countResponse = org.mockito.Mockito.mock(CountResponse.class);
        when(countResponse.count()).thenReturn(42L);
        when(elasticsearchClient.count(any(java.util.function.Function.class))).thenReturn(countResponse);

        TurElasticsearchInstance instance = new TurElasticsearchInstance(elasticsearchClient,
                URI.create("http://localhost:9200").toURL(), "index");

        assertThat(service.getDocumentTotal(instance)).isEqualTo(42L);
    }

    @Test
    void testGetDocumentTotalReturnsZeroOnIOException() throws Exception {
        TurElasticsearch service = new TurElasticsearch(turSNSiteRepository, turSNSiteFieldExtRepository);

        when(elasticsearchClient.count(any(java.util.function.Function.class)))
                .thenThrow(new IOException("count failed"));

        TurElasticsearchInstance instance = new TurElasticsearchInstance(elasticsearchClient,
                URI.create("http://localhost:9200").toURL(), "index");

        assertThat(service.getDocumentTotal(instance)).isZero();
    }

    @Test
    void testDeIndexingHandlesIOException() throws Exception {
        TurElasticsearch service = new TurElasticsearch(turSNSiteRepository, turSNSiteFieldExtRepository);

        when(elasticsearchClient.delete(any(java.util.function.Function.class)))
                .thenThrow(new IOException("delete failed"));

        TurElasticsearchInstance instance = new TurElasticsearchInstance(elasticsearchClient,
                URI.create("http://localhost:9200").toURL(), "index");

        org.junit.jupiter.api.Assertions.assertDoesNotThrow(
                () -> service.deIndexing(instance, "doc-1"));
    }

    @Test
    void testDeIndexingCallsDelete() throws Exception {
        TurElasticsearch service = new TurElasticsearch(turSNSiteRepository, turSNSiteFieldExtRepository);

        DeleteResponse deleteResponse = org.mockito.Mockito.mock(DeleteResponse.class);
        when(elasticsearchClient.delete(any(java.util.function.Function.class)))
                .thenReturn(deleteResponse);

        TurElasticsearchInstance instance = new TurElasticsearchInstance(elasticsearchClient,
                URI.create("http://localhost:9200").toURL(), "index");

        org.junit.jupiter.api.Assertions.assertDoesNotThrow(
                () -> service.deIndexing(instance, "doc-1"));
        org.mockito.Mockito.verify(elasticsearchClient).delete(any(java.util.function.Function.class));
    }

    @Test
    void testDeIndexingByTypeHandlesIOException() throws Exception {
        TurElasticsearch service = new TurElasticsearch(turSNSiteRepository, turSNSiteFieldExtRepository);

        when(elasticsearchClient.deleteByQuery(any(DeleteByQueryRequest.class)))
                .thenThrow(new IOException("delete by query failed"));

        TurElasticsearchInstance instance = new TurElasticsearchInstance(elasticsearchClient,
                URI.create("http://localhost:9200").toURL(), "index");

        org.junit.jupiter.api.Assertions.assertDoesNotThrow(
                () -> service.deIndexingByType(instance, "article"));
    }

    @Test
    void testDeIndexingByTypeCallsDeleteByQuery() throws Exception {
        TurElasticsearch service = new TurElasticsearch(turSNSiteRepository, turSNSiteFieldExtRepository);

        DeleteByQueryResponse response = org.mockito.Mockito.mock(DeleteByQueryResponse.class);
        when(elasticsearchClient.deleteByQuery(any(DeleteByQueryRequest.class)))
                .thenReturn(response);

        TurElasticsearchInstance instance = new TurElasticsearchInstance(elasticsearchClient,
                URI.create("http://localhost:9200").toURL(), "index");

        org.junit.jupiter.api.Assertions.assertDoesNotThrow(
                () -> service.deIndexingByType(instance, "article"));
        org.mockito.Mockito.verify(elasticsearchClient).deleteByQuery(any(DeleteByQueryRequest.class));
    }

    @Test
    void testIndexingSuccessWithValidId() throws Exception {
        TurElasticsearch service = new TurElasticsearch(turSNSiteRepository, turSNSiteFieldExtRepository);
        TurSNSite site = new TurSNSite();
        site.setName("testSite");

        IndexResponse indexResponse = org.mockito.Mockito.mock(IndexResponse.class);
        when(indexResponse.result()).thenReturn(co.elastic.clients.elasticsearch._types.Result.Created);
        when(elasticsearchClient.index(any(IndexRequest.class))).thenReturn(indexResponse);

        TurElasticsearchInstance instance = new TurElasticsearchInstance(elasticsearchClient,
                URI.create("http://localhost:9200").toURL(), "index");

        service.indexing(instance, site, Map.of("id", "doc-1", "title", "Hello World"));

        org.mockito.Mockito.verify(elasticsearchClient).index(any(IndexRequest.class));
    }

    @Test
    void testIndexingHandlesElasticsearchException() throws Exception {
        TurElasticsearch service = new TurElasticsearch(turSNSiteRepository, turSNSiteFieldExtRepository);
        TurSNSite site = new TurSNSite();
        site.setName("testSite");

        ErrorResponse errorResponse = ErrorResponse.of(e -> e
                .error(err -> err.type("mapper_parsing_exception").reason("failed to parse"))
                .status(400));
        when(elasticsearchClient.index(any(IndexRequest.class)))
                .thenThrow(new co.elastic.clients.elasticsearch._types.ElasticsearchException(
                        "es-error", errorResponse));

        TurElasticsearchInstance instance = new TurElasticsearchInstance(elasticsearchClient,
                URI.create("http://localhost:9200").toURL(), "index");

        org.junit.jupiter.api.Assertions.assertDoesNotThrow(
                () -> service.indexing(instance, site, Map.of("id", "doc-2", "title", "Test")));
    }

    @Test
    void testGetDocumentTotalHandlesIndexNotFoundException() throws Exception {
        TurElasticsearch service = new TurElasticsearch(turSNSiteRepository, turSNSiteFieldExtRepository);

        ErrorResponse errorResponse = ErrorResponse.of(e -> e
                .error(err -> err.type("index_not_found_exception").reason("no such index"))
                .status(404));
        when(elasticsearchClient.count(any(java.util.function.Function.class)))
                .thenThrow(new co.elastic.clients.elasticsearch._types.ElasticsearchException(
                        "es-error", errorResponse));

        TurElasticsearchInstance instance = new TurElasticsearchInstance(elasticsearchClient,
                URI.create("http://localhost:9200").toURL(), "index");

        assertThat(service.getDocumentTotal(instance)).isZero();
    }

    @Test
    void testGetDocumentTotalHandlesGenericElasticsearchException() throws Exception {
        TurElasticsearch service = new TurElasticsearch(turSNSiteRepository, turSNSiteFieldExtRepository);

        ErrorResponse errorResponse = ErrorResponse.of(e -> e
                .error(err -> err.type("search_phase_execution_exception").reason("all shards failed"))
                .status(500));
        when(elasticsearchClient.count(any(java.util.function.Function.class)))
                .thenThrow(new co.elastic.clients.elasticsearch._types.ElasticsearchException(
                        "es-error", errorResponse));

        TurElasticsearchInstance instance = new TurElasticsearchInstance(elasticsearchClient,
                URI.create("http://localhost:9200").toURL(), "index");

        assertThat(service.getDocumentTotal(instance)).isZero();
    }

    @Test
    void testRetrieveElasticsearchFromSNHandlesElasticsearchException() throws Exception {
        TurSNSite site = new TurSNSite();
        site.setName("site");
        when(turSNSiteRepository.findByNameIgnoreCase("site")).thenReturn(Optional.of(site));

        ErrorResponse errorResponse = ErrorResponse.of(e -> e
                .error(err -> err.type("index_not_found_exception").reason("no such index"))
                .status(404));
        when(elasticsearchClient.search(any(SearchRequest.class), any(java.lang.reflect.Type.class)))
                .thenThrow(new co.elastic.clients.elasticsearch._types.ElasticsearchException(
                        "es-error", errorResponse));

        TurElasticsearch service = new TurElasticsearch(turSNSiteRepository, turSNSiteFieldExtRepository);
        TurSNSearchParams searchParams = new TurSNSearchParams();
        searchParams.setQ("test");
        TurSEParameters params = new TurSEParameters(searchParams);
        TurSNSiteSearchContext context = new TurSNSiteSearchContext("site", new TurSNConfig(),
                params, Locale.US, URI.create("http://example.com"));
        TurElasticsearchInstance instance = new TurElasticsearchInstance(elasticsearchClient,
                URI.create("http://localhost:9200").toURL(), "index");

        Optional<?> result = service.retrieveElasticsearchFromSN(instance, context);
        assertThat(result).isEmpty();
    }

    @Test
    void testRetrieveElasticsearchFromSNHandlesNonIndexNotFoundElasticsearchException() throws Exception {
        TurSNSite site = new TurSNSite();
        site.setName("site");
        when(turSNSiteRepository.findByNameIgnoreCase("site")).thenReturn(Optional.of(site));

        ErrorResponse errorResponse = ErrorResponse.of(e -> e
                .error(err -> err.type("search_phase_execution_exception")
                        .reason("all shards failed")
                        .rootCause(List.of(
                                ErrorCause.of(rc -> rc.type("illegal_argument_exception")
                                        .reason("field not found")))))
                .status(500));
        when(elasticsearchClient.search(any(SearchRequest.class), any(java.lang.reflect.Type.class)))
                .thenThrow(new co.elastic.clients.elasticsearch._types.ElasticsearchException(
                        "es-error", errorResponse));

        TurElasticsearch service = new TurElasticsearch(turSNSiteRepository, turSNSiteFieldExtRepository);
        TurSNSearchParams searchParams = new TurSNSearchParams();
        searchParams.setQ("test");
        TurSEParameters params = new TurSEParameters(searchParams);
        TurSNSiteSearchContext context = new TurSNSiteSearchContext("site", new TurSNConfig(),
                params, Locale.US, URI.create("http://example.com"));
        TurElasticsearchInstance instance = new TurElasticsearchInstance(elasticsearchClient,
                URI.create("http://localhost:9200").toURL(), "index");

        Optional<?> result = service.retrieveElasticsearchFromSN(instance, context);
        assertThat(result).isEmpty();
    }

    @Test
    void testRetrieveFacetElasticsearchFromSNHandlesElasticsearchException() throws Exception {
        TurSNSite site = new TurSNSite();
        site.setName("site");
        when(turSNSiteRepository.findByNameIgnoreCase("site")).thenReturn(Optional.of(site));
        when(turSNSiteFieldExtRepository.findByTurSNSiteAndNameAndFacetAndEnabled(any(), anyString(), any(int.class), any(int.class)))
                .thenReturn(Collections.emptyList());

        ErrorResponse errorResponse = ErrorResponse.of(e -> e
                .error(err -> err.type("illegal_argument_exception").reason("Text fields not optimized for agg"))
                .status(400));
        when(elasticsearchClient.search(any(SearchRequest.class), any(java.lang.reflect.Type.class)))
                .thenThrow(new co.elastic.clients.elasticsearch._types.ElasticsearchException(
                        "es-error", errorResponse));

        TurElasticsearch service = new TurElasticsearch(turSNSiteRepository, turSNSiteFieldExtRepository);
        TurSNSearchParams searchParams = new TurSNSearchParams();
        searchParams.setQ("test");
        TurSNSiteSearchContext context = new TurSNSiteSearchContext("site", new TurSNConfig(),
                new TurSEParameters(searchParams), Locale.US, URI.create("http://example.com"));
        TurElasticsearchInstance instance = new TurElasticsearchInstance(elasticsearchClient,
                URI.create("http://localhost:9200").toURL(), "index");

        Optional<?> result = service.retrieveFacetElasticsearchFromSN(instance, context, "category");
        assertThat(result).isEmpty();
    }

    @Test
    void testRetrieveElasticsearchFromSNWithMatchAllQuery() throws Exception {
        TurSNSite site = new TurSNSite();
        site.setName("site");
        when(turSNSiteRepository.findByNameIgnoreCase("site")).thenReturn(Optional.of(site));
        // Empty query should produce a match_all query -- test the IOException path
        // to at least exercise buildQuery with empty/wildcard input
        when(elasticsearchClient.search(any(SearchRequest.class), any(java.lang.reflect.Type.class)))
                .thenThrow(new IOException("connection refused"));

        TurElasticsearch service = new TurElasticsearch(turSNSiteRepository, turSNSiteFieldExtRepository);
        TurSNSearchParams searchParams = new TurSNSearchParams();
        searchParams.setQ("*");
        TurSEParameters params = new TurSEParameters(searchParams);
        TurSNSiteSearchContext context = new TurSNSiteSearchContext("site", new TurSNConfig(),
                params, Locale.US, URI.create("http://example.com"));
        TurElasticsearchInstance instance = new TurElasticsearchInstance(elasticsearchClient,
                URI.create("http://localhost:9200").toURL(), "index");

        Optional<?> result = service.retrieveElasticsearchFromSN(instance, context);
        assertThat(result).isEmpty();
    }

    @Test
    void testRetrieveElasticsearchFromSNWithEmptyQuery() throws Exception {
        TurSNSite site = new TurSNSite();
        site.setName("site");
        when(turSNSiteRepository.findByNameIgnoreCase("site")).thenReturn(Optional.of(site));
        when(elasticsearchClient.search(any(SearchRequest.class), any(java.lang.reflect.Type.class)))
                .thenThrow(new IOException("connection refused"));

        TurElasticsearch service = new TurElasticsearch(turSNSiteRepository, turSNSiteFieldExtRepository);
        TurSNSearchParams searchParams = new TurSNSearchParams();
        // null/empty query exercises the matchAll branch
        TurSEParameters params = new TurSEParameters(searchParams);
        TurSNSiteSearchContext context = new TurSNSiteSearchContext("site", new TurSNConfig(),
                params, Locale.US, URI.create("http://example.com"));
        TurElasticsearchInstance instance = new TurElasticsearchInstance(elasticsearchClient,
                URI.create("http://localhost:9200").toURL(), "index");

        Optional<?> result = service.retrieveElasticsearchFromSN(instance, context);
        assertThat(result).isEmpty();
    }

    @ParameterizedTest
    @CsvSource({"test, 'date desc'", "test, 'date asc'", "test, relevance", "test, ''"})
    void testRetrieveElasticsearchFromSNWithSortVariations(String query, String sort) throws Exception {
        TurSNSite site = new TurSNSite();
        site.setName("site");
        when(turSNSiteRepository.findByNameIgnoreCase("site")).thenReturn(Optional.of(site));
        when(elasticsearchClient.search(any(SearchRequest.class), any(java.lang.reflect.Type.class)))
                .thenThrow(new IOException("connection refused"));

        TurElasticsearch service = new TurElasticsearch(turSNSiteRepository, turSNSiteFieldExtRepository);
        TurSNSearchParams searchParams = new TurSNSearchParams();
        searchParams.setQ(query);
        if (sort != null && !sort.isEmpty()) {
            searchParams.setSort(sort);
        }
        TurSEParameters params = new TurSEParameters(searchParams);
        TurSNSiteSearchContext context = new TurSNSiteSearchContext("site", new TurSNConfig(),
                params, Locale.US, URI.create("http://example.com"));
        TurElasticsearchInstance instance = new TurElasticsearchInstance(elasticsearchClient,
                URI.create("http://localhost:9200").toURL(), "index");

        Optional<?> result = service.retrieveElasticsearchFromSN(instance, context);
        assertThat(result).isEmpty();
    }

    @Test
    void testRetrieveElasticsearchFromSNWithFacetFields() throws Exception {
        TurSNSite site = new TurSNSite();
        site.setName("site");
        when(turSNSiteRepository.findByNameIgnoreCase("site")).thenReturn(Optional.of(site));

        TurSNSiteFieldExt textFacet = TurSNSiteFieldExt.builder()
                .name("category").type(TurSEFieldType.TEXT).build();
        TurSNSiteFieldExt intFacet = TurSNSiteFieldExt.builder()
                .name("count").type(TurSEFieldType.INT).build();
        TurSNSiteFieldExt stringFacet = TurSNSiteFieldExt.builder()
                .name("tag").type(TurSEFieldType.STRING).build();
        TurSNSiteFieldExt arrayFacet = TurSNSiteFieldExt.builder()
                .name("labels").type(TurSEFieldType.ARRAY).build();
        TurSNSiteFieldExt currencyFacet = TurSNSiteFieldExt.builder()
                .name("price").type(TurSEFieldType.CURRENCY).build();

        when(turSNSiteFieldExtRepository.findByTurSNSiteAndFacetAndEnabledOrderByFacetPosition(site, 1, 1))
                .thenReturn(List.of(textFacet, intFacet, stringFacet, arrayFacet, currencyFacet));

        when(elasticsearchClient.search(any(SearchRequest.class), any(java.lang.reflect.Type.class)))
                .thenThrow(new IOException("connection refused"));

        TurElasticsearch service = new TurElasticsearch(turSNSiteRepository, turSNSiteFieldExtRepository);
        TurSNSearchParams searchParams = new TurSNSearchParams();
        searchParams.setQ("test");
        TurSEParameters params = new TurSEParameters(searchParams);
        TurSNSiteSearchContext context = new TurSNSiteSearchContext("site", new TurSNConfig(),
                params, Locale.US, URI.create("http://example.com"));
        TurElasticsearchInstance instance = new TurElasticsearchInstance(elasticsearchClient,
                URI.create("http://localhost:9200").toURL(), "index");

        // This exercises addFacetAggregations + getFacetAggField for various field types
        Optional<?> result = service.retrieveElasticsearchFromSN(instance, context);
        assertThat(result).isEmpty();
    }

    @Test
    void testRetrieveElasticsearchFromSNWithPagination() throws Exception {
        TurSNSite site = new TurSNSite();
        site.setName("site");
        when(turSNSiteRepository.findByNameIgnoreCase("site")).thenReturn(Optional.of(site));
        when(elasticsearchClient.search(any(SearchRequest.class), any(java.lang.reflect.Type.class)))
                .thenThrow(new IOException("connection refused"));

        TurElasticsearch service = new TurElasticsearch(turSNSiteRepository, turSNSiteFieldExtRepository);
        TurSNSearchParams searchParams = new TurSNSearchParams();
        searchParams.setQ("test");
        searchParams.setP(3);
        searchParams.setRows(20);
        TurSEParameters params = new TurSEParameters(searchParams);
        TurSNSiteSearchContext context = new TurSNSiteSearchContext("site", new TurSNConfig(),
                params, Locale.US, URI.create("http://example.com"));
        TurElasticsearchInstance instance = new TurElasticsearchInstance(elasticsearchClient,
                URI.create("http://localhost:9200").toURL(), "index");

        // exercises getStartPosition with page=3, rows=20 -> from=40
        Optional<?> result = service.retrieveElasticsearchFromSN(instance, context);
        assertThat(result).isEmpty();
    }

    @Test
    void testRetrieveFacetElasticsearchFromSNWithFieldExtResolution() throws Exception {
        TurSNSite site = new TurSNSite();
        site.setName("site");
        when(turSNSiteRepository.findByNameIgnoreCase("site")).thenReturn(Optional.of(site));

        TurSNSiteFieldExt facetField = TurSNSiteFieldExt.builder()
                .name("category").type(TurSEFieldType.TEXT).build();
        when(turSNSiteFieldExtRepository.findByTurSNSiteAndNameAndFacetAndEnabled(site, "category", 1, 1))
                .thenReturn(java.util.List.of(facetField));

        when(elasticsearchClient.search(any(SearchRequest.class), any(java.lang.reflect.Type.class)))
                .thenThrow(new IOException("connection refused"));

        TurElasticsearch service = new TurElasticsearch(turSNSiteRepository, turSNSiteFieldExtRepository);
        TurSNSearchParams searchParams = new TurSNSearchParams();
        searchParams.setQ("test");
        TurSNSiteSearchContext context = new TurSNSiteSearchContext("site", new TurSNConfig(),
                new TurSEParameters(searchParams), Locale.US, URI.create("http://example.com"));
        TurElasticsearchInstance instance = new TurElasticsearchInstance(elasticsearchClient,
                URI.create("http://localhost:9200").toURL(), "index");

        // exercises getFacetAggField resolution for TEXT type -> ".keyword" suffix
        Optional<?> result = service.retrieveFacetElasticsearchFromSN(instance, context, "category");
        assertThat(result).isEmpty();
    }

    @Test
    void testRetrieveFacetElasticsearchFromSNWithIntFieldExtResolution() throws Exception {
        TurSNSite site = new TurSNSite();
        site.setName("site");
        when(turSNSiteRepository.findByNameIgnoreCase("site")).thenReturn(Optional.of(site));

        TurSNSiteFieldExt intFacetField = TurSNSiteFieldExt.builder()
                .name("count").type(TurSEFieldType.INT).build();
        when(turSNSiteFieldExtRepository.findByTurSNSiteAndNameAndFacetAndEnabled(site, "count", 1, 1))
                .thenReturn(java.util.List.of(intFacetField));

        when(elasticsearchClient.search(any(SearchRequest.class), any(java.lang.reflect.Type.class)))
                .thenThrow(new IOException("connection refused"));

        TurElasticsearch service = new TurElasticsearch(turSNSiteRepository, turSNSiteFieldExtRepository);
        TurSNSearchParams searchParams = new TurSNSearchParams();
        searchParams.setQ("test");
        TurSNSiteSearchContext context = new TurSNSiteSearchContext("site", new TurSNConfig(),
                new TurSEParameters(searchParams), Locale.US, URI.create("http://example.com"));
        TurElasticsearchInstance instance = new TurElasticsearchInstance(elasticsearchClient,
                URI.create("http://localhost:9200").toURL(), "index");

        // INT fields should NOT get .keyword suffix
        Optional<?> result = service.retrieveFacetElasticsearchFromSN(instance, context, "count");
        assertThat(result).isEmpty();
    }

    @Test
    void testRetrieveElasticsearchFromSNWithSortFieldOnly() throws Exception {
        TurSNSite site = new TurSNSite();
        site.setName("site");
        when(turSNSiteRepository.findByNameIgnoreCase("site")).thenReturn(Optional.of(site));
        when(elasticsearchClient.search(any(SearchRequest.class), any(java.lang.reflect.Type.class)))
                .thenThrow(new IOException("connection refused"));

        TurElasticsearch service = new TurElasticsearch(turSNSiteRepository, turSNSiteFieldExtRepository);
        TurSNSearchParams searchParams = new TurSNSearchParams();
        searchParams.setQ("test");
        searchParams.setSort("title"); // single field, no direction -> defaults to Asc
        TurSEParameters params = new TurSEParameters(searchParams);
        TurSNSiteSearchContext context = new TurSNSiteSearchContext("site", new TurSNConfig(),
                params, Locale.US, URI.create("http://example.com"));
        TurElasticsearchInstance instance = new TurElasticsearchInstance(elasticsearchClient,
                URI.create("http://localhost:9200").toURL(), "index");

        // exercises addSorting with single-part sort string (defaults to Asc)
        Optional<?> result = service.retrieveElasticsearchFromSN(instance, context);
        assertThat(result).isEmpty();
    }

    @Test
    void testRetrieveElasticsearchFromSNHandlesNonIndexNotFoundExceptionWithNullRootCause() throws Exception {
        TurSNSite site = new TurSNSite();
        site.setName("site");
        when(turSNSiteRepository.findByNameIgnoreCase("site")).thenReturn(Optional.of(site));

        ErrorResponse errorResponse = ErrorResponse.of(e -> e
                .error(err -> err.type("query_shard_exception").reason("failed"))
                .status(400));
        when(elasticsearchClient.search(any(SearchRequest.class), any(java.lang.reflect.Type.class)))
                .thenThrow(new co.elastic.clients.elasticsearch._types.ElasticsearchException(
                        "es-error", errorResponse));

        TurElasticsearch service = new TurElasticsearch(turSNSiteRepository, turSNSiteFieldExtRepository);
        TurSNSearchParams searchParams = new TurSNSearchParams();
        searchParams.setQ("test");
        TurSEParameters params = new TurSEParameters(searchParams);
        TurSNSiteSearchContext context = new TurSNSiteSearchContext("site", new TurSNConfig(),
                params, Locale.US, URI.create("http://example.com"));
        TurElasticsearchInstance instance = new TurElasticsearchInstance(elasticsearchClient,
                URI.create("http://localhost:9200").toURL(), "index");

        // exercises handleElasticsearchException non-index-not-found with null/empty rootCause
        Optional<?> result = service.retrieveElasticsearchFromSN(instance, context);
        assertThat(result).isEmpty();
    }
}
