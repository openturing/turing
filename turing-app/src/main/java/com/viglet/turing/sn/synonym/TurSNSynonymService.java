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
package com.viglet.turing.sn.synonym;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.viglet.turing.persistence.dto.sn.synonym.TurSNSynonymDto;
import com.viglet.turing.persistence.model.sn.TurSNSite;
import com.viglet.turing.persistence.model.sn.synonym.TurSNSynonym;
import com.viglet.turing.persistence.repository.sn.synonym.TurSNSynonymRepository;
import com.viglet.turing.plugins.se.TurSESynonymRule;

/**
 * T662 / §XXXIX (Block AP) — CRUD + batch + search over the engine-agnostic
 * synonym store, plus the read path the engine plugins consume
 * ({@link #synonymsForApply}). Admin/CRUD reads go straight through the JPA
 * repository (per the bounded domain-layer rule); no caching yet — the engine
 * seam (T663) owns any apply-time memoisation.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Service
public class TurSNSynonymService {

    private final TurSNSynonymRepository repository;

    public TurSNSynonymService(TurSNSynonymRepository repository) {
        this.repository = repository;
    }

    // ---- Reads (DTO) -----------------------------------------------------

    @Transactional(readOnly = true)
    public List<TurSNSynonymDto> list(TurSNSite site, Locale language, String query) {
        List<TurSNSynonym> synonyms = language == null
                ? repository.findByTurSNSite(Sort.by(Sort.Direction.ASC, "type", "input", "name"), site)
                : repository.findByTurSNSiteAndLanguage(site, language);
        String needle = query == null ? null : query.trim().toLowerCase(Locale.ROOT);
        return synonyms.stream()
                .filter(s -> matches(s, needle))
                .map(TurSNSynonymService::toDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public Optional<TurSNSynonymDto> get(String id) {
        return repository.findById(id).map(TurSNSynonymService::toDto);
    }

    /**
     * The enabled, term-populated rules the search-engine plugin pushes into a
     * (site, locale) index. Terms are JOIN-FETCHed so callers can read them
     * outside a transaction.
     */
    @Transactional(readOnly = true)
    public List<TurSNSynonym> synonymsForApply(TurSNSite site, Locale language) {
        return repository.findEnabledForApply(site, language);
    }

    /**
     * The enabled rules for a (site, locale) as the engine-neutral
     * {@link TurSESynonymRule} projection the plugin seam (T663) consumes. The
     * entity → rule mapping (including the lazy {@code terms}) runs inside this
     * transaction so the caller can do engine I/O without a persistence context.
     */
    @Transactional(readOnly = true)
    public List<TurSESynonymRule> rulesForApply(TurSNSite site, Locale language) {
        return repository.findEnabledForApply(site, language).stream()
                .map(s -> new TurSESynonymRule(s.getType(), s.getInput(), List.copyOf(s.getTerms())))
                .toList();
    }

    // ---- Writes ----------------------------------------------------------

    @Transactional
    public TurSNSynonymDto create(TurSNSite site, TurSNSynonymDto dto) {
        TurSNSynonym entity = new TurSNSynonym();
        entity.setTurSNSite(site);
        apply(entity, dto);
        return toDto(repository.save(entity));
    }

    @Transactional
    public Optional<TurSNSynonymDto> update(String id, TurSNSynonymDto dto) {
        return repository.findById(id).map(entity -> {
            apply(entity, dto);
            return toDto(repository.save(entity));
        });
    }

    @Transactional
    public boolean delete(String id) {
        if (!repository.existsById(id)) {
            return false;
        }
        repository.deleteById(id);
        return true;
    }

    /**
     * Bulk upsert (Algolia's {@code batch}). Rules with an id that resolves to
     * an existing row on this site are updated; everything else is created. Used
     * both by the admin "save all" and by the T666 Algolia importer.
     */
    @Transactional
    public List<TurSNSynonymDto> batch(TurSNSite site, List<TurSNSynonymDto> dtos) {
        if (dtos == null) {
            return List.of();
        }
        return dtos.stream().map(dto -> {
            TurSNSynonym entity = resolveForBatch(site, dto);
            apply(entity, dto);
            return toDto(repository.save(entity));
        }).toList();
    }

    private TurSNSynonym resolveForBatch(TurSNSite site, TurSNSynonymDto dto) {
        if (dto.id() != null && !dto.id().isBlank()) {
            TurSNSynonym existing = repository.findById(dto.id())
                    .filter(s -> s.getTurSNSite() != null
                            && s.getTurSNSite().getId().equals(site.getId()))
                    .orElse(null);
            if (existing != null) {
                return existing;
            }
        }
        TurSNSynonym fresh = new TurSNSynonym();
        fresh.setTurSNSite(site);
        return fresh;
    }

    // ---- Mapping + validation -------------------------------------------

    private void apply(TurSNSynonym entity, TurSNSynonymDto dto) {
        if (dto.language() == null) {
            // IllegalArgumentException maps to HTTP 400 via TurGlobalExceptionHandler.
            throw new IllegalArgumentException("Synonym language is required");
        }
        // The normalizer throws IllegalArgumentException on an invalid rule, which
        // the global handler maps to HTTP 400 — no wrapping needed here.
        TurSNSynonymNormalizer.Normalized normalized =
                TurSNSynonymNormalizer.normalize(dto.type(), dto.input(), dto.terms());
        entity.setName(dto.name());
        entity.setType(dto.type());
        entity.setLanguage(dto.language());
        entity.setInput(normalized.input());
        entity.setTerms(normalized.terms());
        entity.setEnabled(dto.enabled());
        entity.setModificationDate(LocalDateTime.now());
    }

    private static boolean matches(TurSNSynonym synonym, String needle) {
        if (needle == null || needle.isEmpty()) {
            return true;
        }
        if (contains(synonym.getName(), needle) || contains(synonym.getInput(), needle)) {
            return true;
        }
        return synonym.getTerms().stream().anyMatch(t -> contains(t, needle));
    }

    private static boolean contains(String value, String needle) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(needle);
    }

    private static TurSNSynonymDto toDto(TurSNSynonym entity) {
        return new TurSNSynonymDto(
                entity.getId(),
                entity.getName(),
                entity.getType(),
                entity.getLanguage(),
                entity.getInput(),
                List.copyOf(entity.getTerms()),
                entity.isEnabled());
    }
}
