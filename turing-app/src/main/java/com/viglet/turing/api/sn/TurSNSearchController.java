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
package com.viglet.turing.api.sn;

import com.viglet.turing.domain.sn.TurSNSiteDomain;
import com.viglet.turing.domain.sn.TurSNSiteRepositoryPort;
import com.viglet.turing.service.storage.TurStorageService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

import java.io.IOException;
import java.io.InputStream;

/**
 * Serves the search frontend for Semantic Navigation sites.
 * <p>
 * When a site has a {@code searchTemplate} configured (pointing to a SPA page),
 * the SPA is served from storage. Otherwise, the default built-in search
 * (React admin app) is served.
 *
 * @author Alexandre Oliveira
 * @since 2026.2.4
 */
@Slf4j
@Controller
@RequestMapping("/sn")
public class TurSNSearchController {

    // --- S1192: extracted duplicated literals ---
    private static final String INDEX_HTML = "index.html";


    private static final String PAGES_PREFIX = "public/";
    private static final ClassPathResource DEFAULT_INDEX = new ClassPathResource("/public/index.html");

    private final TurSNSiteRepositoryPort turSNSiteRepositoryPort;
    private final TurStorageService storageService;

    public TurSNSearchController(TurSNSiteRepositoryPort turSNSiteRepositoryPort,
                                 TurStorageService storageService) {
        this.turSNSiteRepositoryPort = turSNSiteRepositoryPort;
        this.storageService = storageService;
    }

    @GetMapping("/{siteName}")
    public ResponseEntity<Object> serveWithoutSlash(@PathVariable String siteName,
                                               HttpServletRequest request) {
        if (isEmbeddedRequested(request)) {
            return serveEmbeddedIndex();
        }
        // If site has a template, redirect to trailing slash so relative asset paths resolve correctly
        // e.g. ./assets/index.css resolves to /sn/mythical-creatures/assets/index.css (not /sn/assets/index.css)
        boolean hasTemplate = turSNSiteRepositoryPort.findByName(siteName)
                .map(TurSNSiteDomain::hasSearchTemplate)
                .orElse(false);
        if (hasTemplate && storageService.isEnabled()) {
            String query = request.getQueryString();
            String redirect = "/sn/" + siteName + "/" + (query != null ? "?" + query : "");
            return ResponseEntity.status(301)
                    .header(HttpHeaders.LOCATION, redirect)
                    .build();
        }
        return serveEmbeddedIndex();
    }

    @GetMapping("/{siteName}/")
    public ResponseEntity<Object> serveRoot(@PathVariable String siteName,
                                       HttpServletRequest request) {
        return serve(siteName, "", request);
    }

    @GetMapping("/{siteName}/**")
    public ResponseEntity<Object> serveSubPath(@PathVariable String siteName,
                                          HttpServletRequest request) {
        String fullPath = request.getRequestURI();
        String sitePrefix = "/sn/" + siteName + "/";
        String filePath = fullPath.startsWith(sitePrefix)
                ? fullPath.substring(sitePrefix.length())
                : "";
        return serve(siteName, filePath, request);
    }

    private static boolean isEmbeddedRequested(HttpServletRequest request) {
        return "true".equals(request.getParameter("_embedded"));
    }

    private ResponseEntity<Object> serve(String siteName, String filePath, HttpServletRequest request) {
        if (isEmbeddedRequested(request)) {
            return serveEmbeddedIndex();
        }

        String template = turSNSiteRepositoryPort.findByName(siteName)
                .filter(TurSNSiteDomain::hasSearchTemplate)
                .map(TurSNSiteDomain::searchTemplate)
                .orElse(null);

        if (template == null || !storageService.isEnabled()) {
            return serveEmbeddedIndex();
        }

        return serveFromStorage(template, filePath);
    }

    private ResponseEntity<Object> serveFromStorage(String template, String filePath) {
        if (filePath.isBlank()) {
            filePath = INDEX_HTML;
        }

        // Decode URL-encoded characters (e.g. %20 -> space)
        String decodedPath = java.net.URLDecoder.decode(filePath, java.nio.charset.StandardCharsets.UTF_8);
        if (decodedPath.isBlank()) {
            decodedPath = INDEX_HTML;
        }

        String objectName = PAGES_PREFIX + template + "/" + decodedPath;
        try {
            InputStream stream = storageService.downloadObject(objectName);
            String contentType = storageService.guessContentType(objectName);
            String cacheControl = decodedPath.equals(INDEX_HTML)
                    ? "no-cache, no-store, must-revalidate"
                    : "public, max-age=31536000, immutable";
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_TYPE, contentType)
                    .header(HttpHeaders.CACHE_CONTROL, cacheControl)
                    .body(new InputStreamResource(stream));
        } catch (Exception e) {
            log.debug("File not found in template '{}': {} (object={})", template, decodedPath, objectName);
            // SPA fallback: serve template's index.html for client-side routing
            String indexObject = PAGES_PREFIX + template + "/index.html";
            try {
                InputStream indexStream = storageService.downloadObject(indexObject);
                return ResponseEntity.ok()
                        .contentType(MediaType.TEXT_HTML)
                        .body(new InputStreamResource(indexStream));
            } catch (Exception ex) {
                log.debug("Template '{}' index.html not found in storage, falling back to default", template);
                return serveEmbeddedIndex();
            }
        }
    }

    private ResponseEntity<Object> serveEmbeddedIndex() {
        try {
            InputStream is = DEFAULT_INDEX.getInputStream();
            return ResponseEntity.ok()
                    .contentType(MediaType.TEXT_HTML)
                    .header("X-Turing-Embedded", "true")
                    .body(new InputStreamResource(is));
        } catch (IOException e) {
            return ResponseEntity.notFound().build();
        }
    }
}
