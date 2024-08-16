/*
 * Copyright (C) 2016-2022 the original author or authors.
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

package com.viglet.turing.api.sn.console;

import com.google.inject.Inject;
import com.viglet.turing.persistence.model.sn.spotlight.TurSNSiteSpotlight;
import com.viglet.turing.persistence.repository.sn.TurSNSiteRepository;
import com.viglet.turing.persistence.repository.sn.spotlight.TurSNSiteSpotlightDocumentRepository;
import com.viglet.turing.persistence.repository.sn.spotlight.TurSNSiteSpotlightRepository;
import com.viglet.turing.persistence.repository.sn.spotlight.TurSNSiteSpotlightTermRepository;
import com.viglet.turing.persistence.utils.TurPersistenceUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.jetbrains.annotations.NotNull;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

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

    @Inject
    public TurSNSiteSpotlightAPI(TurSNSiteRepository turSNSiteRepository,
                                 TurSNSiteSpotlightRepository turSNSiteSpotlightRepository,
                                 TurSNSiteSpotlightDocumentRepository turSNSiteSpotlightDocumentRepository,
                                 TurSNSiteSpotlightTermRepository turSNSiteSpotlightTermRepository) {
        this.turSNSiteRepository = turSNSiteRepository;
        this.turSNSiteSpotlightRepository = turSNSiteSpotlightRepository;
        this.turSNSiteSpotlightDocumentRepository = turSNSiteSpotlightDocumentRepository;
        this.turSNSiteSpotlightTermRepository = turSNSiteSpotlightTermRepository;
    }

    @Operation(summary = "Semantic Navigation Site Spotlight List")
    @GetMapping
    public List<TurSNSiteSpotlight> turSNSiteSpotlightList(@PathVariable String ignoredSnSiteId) {
        return turSNSiteRepository.findById(ignoredSnSiteId).map(site -> this.turSNSiteSpotlightRepository
                        .findByTurSNSite(TurPersistenceUtils.orderByNameIgnoreCase(), site))
                .orElse(Collections.emptyList());
    }

    @Operation(summary = "Show a Semantic Navigation Site Spotlight")
    @GetMapping("/{id}")
    public TurSNSiteSpotlight turSNSiteSpotlightGet(@PathVariable String ignoredSnSiteId, @PathVariable String id) {

        Optional<TurSNSiteSpotlight> turSNSiteSpotlight = turSNSiteSpotlightRepository.findById(id);
        if (turSNSiteSpotlight.isPresent()) {
            turSNSiteSpotlight.get().setTurSNSiteSpotlightDocuments(
                    turSNSiteSpotlightDocumentRepository.findByTurSNSiteSpotlight(turSNSiteSpotlight.get()));
            turSNSiteSpotlight.get().setTurSNSiteSpotlightTerms(
                    turSNSiteSpotlightTermRepository.findByTurSNSiteSpotlight(turSNSiteSpotlight.get()));
            return turSNSiteSpotlight.get();
        }
        return new TurSNSiteSpotlight();
    }

    @Operation(summary = "Update a Semantic Navigation Site Spotlight")
    @PutMapping("/{id}")
    @CacheEvict(value = {"spotlight", "spotlight_term"}, allEntries = true)
    public TurSNSiteSpotlight turSNSiteSpotlightUpdate(@PathVariable String id,
                                                       @RequestBody TurSNSiteSpotlight turSNSiteSpotlight,
                                                       @PathVariable String ignoredSnSiteId) {

        return turSNSiteSpotlightRepository.findById(id).map(turSNSiteSpotlightEdit -> {
            turSNSiteSpotlightEdit.setDescription(turSNSiteSpotlight.getDescription());
            turSNSiteSpotlightEdit.setLanguage(turSNSiteSpotlight.getLanguage());
            turSNSiteSpotlightEdit.setModificationDate(turSNSiteSpotlight.getModificationDate());
            turSNSiteSpotlightEdit.setName(turSNSiteSpotlight.getName());
            turSNSiteSpotlightEdit.setProvider(turSNSiteSpotlight.getProvider());
            turSNSiteSpotlightEdit.setTurSNSite(turSNSiteSpotlight.getTurSNSite());
            turSNSiteSpotlightEdit.setTurSNSiteSpotlightTerms(turSNSiteSpotlight.getTurSNSiteSpotlightTerms()
                    .stream()
                    .peek(term ->
                            term.setTurSNSiteSpotlight(turSNSiteSpotlight))
                    .collect(Collectors.toSet()));
            turSNSiteSpotlightEdit.setTurSNSiteSpotlightDocuments(turSNSiteSpotlight.getTurSNSiteSpotlightDocuments()
                    .stream()
                    .peek(document ->
                            document.setTurSNSiteSpotlight(turSNSiteSpotlight))
                    .collect(Collectors.toSet()));
            return saveSpotlight(turSNSiteSpotlight, turSNSiteSpotlightEdit);
        }).orElse(new TurSNSiteSpotlight());
    }

    @NotNull
    private TurSNSiteSpotlight saveSpotlight(@RequestBody TurSNSiteSpotlight turSNSiteSpotlight,
                                             TurSNSiteSpotlight turSNSiteSpotlightEdit) {
        turSNSiteSpotlight.getTurSNSiteSpotlightTerms().forEach(term ->
                term.setTurSNSiteSpotlight(turSNSiteSpotlightEdit));
        turSNSiteSpotlight.getTurSNSiteSpotlightDocuments().forEach(document ->
                document.setTurSNSiteSpotlight(turSNSiteSpotlightEdit));
        turSNSiteSpotlightRepository.save(turSNSiteSpotlightEdit);
        return turSNSiteSpotlightEdit;
    }

    @Transactional
    @Operation(summary = "Delete a Semantic Navigation Site Spotlight")
    @DeleteMapping("/{id}")
    @CacheEvict(value = {"spotlight", "spotlight_term"}, allEntries = true)
    public boolean turSNSiteSpotlightDelete(@PathVariable String id, @PathVariable String ignoredSnSiteId) {
        turSNSiteSpotlightRepository.deleteById(id);
        return true;
    }

    @Operation(summary = "Create a Semantic Navigation Site Spotlight")
    @PostMapping
    @CacheEvict(value = {"spotlight", "spotlight_term"}, allEntries = true)
    public TurSNSiteSpotlight turSNSiteSpotlightAdd(@RequestBody TurSNSiteSpotlight turSNSiteSpotlight,
                                                    @PathVariable String ignoredSnSiteId) {
        return saveSpotlight(turSNSiteSpotlight, turSNSiteSpotlight);
    }

    @Operation(summary = "Semantic Navigation Site Spotlight structure")
    @GetMapping("structure")
    public TurSNSiteSpotlight turSNSiteSpotlightStructure(@PathVariable String ignoredSnSiteId) {
        return turSNSiteRepository.findById(ignoredSnSiteId).map(turSNSite -> {
            TurSNSiteSpotlight turSNSiteSpotlight = new TurSNSiteSpotlight();
            turSNSiteSpotlight.setTurSNSite(turSNSite);
            return turSNSiteSpotlight;
        }).orElse(new TurSNSiteSpotlight());
    }
}
