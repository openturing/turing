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
package com.viglet.turing.api.ann;

import java.io.IOException;
import java.io.InputStream;

import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

import com.viglet.turing.domain.sn.TurSNSiteRepositoryPort;

import lombok.extern.slf4j.Slf4j;

/**
 * Serves the ANN (Approximate Nearest Neighbor) search frontend for SN sites.
 * <p>
 * Always returns the default React app shell — no SPA template branching.
 * Returns {@code 404} when the target site does not exist or has GenAI/RAG
 * disabled, gating the page itself by the same condition that hides the
 * admin-list button.
 *
 * @author Alexandre Oliveira
 * @since 2026.2.4
 */
@Slf4j
@Controller
@RequestMapping("/ann")
public class TurAnnSearchController {

    private static final ClassPathResource DEFAULT_INDEX = new ClassPathResource("/public/index.html");

    private final TurSNSiteRepositoryPort turSNSiteRepositoryPort;

    public TurAnnSearchController(TurSNSiteRepositoryPort turSNSiteRepositoryPort) {
        this.turSNSiteRepositoryPort = turSNSiteRepositoryPort;
    }

    @GetMapping("/{siteName}")
    public ResponseEntity<?> serve(@PathVariable String siteName) {
        if (!turSNSiteRepositoryPort.hasRagEnabledForSiteName(siteName)) {
            log.debug("ANN page requested for site '{}' but RAG is not enabled", siteName);
            return ResponseEntity.notFound().build();
        }
        return serveEmbeddedIndex();
    }

    private ResponseEntity<?> serveEmbeddedIndex() {
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
