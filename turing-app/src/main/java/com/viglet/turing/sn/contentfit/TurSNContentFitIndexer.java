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
package com.viglet.turing.sn.contentfit;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import com.viglet.turing.client.sn.job.TurSNJobAttributeSpec;
import com.viglet.turing.commons.se.field.TurSEFieldType;
import com.viglet.turing.commons.sn.field.TurSNFieldName;
import com.viglet.turing.genai.persona.readability.TurReadabilityScorer;
import com.viglet.turing.persistence.model.persona.TurPersona;
import com.viglet.turing.persistence.model.sn.TurSNSite;
import com.viglet.turing.persistence.model.sn.genai.TurSNSiteGenAi;
import com.viglet.turing.persistence.repository.persona.TurPersonaRepository;
import com.viglet.turing.sn.field.TurSNFieldProvisioner;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * T472 / §XXVI.9 — turns the audience-fit evaluation into an <em>index-time</em>
 * Semantic Navigation signal. When a site opts in
 * ({@link TurSNSiteGenAi#contentFitIndexingEnabled} with a resolvable
 * target-audience persona), every document is scored as it is indexed by the
 * pure {@link TurReadabilityScorer} against that persona's audience facet, and
 * the resulting 0–100 readability/fit score is stored in the
 * {@value #FIELD_NAME} SN field. {@link TurSNContentFitCoverageService} then
 * surfaces "content too complex for its audience" alongside the T388
 * field-coverage dashboard.
 *
 * <p><strong>Deterministic-only on purpose.</strong> Index time runs the free,
 * reproducible readability scorer — never the per-document LLM verdict. The
 * grounded LLM report stays the on-demand T467 {@code /content-fit} path; here
 * we keep indexing fast and credit-free (the T385 deterministic-scorer +
 * LLM-seam discipline). Disabled is byte-for-byte the legacy indexing path: no
 * field is provisioned and the scorer never runs.
 *
 * <p>Defensive by construction — any failure (no persona, blank text, scorer or
 * provisioner error) is swallowed and logged so a scoring problem can never
 * block a document from being indexed. Blank/absent text is left
 * <em>unscored</em> (T381 grounding: absent ≠ a misleading neutral 50).
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TurSNContentFitIndexer {

    /** SN field that carries the per-document audience-fit score (0–100, INT). */
    public static final String FIELD_NAME = "content_fit_score";

    private final TurReadabilityScorer readabilityScorer;
    private final TurPersonaRepository personaRepository;
    private final TurSNFieldProvisioner fieldProvisioner;

    /**
     * Computes and attaches the content-fit score to {@code attributes} when the
     * site has opted in. No-op (early return) otherwise. Mutates the map in place
     * and ensures the backing SN field exists before the document is indexed.
     */
    public void enrich(TurSNSite turSNSite, Map<String, Object> attributes) {
        if (turSNSite == null || attributes == null) {
            return;
        }
        try {
            TurSNSiteGenAi genAi = turSNSite.getTurSNSiteGenAi();
            if (genAi == null || !genAi.isContentFitIndexingEnabled()
                    || StringUtils.isBlank(genAi.getContentFitPersonaId())) {
                return;
            }
            TurPersona persona = personaRepository.findById(genAi.getContentFitPersonaId())
                    .orElse(null);
            if (persona == null || !persona.isUsableAsAudience()) {
                log.debug("[T472] content-fit enabled on site '{}' but persona '{}' is "
                        + "missing or not an AUDIENCE persona — skipping",
                        turSNSite.getName(), genAi.getContentFitPersonaId());
                return;
            }
            String text = extractText(turSNSite, attributes);
            if (StringUtils.isBlank(text)) {
                // T381 — absent text must not be scored as a misleading neutral 50.
                return;
            }
            int score = (int) Math.round(
                    readabilityScorer.score(text, persona.getAudience()).fitScore());
            ensureField(turSNSite);
            attributes.put(FIELD_NAME, score);
            log.debug("[T472] content-fit score {} for a document on site '{}' (audience '{}')",
                    score, turSNSite.getName(), persona.getName());
        } catch (Exception e) {
            // Never let a scoring problem block indexing.
            log.warn("[T472] content-fit scoring failed on site '{}': {}",
                    turSNSite.getName(), e.getMessage());
        }
    }

    /**
     * Assembles the text to score from the document's title / description / body,
     * preferring the site's configured default fields and falling back to the
     * conventional {@link TurSNFieldName} names. Multi-valued fields are joined.
     */
    private String extractText(TurSNSite turSNSite, Map<String, Object> attributes) {
        List<String> parts = new ArrayList<>();
        appendField(parts, attributes,
                StringUtils.defaultIfBlank(turSNSite.getDefaultTitleField(), TurSNFieldName.TITLE));
        appendField(parts, attributes, StringUtils.defaultIfBlank(
                turSNSite.getDefaultDescriptionField(), TurSNFieldName.ABSTRACT));
        appendField(parts, attributes,
                StringUtils.defaultIfBlank(turSNSite.getDefaultTextField(), TurSNFieldName.TEXT));
        return String.join("\n", parts).strip();
    }

    private void appendField(List<String> parts, Map<String, Object> attributes, String fieldName) {
        Object value = attributes.get(fieldName);
        if (value instanceof Iterable<?> iterable) {
            iterable.forEach(item -> appendValue(parts, item));
        } else {
            appendValue(parts, value);
        }
    }

    private void appendValue(List<String> parts, Object value) {
        if (value != null) {
            String s = value.toString().strip();
            if (!s.isEmpty()) {
                parts.add(s);
            }
        }
    }

    private void ensureField(TurSNSite turSNSite) {
        fieldProvisioner.ensureField(turSNSite, TurSNJobAttributeSpec.builder()
                .name(FIELD_NAME)
                .type(TurSEFieldType.INT)
                .description("T472 audience content-fit / readability score (0–100, "
                        + "higher = better fit). Computed at index time.")
                .mandatory(false)
                .multiValued(false)
                .facet(false)
                .build());
    }
}
