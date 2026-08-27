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
package com.viglet.turing.contentai;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import com.viglet.turing.properties.TurContentAiProperty;

import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * T1031 / §XX.18 — the wire for Adobe's Content AI search API.
 *
 * <p>One POST, one credential, one parse. The credential is a bearer token or an
 * {@code x-api-key}; the spec allows either on the search endpoints and a
 * deployment configures whichever its Adobe project issued.
 *
 * <p>Two details of the endpoint are worth carrying in code rather than in a
 * runbook. The base URL Adobe publishes is versioned and
 * <em>dated</em> — {@code /adobe/experimental/aemcontentai-expires-20261231/contentAI} —
 * so the SE instance's endpoint URL is used verbatim and never rebuilt from
 * parts; whatever an operator pasted is what gets called. And the API answers a
 * deprecation with a {@code Sunset} header, which is logged at WARN the moment
 * it appears, because the alternative is discovering the shutdown from a support
 * ticket.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Slf4j
@Component
public class TurContentAiClient {

    /** The retrieval endpoint, appended to the SE instance's endpoint URL. */
    public static final String SEARCH_PATH = "/content-sources/search";

    private static final String HEADER_SUNSET = "Sunset";

    private final ObjectMapper objectMapper = JsonMapper.builder().build();

    /**
     * One client for the process. The per-request timeout below is what bounds a
     * slow Adobe; the connect timeout is fixed because it is a property of the
     * network rather than of the query.
     */
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    /**
     * Executes one search against {@code baseUrl}.
     *
     * @param baseUrl  the SE instance endpoint, e.g.
     *                 {@code https://author-p1-e1.adobeaemcloud.com/adobe/experimental/aemcontentai-expires-20261231/contentAI}
     * @param body     the request body built by {@link TurContentAiQueryBuilder}
     * @param property configuration carrying the credential and timeout
     * @return the parsed response
     * @throws TurContentAiException on a missing credential, a transport failure,
     *                               a non-2xx status or an unparseable body — the
     *                               plugin turns this into an empty
     *                               {@code Optional} so a federated site degrades
     *                               to "no results" instead of a 500
     */
    public JsonNode search(String baseUrl, ObjectNode body, TurContentAiProperty property) {
        String credential = credential(property);
        String url = stripTrailingSlash(baseUrl) + SEARCH_PATH;
        Duration timeout = Duration.ofSeconds(Math.max(1, property.getTimeoutSeconds()));

        HttpRequest.Builder request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(timeout)
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)));
        if (StringUtils.isNotBlank(property.getToken())) {
            request.header("Authorization", "Bearer " + property.getToken());
        } else {
            request.header("x-api-key", credential);
        }

        try {
            HttpResponse<String> response =
                    httpClient.send(request.build(), HttpResponse.BodyHandlers.ofString());
            response.headers().firstValue(HEADER_SUNSET).ifPresent(sunset -> log.warn(
                    "Content AI announced a Sunset of {} for {} — the experimental API this "
                            + "plugin federates to is being retired", sunset, url));
            if (response.statusCode() / 100 != 2) {
                throw new TurContentAiException("Content AI returned HTTP %d for %s: %s"
                        .formatted(response.statusCode(), url,
                                StringUtils.abbreviate(response.body(), 500)));
            }
            return objectMapper.readTree(response.body());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new TurContentAiException("Interrupted while calling Content AI at " + url, e);
        } catch (TurContentAiException e) {
            throw e;
        } catch (Exception e) {
            throw new TurContentAiException("Content AI call to " + url + " failed", e);
        }
    }

    private static String stripTrailingSlash(String url) {
        String value = StringUtils.trimToEmpty(url);
        while (value.endsWith("/")) {
            value = value.substring(0, value.length() - 1);
        }
        return value;
    }

    /**
     * The credential, or a refusal. A blank one is not degraded into an
     * anonymous request: the endpoint requires auth, so the only thing an
     * unauthenticated call can produce is a 401 that reads like an outage.
     */
    private String credential(TurContentAiProperty property) {
        if (StringUtils.isNotBlank(property.getToken())) {
            return property.getToken();
        }
        if (StringUtils.isNotBlank(property.getApiKey())) {
            return property.getApiKey();
        }
        throw new TurContentAiException(
                "No Content AI credential configured — set turing.content-ai.token "
                        + "or turing.content-ai.api-key");
    }

    /** Raised for every Content AI failure so the plugin has one thing to catch. */
    public static class TurContentAiException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        public TurContentAiException(String message) {
            super(message);
        }

        public TurContentAiException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
