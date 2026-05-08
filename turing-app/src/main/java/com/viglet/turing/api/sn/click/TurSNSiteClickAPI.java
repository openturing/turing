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
package com.viglet.turing.api.sn.click;

import java.time.Instant;
import java.util.Optional;

import org.apache.commons.lang3.LocaleUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.viglet.turing.persistence.model.sn.TurSNSite;
import com.viglet.turing.persistence.model.sn.metric.TurSNSiteMetricClick;
import com.viglet.turing.persistence.repository.sn.TurSNSiteRepository;
import com.viglet.turing.persistence.repository.sn.metric.TurSNSiteMetricClickRepository;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;

/**
 * Records a click event tied to a previous search. Called by the React SDK
 * (or any consumer) when a user opens a result returned by
 * {@code /api/sn/{siteName}/search}, so dashboards can compute CTR per term.
 *
 * <p>The endpoint is intentionally unauthenticated and side-effect-only —
 * matching the public {@code /search} endpoint that produced the result the
 * user clicked. The handler returns 202 Accepted on success so the SDK can
 * fire-and-forget without blocking navigation; persistence happens before
 * the response, but the client is not expected to retry on failure.
 *
 * <p>Toggled by the same {@code turing.search.metrics.enabled} property that
 * gates {@code TurSNSiteMetricAccess} writes — disabling search metrics
 * disables click metrics too.
 *
 * @author Alexandre Oliveira
 * @since 2026.2.7
 */
@Slf4j
@RestController
@RequestMapping("/api/sn/{siteName}/click")
@Tag(name = "Semantic Navigation Click",
        description = "Records click-throughs on search results so CTR can be computed per term.")
public class TurSNSiteClickAPI {

    private final TurSNSiteRepository turSNSiteRepository;
    private final TurSNSiteMetricClickRepository turSNSiteMetricClickRepository;
    private final boolean metricsEnabled;

    public TurSNSiteClickAPI(TurSNSiteRepository turSNSiteRepository,
            TurSNSiteMetricClickRepository turSNSiteMetricClickRepository,
            @Value("${turing.search.metrics.enabled:false}") boolean metricsEnabled) {
        this.turSNSiteRepository = turSNSiteRepository;
        this.turSNSiteMetricClickRepository = turSNSiteMetricClickRepository;
        this.metricsEnabled = metricsEnabled;
    }

    @PostMapping
    public ResponseEntity<Void> trackClick(@PathVariable String siteName,
            @RequestBody TurSNClickRequest request) {
        if (!metricsEnabled) {
            // Silently accept so the SDK has the same shape regardless of
            // server-side configuration; the client never knows it's disabled.
            return ResponseEntity.accepted().build();
        }
        if (request == null || StringUtils.isBlank(request.documentId())) {
            return ResponseEntity.badRequest().build();
        }
        Optional<TurSNSite> siteOpt = turSNSiteRepository.findByNameIgnoreCase(siteName);
        if (siteOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        TurSNSiteMetricClick click = new TurSNSiteMetricClick();
        click.setAccessDate(Instant.now());
        click.setTurSNSite(siteOpt.get());
        click.setTerm(StringUtils.defaultIfBlank(request.term(), ""));
        click.setDocumentId(request.documentId());
        click.setPosition(Math.max(0, request.position()));
        click.setUserId(StringUtils.trimToNull(request.userId()));
        click.setLanguage(parseLocale(request.locale()));
        try {
            turSNSiteMetricClickRepository.save(click);
        } catch (RuntimeException e) {
            log.warn("Failed to persist click event for site '{}': {}", siteName, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
        return ResponseEntity.accepted().build();
    }

    private static java.util.Locale parseLocale(String raw) {
        if (StringUtils.isBlank(raw)) {
            return null;
        }
        try {
            return LocaleUtils.toLocale(raw);
        } catch (IllegalArgumentException e) {
            // Common when SDK passes BCP-47 ("pt-BR") instead of POSIX ("pt_BR").
            return Optional.ofNullable(LocaleUtils.toLocale(raw.replace('-', '_'))).orElse(null);
        }
    }

    /**
     * Payload sent by the SDK. {@code term} is the query that produced the
     * result; {@code documentId} is whatever id the search engine returned for
     * the clicked document; {@code position} is the 1-based rank within the
     * results page. {@code userId} and {@code locale} are optional.
     */
    public record TurSNClickRequest(String term, String documentId, int position,
            String userId, String locale) {}
}
