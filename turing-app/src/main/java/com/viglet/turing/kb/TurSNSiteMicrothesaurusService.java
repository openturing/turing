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
package com.viglet.turing.kb;

import java.util.List;
import java.util.Optional;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.viglet.turing.client.sn.job.TurSNJobAttributeSpec;
import com.viglet.turing.commons.se.field.TurSEFieldType;
import com.viglet.turing.persistence.dto.kb.TurSNSiteMicrothesaurusConfigDto;
import com.viglet.turing.persistence.dto.kb.TurSNSiteMicrothesaurusDto;
import com.viglet.turing.persistence.model.kb.TurMicrothesaurus;
import com.viglet.turing.persistence.model.kb.TurSNSiteMicrothesaurus;
import com.viglet.turing.persistence.model.kb.TurSNSiteMicrothesaurusConfig;
import com.viglet.turing.persistence.model.sn.TurSNSite;
import com.viglet.turing.persistence.repository.kb.TurMicrothesaurusRepository;
import com.viglet.turing.persistence.repository.kb.TurSNSiteMicrothesaurusConfigRepository;
import com.viglet.turing.persistence.repository.kb.TurSNSiteMicrothesaurusRepository;
import com.viglet.turing.sn.field.TurSNFieldProvisioner;
import com.viglet.turing.sn.kb.TurMicrothesaurusDictionaryService;

/**
 * T670 / §XL (Block AQ) — per-site microthesaurus <em>selection</em> and
 * <em>index configuration</em>. A site opts in by selecting one or more
 * microthesauri and (optionally) tuning the backing field; with no selection the
 * indexing path is byte-identical to the legacy path (ADR 0003).
 *
 * <p>Saving an <em>enabled</em> configuration also wires
 * {@link TurSNFieldProvisioner}: the backing multi-valued TEXT field (+ its
 * auto-created {@code _str} concept facet) is provisioned in every locale core so
 * the T672 enricher has somewhere to write. Provisioning is idempotent and
 * defensive — a provisioning failure never fails the config save.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Service
public class TurSNSiteMicrothesaurusService {

    private static final Logger log = LoggerFactory.getLogger(TurSNSiteMicrothesaurusService.class);

    private final TurSNSiteMicrothesaurusRepository selectionRepository;
    private final TurSNSiteMicrothesaurusConfigRepository configRepository;
    private final TurMicrothesaurusRepository microthesaurusRepository;
    private final TurSNFieldProvisioner fieldProvisioner;
    private final TurMicrothesaurusDictionaryService dictionaryService;

    public TurSNSiteMicrothesaurusService(TurSNSiteMicrothesaurusRepository selectionRepository,
            TurSNSiteMicrothesaurusConfigRepository configRepository,
            TurMicrothesaurusRepository microthesaurusRepository,
            TurSNFieldProvisioner fieldProvisioner,
            TurMicrothesaurusDictionaryService dictionaryService) {
        this.selectionRepository = selectionRepository;
        this.configRepository = configRepository;
        this.microthesaurusRepository = microthesaurusRepository;
        this.fieldProvisioner = fieldProvisioner;
        this.dictionaryService = dictionaryService;
    }

    // ---- selection --------------------------------------------------------

    @Transactional(readOnly = true)
    public List<TurSNSiteMicrothesaurusDto> listSelections(TurSNSite turSNSite) {
        return selectionRepository.findByTurSNSite(turSNSite).stream().map(this::toDto).toList();
    }

    @Transactional
    public TurSNSiteMicrothesaurusDto select(TurSNSite turSNSite, TurSNSiteMicrothesaurusDto dto) {
        if (dto == null || StringUtils.isBlank(dto.microthesaurusId())) {
            throw new IllegalArgumentException("microthesaurusId is required");
        }
        TurMicrothesaurus microthesaurus = microthesaurusRepository.findById(dto.microthesaurusId())
                .orElseThrow(() -> new IllegalArgumentException(
                        "Unknown microthesaurus: " + dto.microthesaurusId()));
        if (selectionRepository.existsByTurSNSiteAndMicrothesaurusId(turSNSite,
                dto.microthesaurusId())) {
            throw new IllegalArgumentException("Microthesaurus already selected for this site");
        }
        TurSNSiteMicrothesaurus selection = new TurSNSiteMicrothesaurus();
        selection.setTurSNSite(turSNSite);
        selection.setMicrothesaurusId(microthesaurus.getId());
        selection.setEnabled(dto.enabled());
        TurSNSiteMicrothesaurusDto saved = toDto(selectionRepository.save(selection));
        dictionaryService.evictAll();
        return saved;
    }

    @Transactional
    public Optional<TurSNSiteMicrothesaurusDto> setSelectionEnabled(String id, boolean enabled) {
        return selectionRepository.findById(id).map(selection -> {
            selection.setEnabled(enabled);
            TurSNSiteMicrothesaurusDto saved = toDto(selectionRepository.save(selection));
            dictionaryService.evictAll();
            return saved;
        });
    }

    @Transactional
    public boolean deselect(String id) {
        if (!selectionRepository.existsById(id)) {
            return false;
        }
        selectionRepository.deleteById(id);
        dictionaryService.evictAll();
        return true;
    }

    // ---- config -----------------------------------------------------------

    /** Returns the stored config, or a transient defaults instance when none exists. */
    @Transactional(readOnly = true)
    public TurSNSiteMicrothesaurusConfigDto getConfig(TurSNSite turSNSite) {
        return configRepository.findByTurSNSite(turSNSite)
                .map(TurSNSiteMicrothesaurusService::toConfigDto)
                .orElseGet(() -> new TurSNSiteMicrothesaurusConfigDto(false,
                        TurSNSiteMicrothesaurusConfig.DEFAULT_FIELD_NAME,
                        TurSNSiteMicrothesaurusConfig.DEFAULT_BOOST, true,
                        false, TurSNSiteMicrothesaurusConfig.DEFAULT_PATH_FIELD_NAME));
    }

    @Transactional
    public TurSNSiteMicrothesaurusConfigDto saveConfig(TurSNSite turSNSite,
            TurSNSiteMicrothesaurusConfigDto dto) {
        if (dto == null) {
            throw new IllegalArgumentException("Config is required");
        }
        TurSNSiteMicrothesaurusConfig config = configRepository.findByTurSNSite(turSNSite)
                .orElseGet(() -> {
                    TurSNSiteMicrothesaurusConfig created = new TurSNSiteMicrothesaurusConfig();
                    created.setTurSNSite(turSNSite);
                    return created;
                });
        config.setEnabled(dto.enabled());
        config.setFieldName(StringUtils.isBlank(dto.fieldName())
                ? TurSNSiteMicrothesaurusConfig.DEFAULT_FIELD_NAME
                : dto.fieldName().trim());
        config.setBoost(dto.boost() <= 0 ? TurSNSiteMicrothesaurusConfig.DEFAULT_BOOST : dto.boost());
        config.setIncludeSynonyms(dto.includeSynonyms());
        config.setPathFacetEnabled(dto.pathFacetEnabled());
        config.setPathFieldName(StringUtils.isBlank(dto.pathFieldName())
                ? TurSNSiteMicrothesaurusConfig.DEFAULT_PATH_FIELD_NAME
                : dto.pathFieldName().trim());
        TurSNSiteMicrothesaurusConfig saved = configRepository.save(config);
        if (saved.isEnabled()) {
            provisionField(turSNSite, saved.getFieldName(), TurSEFieldType.TEXT);
            if (saved.isPathFacetEnabled()) {
                // T677 — exact path tokens: a STRING facet field (docValues), not
                // tokenised TEXT, so hierarchical drill-down values stay intact.
                provisionField(turSNSite, saved.getPathFieldName(), TurSEFieldType.STRING);
            }
        }
        dictionaryService.evictAll();
        return toConfigDto(saved);
    }

    /**
     * Wires {@link TurSNFieldProvisioner}: converges the backing multi-valued
     * TEXT field (whose {@code _str} copy field becomes the concept facet) in
     * every locale core. Idempotent; failures are logged, never propagated.
     */
    void provisionField(TurSNSite turSNSite, String fieldName, TurSEFieldType type) {
        try {
            TurSNJobAttributeSpec spec = TurSNJobAttributeSpec.builder()
                    .name(fieldName)
                    .type(type)
                    .multiValued(true)
                    .facet(true)
                    .description("Knowledge Base microthesaurus terms (index-time expansion)")
                    .build();
            fieldProvisioner.ensureField(turSNSite, spec);
        } catch (RuntimeException e) {
            log.warn("Could not provision microthesaurus field '{}' for site '{}': {}",
                    fieldName, turSNSite.getName(), e.getMessage());
        }
    }

    // ---- mapping ----------------------------------------------------------

    private TurSNSiteMicrothesaurusDto toDto(TurSNSiteMicrothesaurus selection) {
        return microthesaurusRepository.findById(selection.getMicrothesaurusId())
                .map(m -> new TurSNSiteMicrothesaurusDto(selection.getId(),
                        selection.getMicrothesaurusId(), selection.isEnabled(),
                        m.getName(), m.getLanguage(), m.getDomain()))
                .orElseGet(() -> new TurSNSiteMicrothesaurusDto(selection.getId(),
                        selection.getMicrothesaurusId(), selection.isEnabled(), null, null, null));
    }

    static TurSNSiteMicrothesaurusConfigDto toConfigDto(TurSNSiteMicrothesaurusConfig config) {
        return new TurSNSiteMicrothesaurusConfigDto(config.isEnabled(), config.getFieldName(),
                config.getBoost(), config.isIncludeSynonyms(),
                config.isPathFacetEnabled(), config.getPathFieldName());
    }
}
