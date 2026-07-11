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

import com.viglet.turing.persistence.dto.kb.TurKnowledgeBaseDto;
import com.viglet.turing.persistence.model.kb.TurKnowledgeBase;
import com.viglet.turing.persistence.model.kb.TurKnowledgeBaseSource;
import com.viglet.turing.persistence.repository.kb.TurKnowledgeBaseRepository;

/**
 * T669 / §XL (Block AQ) — CRUD over the Knowledge Base aggregate root. Admin/CRUD
 * reads go straight through the JPA repository (the bounded domain-layer rule);
 * tenant isolation is applied by Hibernate through the entity's {@code @TenantId}.
 * Invalid input raises {@link IllegalArgumentException}, mapped to HTTP 400 by the
 * global handler.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Service
public class TurKnowledgeBaseService {

    private final TurKnowledgeBaseRepository repository;

    public TurKnowledgeBaseService(TurKnowledgeBaseRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<TurKnowledgeBaseDto> list() {
        return repository.findByOrderByNameAsc().stream().map(TurKnowledgeBaseService::toDto).toList();
    }

    @Transactional(readOnly = true)
    public Optional<TurKnowledgeBaseDto> get(String id) {
        return repository.findById(id).map(TurKnowledgeBaseService::toDto);
    }

    @Transactional
    public TurKnowledgeBaseDto create(TurKnowledgeBaseDto dto) {
        TurKnowledgeBase entity = new TurKnowledgeBase();
        entity.setCreationDate(LocalDateTime.now());
        apply(entity, dto);
        return toDto(repository.save(entity));
    }

    @Transactional
    public Optional<TurKnowledgeBaseDto> update(String id, TurKnowledgeBaseDto dto) {
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

    private void apply(TurKnowledgeBase entity, TurKnowledgeBaseDto dto) {
        if (dto == null || StringUtils.isBlank(dto.name())) {
            throw new IllegalArgumentException("Knowledge base name is required");
        }
        entity.setName(dto.name().trim());
        entity.setDescription(dto.description());
        entity.setSource(dto.source() == null ? TurKnowledgeBaseSource.USER : dto.source());
        entity.setModificationDate(LocalDateTime.now());
    }

    static TurKnowledgeBaseDto toDto(TurKnowledgeBase entity) {
        return new TurKnowledgeBaseDto(
                entity.getId(),
                entity.getName(),
                entity.getDescription(),
                entity.getSource(),
                entity.getTurMicrothesauri() == null ? 0 : entity.getTurMicrothesauri().size());
    }
}
