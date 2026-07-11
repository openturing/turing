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
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import lombok.extern.slf4j.Slf4j;

/**
 * Minimal Algolia REST reader for the migration importer (T658 / §XXXVIII.2).
 *
 * <p>Reads index settings, browses every record with the cursor API, and lists
 * synonyms — over plain HTTP with {@code java.net.http.HttpClient}, no Algolia
 * SDK. Authenticated with the application id + a (read-capable) API key.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Slf4j
@Component
public class TurAlgoliaClient {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private static final String INDEXES_PATH = "/1/indexes/";

    private final ObjectMapper objectMapper = JsonMapper.builder().build();

    /**
     * Algolia connection details. {@code host} is optional — it defaults to
     * {@code {appId}-dsn.algolia.net} (the read-optimised replica host).
     */
    public record TurAlgoliaConnection(String appId, String apiKey, String host, int timeoutSeconds) {
    }

    /** One page of a browse: the hits and the cursor for the next page (null when exhausted). */
    public record AlgoliaBrowsePage(List<Map<String, Object>> hits, String cursor) {
    }

    /** {@code GET /1/indexes/{index}/settings}. */
    public Map<String, Object> fetchSettings(TurAlgoliaConnection conn, String index) {
        String url = base(conn) + INDEXES_PATH + encode(index) + "/settings";
        return sendJson(conn, HttpRequest.newBuilder(URI.create(url)).GET());
    }

    /**
     * Runs a query and returns the ordered {@code objectID}s of the top
     * {@code hitsPerPage} hits — the source side of the shadow comparison (T661).
     */
    public List<String> search(TurAlgoliaConnection conn, String index, String query, int hitsPerPage) {
        String url = base(conn) + INDEXES_PATH + encode(index) + "/query";
        String body = "{\"query\":" + jsonString(query) + ",\"hitsPerPage\":" + hitsPerPage
                + ",\"attributesToRetrieve\":[\"objectID\"]}";
        Map<String, Object> response = sendJson(conn,
                HttpRequest.newBuilder(URI.create(url)).POST(HttpRequest.BodyPublishers.ofString(body))
                        .header("Content-Type", "application/json"));
        List<String> ids = new ArrayList<>();
        for (Map<String, Object> hit : hitsOf(response)) {
            Object objectId = hit.get("objectID");
            if (objectId != null) {
                ids.add(objectId.toString());
            }
        }
        return ids;
    }

    /**
     * Browses one page of records. Pass {@code null} for the first page, then the
     * cursor returned by the previous page.
     */
    public AlgoliaBrowsePage browse(TurAlgoliaConnection conn, String index, String cursor, int hitsPerPage) {
        String url = base(conn) + INDEXES_PATH + encode(index) + "/browse";
        String body = cursor == null
                ? "{\"hitsPerPage\":" + hitsPerPage + "}"
                : "{\"cursor\":\"" + cursor + "\"}";
        Map<String, Object> response = sendJson(conn,
                HttpRequest.newBuilder(URI.create(url)).POST(HttpRequest.BodyPublishers.ofString(body))
                        .header("Content-Type", "application/json"));
        return toBrowsePage(response);
    }

    /**
     * Lists an index's synonyms (best-effort — returns an empty list when the key
     * lacks synonym access, since synonyms are surfaced, not required).
     */
    public List<Map<String, Object>> fetchSynonyms(TurAlgoliaConnection conn, String index) {
        try {
            String url = base(conn) + INDEXES_PATH + encode(index) + "/synonyms/search";
            Map<String, Object> response = sendJson(conn,
                    HttpRequest.newBuilder(URI.create(url))
                            .POST(HttpRequest.BodyPublishers.ofString("{\"query\":\"\",\"hitsPerPage\":1000}"))
                            .header("Content-Type", "application/json"));
            return hitsOf(response);
        } catch (TurMigrationException e) {
            log.debug("Could not list Algolia synonyms (non-fatal): {}", e.getMessage());
            return List.of();
        }
    }

    private AlgoliaBrowsePage toBrowsePage(Map<String, Object> response) {
        return new AlgoliaBrowsePage(hitsOf(response), asString(response.get("cursor")));
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> hitsOf(Map<String, Object> response) {
        List<Map<String, Object>> hits = new ArrayList<>();
        if (response.get("hits") instanceof List<?> list) {
            for (Object raw : list) {
                if (raw instanceof Map<?, ?> hit) {
                    hits.add((Map<String, Object>) hit);
                }
            }
        }
        return hits;
    }

    private Map<String, Object> sendJson(TurAlgoliaConnection conn, HttpRequest.Builder builder) {
        HttpRequest request = auth(conn, builder).timeout(Duration.ofSeconds(conn.timeoutSeconds())).build();
        try {
            HttpResponse<String> response = client(conn).send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2) {
                throw new TurMigrationException("Algolia responded " + response.statusCode() + " for "
                        + request.uri() + ": " + snippet(response.body()));
            }
            return objectMapper.readValue(response.body(), MAP_TYPE);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new TurMigrationException("Interrupted while calling Algolia: " + request.uri(), e);
        } catch (TurMigrationException e) {
            throw e;
        } catch (Exception e) {
            throw new TurMigrationException("Failed to call Algolia: " + request.uri()
                    + " (" + e.getMessage() + ")", e);
        }
    }

    private HttpClient client(TurAlgoliaConnection conn) {
        return HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(conn.timeoutSeconds()))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    private HttpRequest.Builder auth(TurAlgoliaConnection conn, HttpRequest.Builder builder) {
        return builder
                .header("X-Algolia-Application-Id", conn.appId() == null ? "" : conn.appId())
                .header("X-Algolia-API-Key", conn.apiKey() == null ? "" : conn.apiKey());
    }

    private static String base(TurAlgoliaConnection conn) {
        if (StringUtils.hasText(conn.host())) {
            String host = conn.host().trim();
            String withScheme = host.startsWith("http") ? host : "https://" + host;
            return withScheme.endsWith("/") ? withScheme.substring(0, withScheme.length() - 1) : withScheme;
        }
        if (!StringUtils.hasText(conn.appId())) {
            throw new TurMigrationException("Algolia application id is required");
        }
        return "https://" + conn.appId() + "-dsn.algolia.net";
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
