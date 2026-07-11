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

package com.viglet.turing.sn.migration;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import lombok.extern.slf4j.Slf4j;

/**
 * Minimal Elasticsearch REST reader for the migration importer (T657 / §XXXVIII.1).
 *
 * <p>Talks to a source cluster over plain HTTP with {@code java.net.http.HttpClient}
 * — no vendor SDK — so it works identically against Elastic Cloud and a
 * self-managed cluster. It reads a {@code _mapping} and paginates the whole index
 * with the Scroll API ({@code _search?scroll} &rarr; {@code _search/scroll} &rarr;
 * {@code DELETE _search/scroll}). Basic auth (username/password) or an
 * Elasticsearch API key are both supported.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Slf4j
@Component
public class TurElasticsearchClient {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final ObjectMapper objectMapper = JsonMapper.builder().build();

    /** Source-cluster connection details. */
    public record TurEsConnection(String baseUrl, String username, String password, String apiKey,
            int timeoutSeconds) {
    }

    /** One document from a search hit. */
    public record EsHit(String id, Map<String, Object> source) {
    }

    /** One page of a scroll: the (rolling) scroll id and its hits. */
    public record EsPage(String scrollId, List<EsHit> hits) {
    }

    /** {@code GET /{index}/_mapping}. */
    public Map<String, Object> fetchMapping(TurEsConnection conn, String index) {
        String url = base(conn) + "/" + encode(index) + "/_mapping";
        return sendJson(conn, HttpRequest.newBuilder(URI.create(url)).GET());
    }

    /**
     * Runs a keyword query and returns the ordered ids of the top {@code size}
     * hits — the source side of the shadow comparison (T661).
     */
    public List<String> search(TurEsConnection conn, String index, String query, int size) {
        String url = base(conn) + "/" + encode(index) + "/_search";
        String q = jsonString(query);
        String body = "{\"size\":" + size + ",\"_source\":false,"
                + "\"query\":{\"query_string\":{\"query\":" + q + "}}}";
        Map<String, Object> response = sendJson(conn,
                HttpRequest.newBuilder(URI.create(url)).POST(HttpRequest.BodyPublishers.ofString(body))
                        .header("Content-Type", "application/json"));
        List<String> ids = new ArrayList<>();
        for (EsHit hit : toPage(response).hits()) {
            if (hit.id() != null) {
                ids.add(hit.id());
            }
        }
        return ids;
    }

    /** Opens a scroll over the whole index ({@code match_all}) and returns the first page. */
    public EsPage openScroll(TurEsConnection conn, String index, int size, String keepAlive) {
        String url = base(conn) + "/" + encode(index) + "/_search?scroll=" + encode(keepAlive);
        String body = "{\"size\":" + size + ",\"query\":{\"match_all\":{}},\"sort\":[\"_doc\"]}";
        Map<String, Object> response = sendJson(conn,
                HttpRequest.newBuilder(URI.create(url)).POST(HttpRequest.BodyPublishers.ofString(body))
                        .header("Content-Type", "application/json"));
        return toPage(response);
    }

    /** Fetches the next page of an open scroll. */
    public EsPage nextScroll(TurEsConnection conn, String scrollId, String keepAlive) {
        String url = base(conn) + "/_search/scroll";
        String body = "{\"scroll\":\"" + keepAlive + "\",\"scroll_id\":\"" + scrollId + "\"}";
        Map<String, Object> response = sendJson(conn,
                HttpRequest.newBuilder(URI.create(url)).POST(HttpRequest.BodyPublishers.ofString(body))
                        .header("Content-Type", "application/json"));
        return toPage(response);
    }

    /** Best-effort release of the scroll context on the source cluster. */
    public void clearScroll(TurEsConnection conn, String scrollId) {
        if (!StringUtils.hasText(scrollId)) {
            return;
        }
        try {
            String url = base(conn) + "/_search/scroll";
            String body = "{\"scroll_id\":[\"" + scrollId + "\"]}";
            HttpRequest request = auth(conn,
                    HttpRequest.newBuilder(URI.create(url))
                            .method("DELETE", HttpRequest.BodyPublishers.ofString(body))
                            .header("Content-Type", "application/json"))
                    .timeout(Duration.ofSeconds(conn.timeoutSeconds())).build();
            client(conn).send(request, HttpResponse.BodyHandlers.discarding());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            log.debug("Could not clear Elasticsearch scroll (non-fatal): {}", e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private EsPage toPage(Map<String, Object> response) {
        String scrollId = asString(response.get("_scroll_id"));
        List<EsHit> hits = new ArrayList<>();
        if (response.get("hits") instanceof Map<?, ?> hitsWrap
                && ((Map<String, Object>) hitsWrap).get("hits") instanceof List<?> hitList) {
            for (Object raw : hitList) {
                if (raw instanceof Map<?, ?> hit) {
                    Map<String, Object> hitMap = (Map<String, Object>) hit;
                    Object source = hitMap.get("_source");
                    hits.add(new EsHit(asString(hitMap.get("_id")),
                            source instanceof Map<?, ?> s ? (Map<String, Object>) s : Map.of()));
                }
            }
        }
        return new EsPage(scrollId, hits);
    }

    private Map<String, Object> sendJson(TurEsConnection conn, HttpRequest.Builder builder) {
        HttpRequest request = auth(conn, builder).timeout(Duration.ofSeconds(conn.timeoutSeconds())).build();
        try {
            HttpResponse<String> response = client(conn).send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2) {
                throw new TurMigrationException("Elasticsearch responded " + response.statusCode() + " for "
                        + request.uri() + ": " + snippet(response.body()));
            }
            return objectMapper.readValue(response.body(), MAP_TYPE);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new TurMigrationException("Interrupted while calling Elasticsearch: " + request.uri(), e);
        } catch (TurMigrationException e) {
            throw e;
        } catch (Exception e) {
            throw new TurMigrationException("Failed to call Elasticsearch: " + request.uri()
                    + " (" + e.getMessage() + ")", e);
        }
    }

    private HttpClient client(TurEsConnection conn) {
        return HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(conn.timeoutSeconds()))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    private HttpRequest.Builder auth(TurEsConnection conn, HttpRequest.Builder builder) {
        if (StringUtils.hasText(conn.apiKey())) {
            return builder.header("Authorization", "ApiKey " + conn.apiKey());
        }
        if (StringUtils.hasText(conn.username())) {
            String token = conn.username() + ":" + (conn.password() == null ? "" : conn.password());
            return builder.header("Authorization",
                    "Basic " + Base64.getEncoder().encodeToString(token.getBytes(StandardCharsets.UTF_8)));
        }
        return builder;
    }

    private static String base(TurEsConnection conn) {
        if (!StringUtils.hasText(conn.baseUrl())) {
            throw new TurMigrationException("Elasticsearch URL is required");
        }
        String url = conn.baseUrl().trim();
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    /** JSON-encodes a string value (with surrounding quotes), so a query with quotes/backslashes is safe. */
    private String jsonString(String value) {
        return objectMapper.writeValueAsString(value == null ? "" : value);
    }

    private static String asString(Object value) {
        return value == null ? null : value.toString();
    }

    private static String snippet(String body) {
        if (body == null) {
            return "";
        }
        return body.length() > 400 ? body.substring(0, 400) + "…" : body;
    }
}
