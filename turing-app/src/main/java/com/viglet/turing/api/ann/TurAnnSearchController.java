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

import com.viglet.turing.genai.TurDefaultAgentResolver;
import com.viglet.turing.persistence.model.sn.TurSNSite;
import com.viglet.turing.sn.TurSNSearchProcess;

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

    private final TurSNSearchProcess turSNSearchProcess;
    private final TurDefaultAgentResolver turDefaultAgentResolver;

    public TurAnnSearchController(TurSNSearchProcess turSNSearchProcess,
            TurDefaultAgentResolver turDefaultAgentResolver) {
        this.turSNSearchProcess = turSNSearchProcess;
        this.turDefaultAgentResolver = turDefaultAgentResolver;
    }

    @GetMapping("/{siteName}")
    public ResponseEntity<Object> serve(@PathVariable String siteName) {
        // T622 — gate by the effective agent (site's own, else the global
        // Default AI Agent) so the page loads for zero-config / default-agent
        // sites, matching the search API and the admin launch-bar chip.
        TurSNSite site = turSNSearchProcess.getSNSite(siteName).orElse(null);
        if (site == null || !turDefaultAgentResolver.isRagReady(site.getTurSNSiteGenAi())) {
            log.debug("ANN page requested for site '{}' but RAG is not enabled", siteName);
            return ResponseEntity.notFound().build();
        }
        return serveEmbeddedIndex();
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
