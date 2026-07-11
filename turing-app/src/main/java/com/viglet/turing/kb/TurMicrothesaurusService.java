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

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.viglet.turing.persistence.dto.kb.TurMicrothesaurusDto;
import com.viglet.turing.persistence.model.kb.TurKnowledgeBase;
import com.viglet.turing.persistence.model.kb.TurMicrothesaurus;
import com.viglet.turing.persistence.repository.kb.TurMicrothesaurusRepository;
import com.viglet.turing.persistence.repository.kb.TurSNSiteMicrothesaurusRepository;
import com.viglet.turing.sn.kb.TurMicrothesaurusDictionaryService;

/**
 * T669 / §XL (Block AQ) — CRUD over microthesaurus trees, scoped to a
 * {@link TurKnowledgeBase}. Each tree is one language + one domain (both
 * required). Invalid input raises {@link IllegalArgumentException} → HTTP 400.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Service
public class TurMicrothesaurusService {

    private final TurMicrothesaurusRepository repository;
    private final TurSNSiteMicrothesaurusRepository siteSelectionRepository;
    private final TurMicrothesaurusDictionaryService dictionaryService;

    public TurMicrothesaurusService(TurMicrothesaurusRepository repository,
            TurSNSiteMicrothesaurusRepository siteSelectionRepository,
            TurMicrothesaurusDictionaryService dictionaryService) {
        this.repository = repository;
        this.siteSelectionRepository = siteSelectionRepository;
        this.dictionaryService = dictionaryService;
    }

    @Transactional(readOnly = true)
    public List<TurMicrothesaurusDto> listByKnowledgeBase(TurKnowledgeBase knowledgeBase) {
        return repository.findByTurKnowledgeBase(knowledgeBase).stream()
                .map(TurMicrothesaurusService::toDto).toList();
    }

    @Transactional(readOnly = true)
    public Optional<TurMicrothesaurusDto> get(String id) {
        return repository.findById(id).map(TurMicrothesaurusService::toDto);
    }

    @Transactional
    public TurMicrothesaurusDto create(TurKnowledgeBase knowledgeBase, TurMicrothesaurusDto dto) {
        TurMicrothesaurus entity = new TurMicrothesaurus();
        entity.setTurKnowledgeBase(knowledgeBase);
        apply(entity, dto);
        return toDto(repository.save(entity));
    }

    @Transactional
    public Optional<TurMicrothesaurusDto> update(String id, TurMicrothesaurusDto dto) {
        return repository.findById(id).map(entity -> {
            apply(entity, dto);
            TurMicrothesaurusDto saved = toDto(repository.save(entity));
            dictionaryService.evictAll();
            return saved;
        });
    }

    @Transactional
    public boolean delete(String id) {
        if (!repository.existsById(id)) {
            return false;
        }
        // T670: drop any per-site selections pointing at this tree (soft FK, no DB cascade).
        siteSelectionRepository.deleteByMicrothesaurusId(id);
        repository.deleteById(id);
        dictionaryService.evictAll();
        return true;
    }

    private void apply(TurMicrothesaurus entity, TurMicrothesaurusDto dto) {
        if (dto == null || StringUtils.isBlank(dto.name())) {
            throw new IllegalArgumentException("Microthesaurus name is required");
        }
        if (dto.language() == null) {
            throw new IllegalArgumentException("Microthesaurus language is required");
        }
        if (StringUtils.isBlank(dto.domain())) {
            throw new IllegalArgumentException("Microthesaurus domain is required");
        }
        entity.setName(dto.name().trim());
        entity.setDescription(dto.description());
        entity.setLanguage(dto.language());
        entity.setDomain(dto.domain().trim());
        entity.setModificationDate(LocalDateTime.now());
    }

    static TurMicrothesaurusDto toDto(TurMicrothesaurus entity) {
        return new TurMicrothesaurusDto(
                entity.getId(),
                entity.getName(),
                entity.getDescription(),
                entity.getLanguage(),
                entity.getDomain(),
                entity.getTurKnowledgeBase() == null ? null : entity.getTurKnowledgeBase().getId(),
                entity.getTurThesaurusTerms() == null ? 0 : entity.getTurThesaurusTerms().size());
    }
}
