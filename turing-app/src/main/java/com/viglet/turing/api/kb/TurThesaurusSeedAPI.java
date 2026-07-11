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

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.viglet.turing.api.exception.TurNotFoundException;
import com.viglet.turing.kb.TurThesaurusSeedService;
import com.viglet.turing.persistence.dto.kb.TurMicrothesaurusDto;
import com.viglet.turing.persistence.dto.kb.TurThesaurusSeedDto;
import com.viglet.turing.persistence.model.kb.TurKnowledgeBase;
import com.viglet.turing.persistence.repository.kb.TurKnowledgeBaseRepository;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * T674 / §XL (Block AQ) — the seed-library browse area: list the bundled,
 * pre-populated microthesauri (education pt/en/it) and import one into a
 * Knowledge Base on demand.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@RestController
@RequestMapping("/api/kb")
@Tag(name = "Knowledge Base Seed Library", description = "Bundled importable microthesauri")
public class TurThesaurusSeedAPI {

    private final TurKnowledgeBaseRepository turKnowledgeBaseRepository;
    private final TurThesaurusSeedService seedService;

    public TurThesaurusSeedAPI(TurKnowledgeBaseRepository turKnowledgeBaseRepository,
            TurThesaurusSeedService seedService) {
        this.turKnowledgeBaseRepository = turKnowledgeBaseRepository;
        this.seedService = seedService;
    }

    @Operation(summary = "List bundled seed microthesauri available to import")
    @GetMapping("/seeds")
    public List<TurThesaurusSeedDto> listSeeds() {
        return seedService.list();
    }

    @Operation(summary = "Import a bundled seed microthesaurus into a knowledge base")
    @PostMapping("/{knowledgeBaseId}/seeds/{seedId}")
    public TurMicrothesaurusDto importSeed(@PathVariable String knowledgeBaseId,
            @PathVariable String seedId) {
        TurKnowledgeBase kb = turKnowledgeBaseRepository.findById(knowledgeBaseId)
                .orElseThrow(() -> TurNotFoundException.of("Knowledge Base", knowledgeBaseId));
        return seedService.importSeed(kb, seedId);
    }
}
