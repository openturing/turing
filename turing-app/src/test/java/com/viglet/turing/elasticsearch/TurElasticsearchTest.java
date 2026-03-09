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
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.viglet.turing.commons.se.TurSEParameters;
import com.viglet.turing.commons.sn.TurSNConfig;
import com.viglet.turing.commons.sn.bean.TurSNSearchParams;
import com.viglet.turing.commons.sn.search.TurSNSiteSearchContext;
import com.viglet.turing.persistence.model.sn.TurSNSite;
import com.viglet.turing.persistence.repository.sn.TurSNSiteRepository;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.SearchRequest;

/**
 * Unit tests for TurElasticsearch.
 *
 * @author Alexandre Oliveira
 * @since 2026.1.10
 */
@ExtendWith(MockitoExtension.class)
class TurElasticsearchTest {

    @Mock
    private TurSNSiteRepository turSNSiteRepository;

    @Mock
    private ElasticsearchClient elasticsearchClient;

    @Test
    void testRetrieveElasticsearchFromSNReturnsEmptyWhenSiteMissing() throws Exception {
        when(turSNSiteRepository.findByName(anyString())).thenReturn(Optional.empty());

        TurElasticsearch service = new TurElasticsearch(turSNSiteRepository);
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
        when(turSNSiteRepository.findByName("site")).thenReturn(Optional.of(site));
        when(elasticsearchClient.search(any(SearchRequest.class), any(Type.class)))
                .thenThrow(new IOException("boom"));

        TurElasticsearch service = new TurElasticsearch(turSNSiteRepository);
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
}
