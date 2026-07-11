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
package com.viglet.turing.api.kb;

import java.util.List;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.viglet.turing.api.exception.TurNotFoundException;
import com.viglet.turing.kb.TurThesaurusTermService;
import com.viglet.turing.persistence.dto.kb.TurThesaurusTermDto;
import com.viglet.turing.persistence.model.kb.TurMicrothesaurus;
import com.viglet.turing.persistence.repository.kb.TurMicrothesaurusRepository;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * T669 / §XL (Block AQ) — management REST API for thesaurus terms, scoped to a
 * microthesaurus. A term's recognition variations and non-hierarchical relations
 * are authored inline with the term; {@code parentTermId} is the broader/narrower
 * link. {@code ?roots=true} lists only root terms; {@code /{id}/children} walks a
 * term's narrower terms.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@RestController
@RequestMapping("/api/kb/microthesaurus/{microthesaurusId}/term")
@Tag(name = "Knowledge Base Term", description = "Thesaurus Term API")
public class TurThesaurusTermAPI {

    private final TurMicrothesaurusRepository turMicrothesaurusRepository;
    private final TurThesaurusTermService turThesaurusTermService;

    public TurThesaurusTermAPI(TurMicrothesaurusRepository turMicrothesaurusRepository,
            TurThesaurusTermService turThesaurusTermService) {
        this.turMicrothesaurusRepository = turMicrothesaurusRepository;
        this.turThesaurusTermService = turThesaurusTermService;
    }

    private TurMicrothesaurus microthesaurus(String microthesaurusId) {
        return turMicrothesaurusRepository.findById(microthesaurusId)
                .orElseThrow(() -> TurNotFoundException.of("Microthesaurus", microthesaurusId));
    }

    @Operation(summary = "List a microthesaurus' terms (all, or roots only via ?roots=true)")
    @GetMapping
    public List<TurThesaurusTermDto> list(@PathVariable String microthesaurusId,
            @RequestParam(required = false, defaultValue = "false") boolean roots) {
        TurMicrothesaurus microthesaurus = microthesaurus(microthesaurusId);
        return roots
                ? turThesaurusTermService.listRoots(microthesaurus)
                : turThesaurusTermService.listByMicrothesaurus(microthesaurus);
    }

    @Operation(summary = "List a term's narrower (child) terms")
    @GetMapping("/{id}/children")
    public List<TurThesaurusTermDto> children(@PathVariable String microthesaurusId,
            @PathVariable String id) {
        microthesaurus(microthesaurusId);
        return turThesaurusTermService.listChildren(id);
    }

    @Operation(summary = "Show a term")
    @GetMapping("/{id}")
    public TurThesaurusTermDto get(@PathVariable String microthesaurusId, @PathVariable String id) {
        microthesaurus(microthesaurusId);
        return turThesaurusTermService.get(id)
                .orElseThrow(() -> TurNotFoundException.of("Thesaurus Term", id));
    }

    @Operation(summary = "Create a term")
    @PostMapping
    public TurThesaurusTermDto create(@PathVariable String microthesaurusId,
            @RequestBody TurThesaurusTermDto dto) {
        return turThesaurusTermService.create(microthesaurus(microthesaurusId), dto);
    }

    @Operation(summary = "Update a term")
    @PutMapping("/{id}")
    public TurThesaurusTermDto update(@PathVariable String microthesaurusId, @PathVariable String id,
            @RequestBody TurThesaurusTermDto dto) {
        microthesaurus(microthesaurusId);
        return turThesaurusTermService.update(id, dto)
                .orElseThrow(() -> TurNotFoundException.of("Thesaurus Term", id));
    }

    @Operation(summary = "Delete a term")
    @DeleteMapping("/{id}")
    public boolean delete(@PathVariable String microthesaurusId, @PathVariable String id) {
        microthesaurus(microthesaurusId);
        return turThesaurusTermService.delete(id);
    }
}
