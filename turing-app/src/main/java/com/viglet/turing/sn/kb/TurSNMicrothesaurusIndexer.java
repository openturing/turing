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
package com.viglet.turing.sn.kb;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import com.viglet.turing.client.sn.job.TurSNJobAttributeSpec;
import com.viglet.turing.commons.se.field.TurSEFieldType;
import com.viglet.turing.commons.sn.field.TurSNFieldName;
import com.viglet.turing.persistence.model.kb.TurSNSiteMicrothesaurusConfig;
import com.viglet.turing.persistence.model.sn.TurSNSite;
import com.viglet.turing.persistence.repository.kb.TurSNSiteMicrothesaurusConfigRepository;
import com.viglet.turing.sn.field.TurSNFieldProvisioner;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * T672 / §XL (Block AQ) — the index-time enricher that expands every document
 * with the root→term hierarchical path of each controlled-vocabulary term it
 * mentions (ADR 0003 §3). Modelled exactly on {@link com.viglet.turing.sn.contentfit.TurSNContentFitIndexer}
 * (T472): wired into {@code TurSNProcessQueue.index(...)} after the other
 * enrichers, it mutates the {@code attributes} map in place and provisions its
 * own backing field.
 *
 * <p>Flow: (1) early-return unless the site has an <em>enabled</em>
 * microthesaurus config — a site that never opted in indexes byte-for-byte as
 * before; (2) load the cached recognition dictionary for (site, document locale)
 * — empty (no selection matching the locale) also returns early; (3) extract the
 * title/description/body text; (4) recognise mentioned terms in one pass;
 * (5) union their pre-computed ancestor paths (deduped); (6) ensure the
 * multi-valued TEXT field exists (its {@code _str} copy field is the concept
 * facet) and write the terms.
 *
 * <p><strong>Defensive by construction</strong> — any failure is swallowed and
 * logged; a thesaurus problem can never block a document from being indexed.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TurSNMicrothesaurusIndexer {

    private final TurSNSiteMicrothesaurusConfigRepository configRepository;
    private final TurMicrothesaurusDictionaryService dictionaryService;
    private final TurSNFieldProvisioner fieldProvisioner;

    /**
     * Recognises controlled-vocabulary terms in the document and writes their
     * hierarchical paths to the configured field. No-op unless the site opted in.
     */
    public void enrich(TurSNSite turSNSite, Locale locale, Map<String, Object> attributes) {
        if (turSNSite == null || attributes == null) {
            return;
        }
        try {
            TurSNSiteMicrothesaurusConfig config = configRepository.findByTurSNSite(turSNSite)
                    .orElse(null);
            if (config == null || !config.isEnabled()) {
                return; // never opted in → byte-identical legacy path
            }
            TurRecognitionDictionary dictionary = dictionaryService.getDictionary(turSNSite, locale);
            if (dictionary.isEmpty()) {
                return; // no selected microthesaurus matches this document's locale
            }
            String text = extractText(turSNSite, attributes);
            if (StringUtils.isBlank(text)) {
                return;
            }
            Set<String> terms = new LinkedHashSet<>();
            Set<String> pathTokens = new LinkedHashSet<>();
            dictionary.recognize(text).forEach(recognized -> {
                terms.addAll(recognized.path());
                if (config.isPathFacetEnabled()) {
                    pathTokens.addAll(pathTokens(recognized.path()));
                }
            });
            if (terms.isEmpty()) {
                return;
            }
            ensureField(turSNSite, config.getFieldName(), TurSEFieldType.TEXT);
            attributes.put(config.getFieldName(), new ArrayList<>(terms));
            if (config.isPathFacetEnabled() && !pathTokens.isEmpty()) {
                // T677 — exact hierarchical drill-down tokens go to a STRING facet field.
                ensureField(turSNSite, config.getPathFieldName(), TurSEFieldType.STRING);
                attributes.put(config.getPathFieldName(), new ArrayList<>(pathTokens));
            }
            log.debug("[T672] expanded a document on site '{}' with {} microthesaurus term(s)",
                    turSNSite.getName(), terms.size());
        } catch (Exception e) {
            // Never let a thesaurus problem block indexing.
            log.warn("[T672] microthesaurus expansion failed on site '{}': {}",
                    turSNSite.getName(), e.getMessage());
        }
    }

    /** Same title/description/body assembly shape as {@code TurSNContentFitIndexer}. */
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

    /**
     * T677 — builds the level-prefixed cumulative path tokens from a root→term
     * label path, the standard Solr hierarchical-faceting shape. For
     * {@code [Doença, Doença respiratória, Pneumonia]} it yields
     * {@code 0/Doença}, {@code 1/Doença/Doença respiratória},
     * {@code 2/Doença/Doença respiratória/Pneumonia}. A slash in a label is
     * escaped so it can't forge a level boundary.
     */
    static List<String> pathTokens(List<String> path) {
        List<String> tokens = new ArrayList<>(path.size());
        StringBuilder prefix = new StringBuilder();
        for (int level = 0; level < path.size(); level++) {
            if (level > 0) {
                prefix.append('/');
            }
            prefix.append(path.get(level).replace("/", "\\/"));
            tokens.add(level + "/" + prefix);
        }
        return tokens;
    }

    private void ensureField(TurSNSite turSNSite, String fieldName, TurSEFieldType type) {
        fieldProvisioner.ensureField(turSNSite, TurSNJobAttributeSpec.builder()
                .name(fieldName)
                .type(type)
                .description("T672/T677 Knowledge Base microthesaurus — index-time "
                        + "hierarchical expansion (controlled vocabulary).")
                .mandatory(false)
                .multiValued(true)
                .facet(true)
                .build());
    }
}
