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
package com.viglet.turing.api.page;

import com.viglet.turing.service.storage.TurStorageService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

import java.io.InputStream;

/**
 * Serves SPA static files from MinIO at {@code /pages/{siteName}/**}.
 * For SPA routing, any path that doesn't match a real file falls back to index.html.
 *
 * @author Alexandre Oliveira
 * @since 2026.2.4
 */
@Slf4j
@Controller
@RequestMapping("/pages")
public class TurPageController {

    private static final String PAGES_PREFIX = "public/";

    private final TurStorageService storageService;

    public TurPageController(TurStorageService storageService) {
        this.storageService = storageService;
    }

    @GetMapping({"/{siteName}/", "/{siteName}/**"})
    public ResponseEntity<InputStreamResource> serve(@PathVariable String siteName,
                                                     HttpServletRequest request) {
        if (!storageService.isEnabled()) {
            return ResponseEntity.notFound().build();
        }

        String fullPath = request.getRequestURI();
        String sitePrefix = "/pages/" + siteName + "/";
        String filePath = fullPath.startsWith(sitePrefix)
                ? fullPath.substring(sitePrefix.length())
                : "";

        if (filePath.isBlank()) {
            filePath = "index.html";
        }

        String objectName = PAGES_PREFIX + siteName + "/" + filePath;

        try {
            InputStream stream = storageService.downloadObject(objectName);
            String contentType = storageService.guessContentType(objectName);
            String cacheControl = "index.html".equals(filePath)
                    ? "no-cache, no-store, must-revalidate"
                    : "public, max-age=31536000, immutable";
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_TYPE, contentType)
                    .header(HttpHeaders.CACHE_CONTROL, cacheControl)
                    .body(new InputStreamResource(stream));
        } catch (Exception e) {
            // SPA fallback: serve index.html for client-side routing
            String indexObject = PAGES_PREFIX + siteName + "/index.html";
            try {
                InputStream indexStream = storageService.downloadObject(indexObject);
                return ResponseEntity.ok()
                        .contentType(MediaType.TEXT_HTML)
                        .body(new InputStreamResource(indexStream));
            } catch (Exception ex) {
                return ResponseEntity.notFound().build();
            }
        }
    }

    @GetMapping("/{siteName}")
    public ResponseEntity<Void> redirectToTrailingSlash(@PathVariable String siteName) {
        return ResponseEntity.status(301)
                .header(HttpHeaders.LOCATION, "/pages/" + siteName + "/")
                .build();
    }
}
