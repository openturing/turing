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

import java.io.IOException;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.viglet.turing.api.exception.TurNotFoundException;
import com.viglet.turing.kb.TurAuthorityFileImportService;
import com.viglet.turing.kb.TurMicrothesaurusService;
import com.viglet.turing.kb.TurThesaurusGenerationService;
import com.viglet.turing.persistence.dto.kb.TurMicrothesaurusDto;
import com.viglet.turing.persistence.dto.kb.TurThesaurusDraft;
import com.viglet.turing.persistence.dto.kb.TurThesaurusGenerationRequest;
import com.viglet.turing.persistence.model.kb.TurKnowledgeBase;
import com.viglet.turing.persistence.repository.kb.TurKnowledgeBaseRepository;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * T669 / §XL (Block AQ) — management REST API for microthesaurus trees, scoped to
 * a knowledge base.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@RestController
@RequestMapping("/api/kb/{knowledgeBaseId}/microthesaurus")
@Tag(name = "Knowledge Base Microthesaurus", description = "Microthesaurus API")
public class TurMicrothesaurusAPI {

    private final TurKnowledgeBaseRepository turKnowledgeBaseRepository;
    private final TurMicrothesaurusService turMicrothesaurusService;
    private final TurAuthorityFileImportService turAuthorityFileImportService;
    private final TurThesaurusGenerationService turThesaurusGenerationService;

    public TurMicrothesaurusAPI(TurKnowledgeBaseRepository turKnowledgeBaseRepository,
            TurMicrothesaurusService turMicrothesaurusService,
            TurAuthorityFileImportService turAuthorityFileImportService,
            TurThesaurusGenerationService turThesaurusGenerationService) {
        this.turKnowledgeBaseRepository = turKnowledgeBaseRepository;
        this.turMicrothesaurusService = turMicrothesaurusService;
        this.turAuthorityFileImportService = turAuthorityFileImportService;
        this.turThesaurusGenerationService = turThesaurusGenerationService;
    }

    private TurKnowledgeBase knowledgeBase(String knowledgeBaseId) {
        return turKnowledgeBaseRepository.findById(knowledgeBaseId)
                .orElseThrow(() -> TurNotFoundException.of("Knowledge Base", knowledgeBaseId));
    }

    @Operation(summary = "List a knowledge base's microthesauri")
    @GetMapping
    public List<TurMicrothesaurusDto> list(@PathVariable String knowledgeBaseId) {
        return turMicrothesaurusService.listByKnowledgeBase(knowledgeBase(knowledgeBaseId));
    }

    @Operation(summary = "Show a microthesaurus")
    @GetMapping("/{id}")
    public TurMicrothesaurusDto get(@PathVariable String knowledgeBaseId, @PathVariable String id) {
        knowledgeBase(knowledgeBaseId);
        return turMicrothesaurusService.get(id)
                .orElseThrow(() -> TurNotFoundException.of("Microthesaurus", id));
    }

    @Operation(summary = "Create a microthesaurus")
    @PostMapping
    public TurMicrothesaurusDto create(@PathVariable String knowledgeBaseId,
            @RequestBody TurMicrothesaurusDto dto) {
        return turMicrothesaurusService.create(knowledgeBase(knowledgeBaseId), dto);
    }

    @Operation(summary = "Update a microthesaurus")
    @PutMapping("/{id}")
    public TurMicrothesaurusDto update(@PathVariable String knowledgeBaseId, @PathVariable String id,
            @RequestBody TurMicrothesaurusDto dto) {
        knowledgeBase(knowledgeBaseId);
        return turMicrothesaurusService.update(id, dto)
                .orElseThrow(() -> TurNotFoundException.of("Microthesaurus", id));
    }

    @Operation(summary = "Delete a microthesaurus")
    @DeleteMapping("/{id}")
    public boolean delete(@PathVariable String knowledgeBaseId, @PathVariable String id) {
        knowledgeBase(knowledgeBaseId);
        return turMicrothesaurusService.delete(id);
    }

    @Operation(summary = "Import a microthesaurus from a Turing Thesaurus Exchange authority file (XML)")
    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public TurMicrothesaurusDto importAuthorityFile(@PathVariable String knowledgeBaseId,
            @RequestPart("file") MultipartFile file,
            @RequestParam(name = "domain", required = false) String domain) {
        TurKnowledgeBase kb = knowledgeBase(knowledgeBaseId);
        try {
            return turAuthorityFileImportService.importAuthorityFile(kb, file.getBytes(), domain);
        } catch (IOException e) {
            throw new IllegalArgumentException("Could not read the uploaded file: " + e.getMessage());
        }
    }

    @Operation(summary = "Draft a microthesaurus hierarchy with the LLM (never persisted — for review)")
    @PostMapping("/generate")
    public TurThesaurusDraft generate(@PathVariable String knowledgeBaseId,
            @RequestBody TurThesaurusGenerationRequest request) {
        knowledgeBase(knowledgeBaseId);
        return turThesaurusGenerationService.generate(request);
    }

    @Operation(summary = "Persist a reviewed generated draft as a new microthesaurus")
    @PostMapping("/from-draft")
    public TurMicrothesaurusDto createFromDraft(@PathVariable String knowledgeBaseId,
            @RequestBody TurThesaurusDraft draft) {
        return turThesaurusGenerationService.materialize(knowledgeBase(knowledgeBaseId), draft);
    }
}
