/*
 * Copyright (C) 2016-2022 the original author or authors.
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

package com.viglet.turing.api.sn.console;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.jetbrains.annotations.NotNull;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.viglet.turing.persistence.dto.sn.spotlight.TurSNSiteSpotlightDto;
import com.viglet.turing.persistence.mapper.sn.spotlight.TurSNSiteSpotlightMapper;
import com.viglet.turing.persistence.model.sn.spotlight.TurSNSiteSpotlight;
import com.viglet.turing.sn.spotlight.TurSpotlightCache;
import com.viglet.turing.system.cache.TurCacheEvictionScheduler;
import com.viglet.turing.persistence.model.sn.spotlight.TurSNSiteSpotlightDocument;
import com.viglet.turing.persistence.model.sn.spotlight.TurSNSiteSpotlightTerm;
import com.viglet.turing.persistence.repository.sn.TurSNSiteRepository;
import com.viglet.turing.persistence.repository.sn.spotlight.TurSNSiteSpotlightDocumentRepository;
import com.viglet.turing.persistence.repository.sn.spotlight.TurSNSiteSpotlightRepository;
import com.viglet.turing.persistence.repository.sn.spotlight.TurSNSiteSpotlightTermRepository;
import com.viglet.turing.spring.utils.TurPersistenceUtils;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * @author Alexandre Oliveira
 * @since 0.3.4
 */

@RestController
@RequestMapping("/api/sn/{ignoredSnSiteId}/spotlight")
@Tag(name = "Semantic Navigation Spotlight", description = "Semantic Navigation Spotlight API")
public class TurSNSiteSpotlightAPI {
    private final TurSNSiteRepository turSNSiteRepository;
    private final TurSNSiteSpotlightRepository turSNSiteSpotlightRepository;
    private final TurSNSiteSpotlightDocumentRepository turSNSiteSpotlightDocumentRepository;
    private final TurSNSiteSpotlightTermRepository turSNSiteSpotlightTermRepository;
    private final TurSNSiteSpotlightMapper turSNSiteSpotlightMapper;
    private final TurCacheEvictionScheduler evictionScheduler;

    public TurSNSiteSpotlightAPI(TurSNSiteRepository turSNSiteRepository,
            TurSNSiteSpotlightRepository turSNSiteSpotlightRepository,
            TurSNSiteSpotlightDocumentRepository turSNSiteSpotlightDocumentRepository,
            TurSNSiteSpotlightTermRepository turSNSiteSpotlightTermRepository,
            TurSNSiteSpotlightMapper turSNSiteSpotlightMapper,
            TurCacheEvictionScheduler evictionScheduler) {
        this.turSNSiteRepository = turSNSiteRepository;
        this.turSNSiteSpotlightRepository = turSNSiteSpotlightRepository;
        this.turSNSiteSpotlightDocumentRepository = turSNSiteSpotlightDocumentRepository;
        this.turSNSiteSpotlightTermRepository = turSNSiteSpotlightTermRepository;
        this.turSNSiteSpotlightMapper = turSNSiteSpotlightMapper;
        this.evictionScheduler = evictionScheduler;
    }

    /**
     * T904 / §LXVIII.11 — clears the spotlight caches through the shared after-commit
     * scheduler rather than {@code @CacheEvict}.
     *
     * <p>
     * The delete endpoint is {@code @Transactional} and carried {@code @CacheEvict} on the
     * same method, so which of the two ran first was decided by the relative order of two
     * interceptors that both declare {@code LOWEST_PRECEDENCE} — correct by accident, at
     * best. The other two endpoints were not transactional and so were fine, and are
     * converted anyway: the point is that a reader no longer has to work out which is
     * which, and adding {@code @Transactional} here later cannot quietly reintroduce it.
     * </p>
     */
    private void clearSpotlightCaches() {
        evictionScheduler.clearOnce(TurSpotlightCache.SPOTLIGHT_CACHE);
        evictionScheduler.clearOnce(TurSpotlightCache.SPOTLIGHT_TERM_CACHE);
    }

    @Operation(summary = "Semantic Navigation Site Spotlight List")
    @GetMapping
    public List<TurSNSiteSpotlightDto> turSNSiteSpotlightList(@PathVariable String ignoredSnSiteId) {
        return turSNSiteRepository.findById(ignoredSnSiteId)
                .map(site -> turSNSiteSpotlightMapper.toDtoList(this.turSNSiteSpotlightRepository
                        .findByTurSNSite(TurPersistenceUtils.orderByNameIgnoreCase(), site)))
                .orElse(Collections.emptyList());
    }

    @Operation(summary = "Show a Semantic Navigation Site Spotlight")
    @GetMapping("/{id}")
    public TurSNSiteSpotlightDto turSNSiteSpotlightGet(@PathVariable String ignoredSnSiteId,
            @PathVariable String id) {

        Optional<TurSNSiteSpotlight> turSNSiteSpotlight = turSNSiteSpotlightRepository.findById(id);
        if (turSNSiteSpotlight.isPresent()) {
            turSNSiteSpotlight.get()
                    .setTurSNSiteSpotlightDocuments(turSNSiteSpotlightDocumentRepository
                            .findByTurSNSiteSpotlight(turSNSiteSpotlight.get()));
            turSNSiteSpotlight.get().setTurSNSiteSpotlightTerms(turSNSiteSpotlightTermRepository
                    .findByTurSNSiteSpotlight(turSNSiteSpotlight.get()));
            return turSNSiteSpotlightMapper.toDto(turSNSiteSpotlight.get());
        }
        return new TurSNSiteSpotlightDto();
    }

    @Operation(summary = "Update a Semantic Navigation Site Spotlight")
    @PutMapping("/{id}")
    public TurSNSiteSpotlightDto turSNSiteSpotlightUpdate(@PathVariable String id,
            @RequestBody TurSNSiteSpotlightDto turSNSiteSpotlightDto,
            @PathVariable String ignoredSnSiteId) {
        clearSpotlightCaches();
        TurSNSiteSpotlight turSNSiteSpotlight = turSNSiteSpotlightMapper.toEntity(turSNSiteSpotlightDto);

        return turSNSiteSpotlightRepository.findById(id).map(turSNSiteSpotlightEdit -> {
            turSNSiteSpotlightEdit.setDescription(turSNSiteSpotlight.getDescription());
            turSNSiteSpotlightEdit.setLanguage(turSNSiteSpotlight.getLanguage());
            turSNSiteSpotlightEdit.setModificationDate(turSNSiteSpotlight.getModificationDate());
            turSNSiteSpotlightEdit.setName(turSNSiteSpotlight.getName());
            turSNSiteSpotlightEdit.setProvider(turSNSiteSpotlight.getProvider());
            turSNSiteSpotlightEdit.setTurSNSite(turSNSiteSpotlight.getTurSNSite());
            Set<TurSNSiteSpotlightTerm> terms = new HashSet<>();
            for (TurSNSiteSpotlightTerm term : turSNSiteSpotlight.getTurSNSiteSpotlightTerms()) {
                term.setTurSNSiteSpotlight(turSNSiteSpotlight);
                terms.add(term);
            }
            turSNSiteSpotlightEdit.setTurSNSiteSpotlightTerms(terms);
            Set<TurSNSiteSpotlightDocument> result = new HashSet<>();
            for (TurSNSiteSpotlightDocument document : turSNSiteSpotlight
                    .getTurSNSiteSpotlightDocuments()) {
                document.setTurSNSiteSpotlight(turSNSiteSpotlight);
                result.add(document);
            }
            turSNSiteSpotlightEdit.setTurSNSiteSpotlightDocuments(result);
            return turSNSiteSpotlightMapper.toDto(saveSpotlight(turSNSiteSpotlight, turSNSiteSpotlightEdit));
        }).orElse(new TurSNSiteSpotlightDto());
    }

    @NotNull
    private TurSNSiteSpotlight saveSpotlight(TurSNSiteSpotlight turSNSiteSpotlight,
            TurSNSiteSpotlight turSNSiteSpotlightEdit) {
        turSNSiteSpotlight.getTurSNSiteSpotlightTerms()
                .forEach(term -> term.setTurSNSiteSpotlight(turSNSiteSpotlightEdit));
        turSNSiteSpotlight.getTurSNSiteSpotlightDocuments()
                .forEach(document -> document.setTurSNSiteSpotlight(turSNSiteSpotlightEdit));
        turSNSiteSpotlightRepository.save(turSNSiteSpotlightEdit);
        return turSNSiteSpotlightEdit;
    }

    @Transactional
    @Operation(summary = "Delete a Semantic Navigation Site Spotlight")
    @DeleteMapping("/{id}")
    public boolean turSNSiteSpotlightDelete(@PathVariable String id,
            @PathVariable String ignoredSnSiteId) {
        clearSpotlightCaches();
        turSNSiteSpotlightRepository.deleteById(id);
        return true;
    }

    @Operation(summary = "Create a Semantic Navigation Site Spotlight")
    @PostMapping
    public TurSNSiteSpotlightDto turSNSiteSpotlightAdd(
            @RequestBody TurSNSiteSpotlightDto turSNSiteSpotlightDto,
            @PathVariable String ignoredSnSiteId) {
        clearSpotlightCaches();
        TurSNSiteSpotlight turSNSiteSpotlight = turSNSiteSpotlightMapper.toEntity(turSNSiteSpotlightDto);
        return turSNSiteSpotlightMapper.toDto(saveSpotlight(turSNSiteSpotlight, turSNSiteSpotlight));
    }

    @Operation(summary = "Semantic Navigation Site Spotlight structure")
    @GetMapping("structure")
    public TurSNSiteSpotlightDto turSNSiteSpotlightStructure(@PathVariable String ignoredSnSiteId) {
        return turSNSiteRepository.findById(ignoredSnSiteId).map(turSNSite -> {
            TurSNSiteSpotlight turSNSiteSpotlight = new TurSNSiteSpotlight();
            turSNSiteSpotlight.setTurSNSite(turSNSite);
            return turSNSiteSpotlightMapper.toDto(turSNSiteSpotlight);
        }).orElse(new TurSNSiteSpotlightDto());
    }
}
