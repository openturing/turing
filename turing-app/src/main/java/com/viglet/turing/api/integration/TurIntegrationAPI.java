/*
 * Copyright (C) 2016-2024 the original author or authors.
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
package com.viglet.turing.api.integration;

import java.net.URI;
import java.nio.file.Paths;

import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.io.entity.InputStreamEntity;
import org.apache.hc.core5.http.io.support.ClassicRequestBuilder;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import com.viglet.turing.persistence.model.integration.TurIntegrationInstance;
import com.viglet.turing.persistence.repository.integration.TurIntegrationInstanceRepository;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequestMapping("/api/v2/integration/{integrationId}")
@Tag(name = "Integration API", description = "Integration API")
public class TurIntegrationAPI {
    private final TurIntegrationInstanceRepository turIntegrationInstanceRepository;
    private final CloseableHttpClient proxyHttpClient;

    TurIntegrationAPI(TurIntegrationInstanceRepository turIntegrationInstanceRepository,
            CloseableHttpClient proxyHttpClient) {
        this.turIntegrationInstanceRepository = turIntegrationInstanceRepository;
        this.proxyHttpClient = proxyHttpClient;
    }

    @SuppressWarnings("java:S3752")
    @RequestMapping(value = "**", method = { RequestMethod.GET, RequestMethod.POST,
            RequestMethod.PUT, RequestMethod.DELETE }, produces = { MediaType.APPLICATION_JSON_VALUE })
    public void indexAnyRequest(HttpServletRequest request, HttpServletResponse response,
            @PathVariable String integrationId) {
        turIntegrationInstanceRepository.findById(integrationId).ifPresent(
                turIntegrationInstance -> proxy(turIntegrationInstance, request, response));
    }

    private void proxy(TurIntegrationInstance turIntegrationInstance, HttpServletRequest request,
            HttpServletResponse response) {
        URI baseUri = URI.create(turIntegrationInstance.getEndpoint());
        String relativePath = request.getRequestURI()
                .replace("/api/v2/integration/" + turIntegrationInstance.getId(), "/api/v2");
        URI fullUri = baseUri.resolve(relativePath);
        log.debug("Executing: {}", fullUri);

        try {
            if (!isAllowedProxyTarget(baseUri, fullUri, response)) {
                return;
            }

            String proxiedPath = fullUri.getPath();
            if (!isValidProxyPath(proxiedPath) || !proxiedPath.startsWith("/api/v2")) {
                log.warn("Blocked SSRF attempt: invalid or unauthorized path: {}", proxiedPath);
                response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                response.getWriter().write("{\"error\": \"Forbidden proxy path\"}");
                return;
            }

            ClassicHttpRequest proxyRequest = ClassicRequestBuilder.create(request.getMethod())
                    .setUri(fullUri)
                    .setEntity(new InputStreamEntity(request.getInputStream(), ContentType.APPLICATION_JSON))
                    .build();

            copyRequestHeaders(request, proxyRequest);

            proxyHttpClient.execute(proxyRequest, proxyResponse -> {
                response.setStatus(proxyResponse.getCode());
                copyResponseHeaders(proxyResponse, response);

                if (proxyResponse.getEntity() != null) {
                    proxyResponse.getEntity().writeTo(response.getOutputStream());
                }
                return null;
            });

        } catch (Exception e) {
            log.error("Proxy Error: {}", e.getMessage(), e);
            response.setStatus(HttpServletResponse.SC_BAD_GATEWAY);
        }
    }

    private boolean isAllowedProxyTarget(URI baseUri, URI fullUri, HttpServletResponse response) {
        if (!baseUri.getHost().equalsIgnoreCase(fullUri.getHost())
                || !baseUri.getScheme().equalsIgnoreCase(fullUri.getScheme())) {
            log.warn("Blocked SSRF attempt: attempted host={}, scheme={}", fullUri.getHost(),
                    fullUri.getScheme());
            try {
                response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                response.getWriter().write("{\"error\": \"Forbidden proxy target\"}");
            } catch (Exception e) {
                log.error("Error writing forbidden proxy target response: {}", e.getMessage(), e);
            }
            return false;
        }
        return true;
    }

    private void copyRequestHeaders(HttpServletRequest request, ClassicHttpRequest proxyRequest) {
        request.getHeaderNames().asIterator().forEachRemaining(h -> {
            if (!h.equalsIgnoreCase(HttpHeaders.CONTENT_LENGTH)) {
                proxyRequest.addHeader(h, request.getHeader(h));
            }
        });
    }

    private void copyResponseHeaders(org.apache.hc.core5.http.ClassicHttpResponse proxyResponse,
            HttpServletResponse response) {
        for (Header header : proxyResponse.getHeaders()) {
            String name = header.getName();
            boolean isCorsHeader = name.equalsIgnoreCase(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN) ||
                    name.equalsIgnoreCase(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS) ||
                    name.equalsIgnoreCase(HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS) ||
                    name.equalsIgnoreCase(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS) ||
                    name.equalsIgnoreCase(HttpHeaders.ACCESS_CONTROL_MAX_AGE);

            if (!name.equalsIgnoreCase(HttpHeaders.TRANSFER_ENCODING) && !isCorsHeader) {
                response.setHeader(name, header.getValue());
            }
        }
    }

    /**
     * Validates that the proxy path is safe: normalized, does not contain directory
     * traversal, and
     * starts with the expected API prefix.
     */
    private boolean isValidProxyPath(String path) {
        if (path == null) {
            return false;
        }
        // Normalize the path first to eliminate any ./ or ../ segments
        String normalized = Paths.get(path).normalize().toString().replace('\\', '/');

        // Ensure the normalized path still starts with the expected API prefix.
        // This prevents proxying to arbitrary internal endpoints.
        if (!normalized.startsWith("/api/")) {
            return false;
        }

        // Disallow any attempts at directory traversal in the normalized path
        if (normalized.contains("..")) {
            return false;
        }

        // Optionally, restrict characters in the path to a safe subset
        // (alphanumeric, slash, dash, underscore, dot).
        for (int i = 0; i < normalized.length(); i++) {
            char c = normalized.charAt(i);
            if (!(Character.isLetterOrDigit(c) || c == '/' || c == '-' || c == '_' || c == '.')) {
                return false;
            }
        }

        return true;
    }
}