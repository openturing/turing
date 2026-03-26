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
package com.viglet.turing.elasticsearch;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.mapping.Property;
import co.elastic.clients.elasticsearch.cat.IndicesResponse;
import co.elastic.clients.elasticsearch.cat.indices.IndicesRecord;
import co.elastic.clients.elasticsearch.indices.ExistsRequest;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import com.viglet.turing.commons.se.field.TurSEFieldType;
import com.viglet.turing.solr.bean.TurSECoreInfo;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.HttpHost;
import org.apache.http.HttpRequestInterceptor;
import org.elasticsearch.client.RestClient;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Static utility methods for Elasticsearch index management.
 * Mirrors {@link com.viglet.turing.lucene.TurLuceneUtils} for the Elasticsearch engine.
 *
 * @author Alexandre Oliveira
 * @since 2026.1
 */
@Slf4j
public class TurElasticsearchUtils {

    private TurElasticsearchUtils() {
        throw new IllegalStateException("Elasticsearch Utility class");
    }

    /**
     * Creates an Elasticsearch index if it does not already exist.
     *
     * @param endpointUrl the Elasticsearch endpoint URL
     * @param indexName   the name of the index to create
     */
    public static void createIndex(String endpointUrl, String indexName) {
        String index = indexName.toLowerCase();
        try (RestClient restClient = buildRestClient(endpointUrl);
             RestClientTransport transport = new RestClientTransport(restClient, new JacksonJsonpMapper());
             ElasticsearchClient client = new ElasticsearchClient(transport)) {
            boolean exists = client.indices().exists(
                    ExistsRequest.of(r -> r.index(index))).value();
            if (exists) {
                log.info("Elasticsearch index '{}' already exists at {}", index, endpointUrl);
            } else {
                client.indices().create(r -> r.index(index));
                log.info("Elasticsearch index '{}' created at {}", index, endpointUrl);
            }
        } catch (IOException e) {
            log.error("Failed to create Elasticsearch index '{}' at {}: {}", indexName, endpointUrl,
                    e.getMessage(), e);
        }
    }

    /**
     * Creates an Elasticsearch index with explicit field type mappings.
     * STRING/ARRAY → keyword, TEXT → text+keyword sub-field, BOOL → boolean,
     * INT → integer, LONG/DATE → long/date, FLOAT → float, DOUBLE/CURRENCY → double.
     *
     * @param endpointUrl the Elasticsearch endpoint URL
     * @param indexName   the name of the index to create
     * @param fieldTypes  map of field name → Turing field type
     */
    public static void createIndex(String endpointUrl, String indexName,
            Map<String, TurSEFieldType> fieldTypes) {
        String index = indexName.toLowerCase();
        try (RestClient restClient = buildRestClient(endpointUrl);
             RestClientTransport transport = new RestClientTransport(restClient, new JacksonJsonpMapper());
             ElasticsearchClient client = new ElasticsearchClient(transport)) {
            boolean exists = client.indices().exists(
                    ExistsRequest.of(r -> r.index(index))).value();
            if (exists) {
                log.info("Elasticsearch index '{}' already exists at {}", index, endpointUrl);
            } else {
                Map<String, Property> properties = new HashMap<>();
                fieldTypes.forEach((name, type) -> properties.put(name, toEsProperty(type)));
                client.indices().create(r -> r.index(index)
                        .mappings(m -> m.properties(properties)));
                log.info("Elasticsearch index '{}' created with {} field mappings at {}",
                        index, properties.size(), endpointUrl);
            }
        } catch (IOException e) {
            log.error("Failed to create Elasticsearch index '{}' at {}: {}", indexName, endpointUrl,
                    e.getMessage(), e);
        }
    }

    private static final String KEYWORD = "keyword";

    private static Property toEsProperty(TurSEFieldType type) {
        Property keywordSubField = Property.of(fp -> fp.keyword(k -> k.ignoreAbove(256)));
        return switch (type) {
            // TEXT: analyzed full-text + .keyword sub-field for aggregation
            case TEXT -> Property.of(p -> p.text(t -> t
                    .fields(KEYWORD, keywordSubField)));
            // STRING/ARRAY: keyword (no tokenization) analyzer + .keyword sub-field for aggregation
            // Using "keyword" analyzer keeps exact-match semantics while providing a .keyword
            // sub-field — consistent with ES dynamic mapping so existing and new indexes behave the same
            case STRING, ARRAY -> Property.of(p -> p.text(t -> t
                    .analyzer(KEYWORD)
                    .fields(KEYWORD, keywordSubField)));
            case BOOL -> Property.of(p -> p.boolean_(b -> b));
            case INT -> Property.of(p -> p.integer(i -> i));
            case LONG -> Property.of(p -> p.long_(l -> l));
            case FLOAT -> Property.of(p -> p.float_(f -> f));
            case DOUBLE -> Property.of(p -> p.double_(d -> d));
            // CURRENCY values are stored as "amount,ISO4217" strings (e.g. "150.00,BRL")
            case CURRENCY -> Property.of(p -> p.text(t -> t
                    .analyzer(KEYWORD)
                    .fields(KEYWORD, keywordSubField)));
            case DATE -> Property.of(p -> p.date(d -> d));
        };
    }

    /**
     * Deletes an Elasticsearch index if it exists.
     *
     * @param endpointUrl the Elasticsearch endpoint URL
     * @param indexName   the name of the index to delete
     */
    public static void deleteIndex(String endpointUrl, String indexName) {
        try (RestClient restClient = buildRestClient(endpointUrl);
             RestClientTransport transport = new RestClientTransport(restClient, new JacksonJsonpMapper());
             ElasticsearchClient client = new ElasticsearchClient(transport)) {
            boolean exists = client.indices().exists(
                    ExistsRequest.of(r -> r.index(indexName))).value();
            if (exists) {
                client.indices().delete(r -> r.index(indexName));
                log.info("Elasticsearch index '{}' deleted at {}", indexName, endpointUrl);
            } else {
                log.debug("Elasticsearch index '{}' not found, skipping delete", indexName);
            }
        } catch (IOException e) {
            log.error("Failed to delete Elasticsearch index '{}' at {}: {}", indexName, endpointUrl,
                    e.getMessage(), e);
        }
    }

    /**
     * Lists all non-hidden Elasticsearch indices with their document counts.
     *
     * @param endpointUrl the Elasticsearch endpoint URL
     * @return list of index info (name + doc count), empty list on error
     */
    public static List<TurSECoreInfo> listIndexes(String endpointUrl) {
        List<TurSECoreInfo> result = new ArrayList<>();
        try (RestClient restClient = buildRestClient(endpointUrl);
             RestClientTransport transport = new RestClientTransport(restClient, new JacksonJsonpMapper());
             ElasticsearchClient client = new ElasticsearchClient(transport)) {
            IndicesResponse catResponse = client.cat().indices();
            for (IndicesRecord indicesRecord : catResponse.indices()) {
                String indexName = indicesRecord.index();
                if (indexName != null && !indexName.startsWith(".")) {
                    long docCount = 0;
                    if (indicesRecord.docsCount() != null) {
                        try {
                            docCount = Long.parseLong(indicesRecord.docsCount());
                        } catch (NumberFormatException ignored) {
                            // keep 0
                        }
                    }
                    result.add(new TurSECoreInfo(indexName, docCount, List.of()));
                }
            }
        } catch (IOException e) {
            log.error("Failed to list Elasticsearch indices at {}: {}", endpointUrl, e.getMessage(), e);
        }
        return result;
    }

    private static RestClient buildRestClient(String endpointUrl) {
        URI uri = URI.create(endpointUrl);
        HttpHost httpHost = new HttpHost(uri.getHost(), uri.getPort(), uri.getScheme());
        return RestClient.builder(httpHost)
                .setHttpClientConfigCallback(clientBuilder -> {
                    clientBuilder.addInterceptorLast((HttpRequestInterceptor) (request, context) -> {
                        request.removeHeaders("Content-Type");
                        request.removeHeaders("Accept");
                        request.setHeader("Content-Type", "application/json");
                        request.setHeader("Accept", "application/json");
                    });
                    return clientBuilder;
                })
                .build();
    }
}
