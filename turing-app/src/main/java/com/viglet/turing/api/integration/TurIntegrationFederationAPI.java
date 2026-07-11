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

import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.protocol.HttpClientContext;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.io.support.ClassicRequestBuilder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.viglet.turing.persistence.model.integration.TurIntegrationInstance;
import com.viglet.turing.persistence.repository.integration.TurIntegrationInstanceRepository;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;

/**
 * Proxy for Module Federation assets from integration endpoints.
 * <p>
 * Maps {@code /api/v2/integration/{id}/federation/**} to the remote
 * endpoint's root, allowing the frontend to load {@code remoteEntry.js}
 * and other federated chunks without direct browser access to the
 * integration server.
 *
 * @author Alexandre Oliveira
 * @since 2026.2.4
 */
@Slf4j
@RestController
@RequestMapping("/api/v2/integration/{integrationId}/federation")
@Tag(name = "Integration Federation Proxy", description = "Proxies Module Federation assets from integration endpoints")
public class TurIntegrationFederationAPI {

    private static final String PREFIX = "/api/v2/integration/";
    private static final String FEDERATION_SEGMENT = "/federation/";

    private final TurIntegrationInstanceRepository integrationInstanceRepository;
    private final CloseableHttpClient proxyHttpClient;
    private final com.viglet.turing.spring.security.ssrf.TurSsrfGuard ssrfGuard;

    TurIntegrationFederationAPI(TurIntegrationInstanceRepository integrationInstanceRepository,
            CloseableHttpClient proxyHttpClient,
            com.viglet.turing.spring.security.ssrf.TurSsrfGuard ssrfGuard) {
        this.integrationInstanceRepository = integrationInstanceRepository;
        this.proxyHttpClient = proxyHttpClient;
        this.ssrfGuard = ssrfGuard;
    }

    @GetMapping("**")
    public void proxyFederationAsset(HttpServletRequest request, HttpServletResponse response,
            @PathVariable String integrationId) {
        integrationInstanceRepository.findById(integrationId).ifPresentOrElse(
                instance -> proxyAsset(instance, request, response),
                () -> {
                    response.setStatus(HttpServletResponse.SC_NOT_FOUND);
                    try {
                        response.getWriter().write("{\"error\": \"Integration not found\"}");
                    } catch (Exception e) {
                        log.error("Error writing 404 response: {}", e.getMessage(), e);
                    }
                });
    }

    private void proxyAsset(TurIntegrationInstance instance, HttpServletRequest request,
            HttpServletResponse response) {
        String requestUri = request.getRequestURI();
        String federationPrefix = PREFIX + instance.getId() + FEDERATION_SEGMENT;
        int prefixIndex = requestUri.indexOf(federationPrefix);
        if (prefixIndex < 0) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            return;
        }

        String relativePath = requestUri.substring(prefixIndex + federationPrefix.length());

        if (!isValidAssetPath(relativePath)) {
            log.warn("Blocked invalid federation asset path: {}", relativePath);
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            return;
        }

        String endpoint = instance.getEndpoint().replaceAll("/+$", "");
        String targetUrl = endpoint + "/" + relativePath;

        String queryString = request.getQueryString();
        if (queryString != null) {
            targetUrl += "?" + queryString;
        }

        try {
            URI targetUri = URI.create(targetUrl);
            URI baseUri = URI.create(endpoint);

            if (!baseUri.getHost().equalsIgnoreCase(targetUri.getHost())
                    || !baseUri.getScheme().equalsIgnoreCase(targetUri.getScheme())) {
                log.warn("Blocked SSRF attempt in federation proxy: {}", targetUri);
                response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                return;
            }

            // T643 / §XXXVII.5 — the host==base string check does not stop a
            // configured endpoint whose host resolves to an internal address
            // (DNS rebinding / an internal-only integration host). Resolve and
            // gate the target through the central egress guard.
            if (!ssrfGuard.isAllowedUrl(targetUri.toString())) {
                log.warn("Blocked federation proxy target by SSRF guard: {}", targetUri);
                response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                return;
            }

            ClassicHttpRequest proxyRequest = ClassicRequestBuilder.get(targetUri).build();

            // T643 / §XXXVII.5 — do NOT follow redirects: a trusted endpoint that
            // 302s to an internal address would otherwise be fetched server-side,
            // bypassing the host/address checks above. Disabled per-request via the
            // context so the shared pooled client is untouched.
            HttpClientContext proxyContext = HttpClientContext.create();
            proxyContext.setRequestConfig(RequestConfig.custom().setRedirectsEnabled(false).build());

            proxyHttpClient.execute(proxyRequest, proxyContext, proxyResponse -> {
                response.setStatus(proxyResponse.getCode());

                for (Header header : proxyResponse.getHeaders()) {
                    String name = header.getName();
                    if (name.equalsIgnoreCase("Content-Type")
                            || name.equalsIgnoreCase("Content-Length")
                            || name.equalsIgnoreCase("Cache-Control")
                            || name.equalsIgnoreCase("ETag")
                            || name.equalsIgnoreCase("Last-Modified")) {
                        response.setHeader(name, header.getValue());
                    }
                }

                if (proxyResponse.getEntity() != null) {
                    proxyResponse.getEntity().writeTo(response.getOutputStream());
                }
                return null;
            });

        } catch (Exception e) {
            log.error("Federation proxy error: {}", e.getMessage(), e);
            response.setStatus(HttpServletResponse.SC_BAD_GATEWAY);
        }
    }

    private boolean isValidAssetPath(String path) {
        if (path == null || path.isEmpty()) {
            return false;
        }
        if (path.contains("..")) {
            return false;
        }
        for (int i = 0; i < path.length(); i++) {
            char c = path.charAt(i);
            if (!(Character.isLetterOrDigit(c) || c == '/' || c == '-' || c == '_' || c == '.')) {
                return false;
            }
        }
        return true;
    }
}
