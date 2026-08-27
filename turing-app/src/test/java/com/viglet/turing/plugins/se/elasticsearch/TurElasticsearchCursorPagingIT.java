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
package com.viglet.turing.plugins.se.elasticsearch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.apache.http.HttpHost;
import org.apache.http.HttpRequestInterceptor;
import org.elasticsearch.client.RestClient;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.rest_client.RestClientTransport;

import com.viglet.turing.commons.se.field.TurSEFieldType;
import com.viglet.turing.commons.sn.field.TurSNFieldName;
import com.viglet.turing.elasticsearch.TurElasticsearch;
import com.viglet.turing.elasticsearch.TurElasticsearchInstance;
import com.viglet.turing.elasticsearch.TurElasticsearchInstanceProcess;
import com.viglet.turing.elasticsearch.TurElasticsearchUtils;
import com.viglet.turing.persistence.model.sn.locale.TurSNSiteLocale;
import com.viglet.turing.persistence.repository.sn.field.TurSNSiteFieldExtRepository;

/**
 * T1040 / §CVI.5 — the Elasticsearch half: asks the engine the same two questions
 * the Solr container answered, against the mapping Turing itself creates.
 *
 * <p>The index is built through {@link TurElasticsearchUtils#createIndex} rather
 * than by dynamic mapping, because the mapping is the thing under test. A
 * {@code STRING} field becomes {@code text} with the {@code keyword} analyzer plus
 * a {@code .keyword} sub-field, so {@code id} is a <em>text</em> field — and both
 * cursor methods catch {@code Exception} and return an empty page, which the
 * caller reads as "the index holds nothing" rather than as a failure.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Testcontainers(disabledWithoutDocker = true)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TurElasticsearchCursorPagingIT {

    private static final String INDEX = "turing_it";
    private static final String SOURCE = "AEM Author";

    private static final GenericContainer<?> ES = new GenericContainer<>(DockerImageName
            .parse("docker.elastic.co/elasticsearch/elasticsearch:9.1.0"))
                    .withExposedPorts(9200)
                    .withEnv("discovery.type", "single-node")
                    .withEnv("xpack.security.enabled", "false")
                    .withEnv("ES_JAVA_OPTS", "-Xms512m -Xmx512m")
                    .waitingFor(Wait.forHttp("/_cluster/health")
                            .forStatusCode(200)
                            .withStartupTimeout(java.time.Duration.ofMinutes(4)));

    /** The same ids the Solr container is seeded with, for the same reasons. */
    private static final List<String> IDS = List.of(
            "/content/wknd/ski touring mont blanc",
            "/content/wknd/skiing",
            "doc-00001",
            "doc-00002",
            "my doc",
            "ns:doc",
            "plain");

    private TurElasticsearchSearchEnginePlugin plugin;
    private TurSNSiteLocale siteLocale;

    /** Mirrors {@code TurElasticsearchInstanceProcess.createInstance} — headers included. */
    private static ElasticsearchClient productionStyleClient(String url) {
        URI uri = URI.create(url);
        RestClient restClient = RestClient
                .builder(new HttpHost(uri.getHost(), uri.getPort(), uri.getScheme()))
                .setHttpClientConfigCallback(builder -> {
                    builder.addInterceptorLast((HttpRequestInterceptor) (request, context) -> {
                        request.removeHeaders("Content-Type");
                        request.removeHeaders("Accept");
                        request.setHeader("Content-Type", "application/json");
                        request.setHeader("Accept", "application/json");
                    });
                    return builder;
                })
                .build();
        return new ElasticsearchClient(new RestClientTransport(restClient, new JacksonJsonpMapper()));
    }

    @BeforeAll
    void startAndSeed() throws Exception {
        ES.start();
        String url = "http://" + ES.getHost() + ":" + ES.getMappedPort(9200);

        // Production's own mapping call, with the field types an SN site declares.
        Map<String, TurSEFieldType> fieldTypes = new LinkedHashMap<>();
        fieldTypes.put(TurSNFieldName.ID, TurSEFieldType.STRING);
        fieldTypes.put(TurSNFieldName.TYPE, TurSEFieldType.STRING);
        fieldTypes.put(TurSNFieldName.SOURCE_APPS, TurSEFieldType.STRING);
        fieldTypes.put(TurSNFieldName.CHECKSUM, TurSEFieldType.STRING);
        TurElasticsearchUtils.createIndex(url, INDEX, fieldTypes);

        ElasticsearchClient client = productionStyleClient(url);
        for (String id : IDS) {
            Map<String, Object> doc = new LinkedHashMap<>();
            doc.put(TurSNFieldName.ID, id);
            doc.put(TurSNFieldName.TYPE, "Page");
            doc.put(TurSNFieldName.SOURCE_APPS, SOURCE);
            doc.put(TurSNFieldName.CHECKSUM, "sum-" + Math.abs(id.hashCode()));
            client.index(i -> i.index(INDEX.toLowerCase()).id(id).document(doc));
        }
        client.indices().refresh(r -> r.index(INDEX.toLowerCase()));

        var instanceProcess = mock(TurElasticsearchInstanceProcess.class);
        when(instanceProcess.initElasticsearchInstance(any(TurSNSiteLocale.class)))
                .thenReturn(Optional.of(new TurElasticsearchInstance(client,
                        URI.create(url).toURL(), INDEX.toLowerCase())));

        siteLocale = new TurSNSiteLocale();
        plugin = new TurElasticsearchSearchEnginePlugin(mock(TurElasticsearch.class),
                instanceProcess, mock(TurSNSiteFieldExtRepository.class));
    }

    @AfterAll
    void stopContainer() {
        ES.stop();
    }

    private List<String> sweepInventory(int rows) {
        List<String> ids = new ArrayList<>();
        String cursor = null;
        for (int guard = 0; guard < 100; guard++) {
            var page = plugin.retrieveInventoryPage(siteLocale, SOURCE, cursor, rows);
            if (page.entries().isEmpty()) {
                break;
            }
            page.entries().forEach(entry -> ids.add(entry.id()));
            cursor = page.nextCursor();
            if (!page.hasMore() || cursor == null) {
                break;
            }
        }
        return ids;
    }

    private static Map<String, ?> siteFieldMap() {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put(TurSNFieldName.SOURCE_APPS, null);
        return fields;
    }

    @Test
    @DisplayName("An inventory sweep reaches every document — an empty first page is the bug")
    void inventorySweepIsComplete() {
        assertThat(sweepInventory(2))
                .as("an empty sweep here means the caller was told the index is empty")
                .containsExactlyInAnyOrderElementsOf(IDS);
    }

    @Test
    @DisplayName("The first inventory page is not empty, and reports the source total")
    void firstPageIsNotEmpty() {
        var page = plugin.retrieveInventoryPage(siteLocale, SOURCE, null, 50);

        assertThat(page.supported()).isTrue();
        assertThat(page.entries()).hasSize(IDS.size());
        assertThat(page.remaining()).isEqualTo(IDS.size());
    }

    @Test
    @DisplayName("Pages come back id-ascending, which is what makes the last id a valid cursor")
    void pagesAreIdAscending() {
        assertThat(sweepInventory(2)).isSorted();
    }

    @Test
    @DisplayName("The cursor bound is exclusive — Elasticsearch agreeing, not being assumed")
    void cursorBoundIsExclusive() {
        List<String> sorted = IDS.stream().sorted().toList();
        String third = sorted.get(2);

        var page = plugin.retrieveInventoryPage(siteLocale, SOURCE, third, 50);

        assertThat(page.entries()).extracting("id")
                .doesNotContain(third)
                .containsExactlyElementsOf(sorted.subList(3, sorted.size()));
    }

    @Test
    @DisplayName("A cursor on an id containing a space continues instead of ending the sweep")
    void spacedCursorDoesNotEndTheSweep() {
        var page = plugin.retrieveInventoryPage(siteLocale, SOURCE, "my doc", 50);

        assertThat(page.entries()).extracting("id").containsExactly("ns:doc", "plain");
    }

    @Test
    @DisplayName("A source name containing a space filters, rather than matching everything")
    void sourceFilterHandlesASpace() {
        var matching = plugin.retrieveInventoryPage(siteLocale, SOURCE, null, 50);
        var other = plugin.retrieveInventoryPage(siteLocale, "AEM Publish", null, 50);

        assertThat(matching.entries()).hasSize(IDS.size());
        assertThat(other.entries()).isEmpty();
    }

    @Test
    @DisplayName("The export cursor agrees with the inventory cursor")
    void exportSweepAgreesWithInventory() {
        List<String> ids = new ArrayList<>();
        String cursor = null;
        for (int guard = 0; guard < 100; guard++) {
            List<Map<String, Object>> page =
                    plugin.retrieveDocumentPageAfter(siteLocale, siteFieldMap(), cursor, 3);
            if (page.isEmpty()) {
                break;
            }
            page.forEach(doc -> ids.add(doc.get(TurSNFieldName.ID).toString()));
            cursor = ids.get(ids.size() - 1);
            if (page.size() < 3) {
                break;
            }
        }

        assertThat(ids).containsExactlyInAnyOrderElementsOf(IDS);
    }

    @Test
    @DisplayName("An id longer than the keyword sub-field's ignore_above is still swept")
    void veryLongIdIsNotLost() throws Exception {
        // The sort field is id.keyword, which carries ignore_above: 256 — a longer
        // id has no keyword value at all. Asked rather than assumed, because a
        // document silently absent from a sweep is what the audit calls missing.
        String longId = "/content/wknd/" + "a".repeat(300);
        String url = "http://" + ES.getHost() + ":" + ES.getMappedPort(9200);
        ElasticsearchClient client = productionStyleClient(url);
        Map<String, Object> doc = new LinkedHashMap<>();
        doc.put(TurSNFieldName.ID, longId);
        doc.put(TurSNFieldName.TYPE, "Page");
        doc.put(TurSNFieldName.SOURCE_APPS, SOURCE);
        doc.put(TurSNFieldName.CHECKSUM, "sum-long");
        client.index(i -> i.index(INDEX.toLowerCase()).id(longId).document(doc));
        client.indices().refresh(r -> r.index(INDEX.toLowerCase()));

        try {
            var page = plugin.retrieveInventoryPage(siteLocale, SOURCE, null, 50);
            assertThat(page.entries()).extracting("id").contains(longId);
            assertThat(page.remaining()).isEqualTo(IDS.size() + 1L);
        } finally {
            client.delete(d -> d.index(INDEX.toLowerCase()).id(longId));
            client.indices().refresh(r -> r.index(INDEX.toLowerCase()));
        }
    }

    @Test
    @DisplayName("The indexed checksum comes back on every row")
    void checksumIsReported() {
        var page = plugin.retrieveInventoryPage(siteLocale, SOURCE, null, 50);

        assertThat(page.entries()).allSatisfy(entry ->
                assertThat(entry.checksum()).isNotBlank().startsWith("sum-"));
    }
}
