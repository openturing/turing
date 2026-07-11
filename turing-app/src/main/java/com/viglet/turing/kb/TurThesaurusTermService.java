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
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.viglet.turing.persistence.dto.kb.TurThesaurusTermDto;
import com.viglet.turing.persistence.dto.kb.TurThesaurusTermRelationDto;
import com.viglet.turing.persistence.dto.kb.TurThesaurusTermVariationDto;
import com.viglet.turing.persistence.model.kb.TurMicrothesaurus;
import com.viglet.turing.persistence.model.kb.TurThesaurusRelationDirectionality;
import com.viglet.turing.persistence.model.kb.TurThesaurusTerm;
import com.viglet.turing.persistence.model.kb.TurThesaurusTermRelation;
import com.viglet.turing.persistence.model.kb.TurThesaurusTermVariation;
import com.viglet.turing.persistence.repository.kb.TurThesaurusTermRepository;
import com.viglet.turing.sn.kb.TurMicrothesaurusDictionaryService;

/**
 * T669 / §XL (Block AQ) — CRUD over thesaurus terms, scoped to a
 * {@link TurMicrothesaurus}. A term carries its recognition {@code variations}
 * and non-hierarchical {@code relations} inline; the broader/narrower link is the
 * soft {@code parentTermId}. Invalid input raises {@link IllegalArgumentException}
 * → HTTP 400.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Service
public class TurThesaurusTermService {

    private final TurThesaurusTermRepository repository;
    private final TurMicrothesaurusDictionaryService dictionaryService;

    public TurThesaurusTermService(TurThesaurusTermRepository repository,
            TurMicrothesaurusDictionaryService dictionaryService) {
        this.repository = repository;
        this.dictionaryService = dictionaryService;
    }

    @Transactional(readOnly = true)
    public List<TurThesaurusTermDto> listByMicrothesaurus(TurMicrothesaurus microthesaurus) {
        return repository.findByTurMicrothesaurus(microthesaurus).stream()
                .map(TurThesaurusTermService::toDto).toList();
    }

    /** Root terms of a microthesaurus (those with no broader/parent term). */
    @Transactional(readOnly = true)
    public List<TurThesaurusTermDto> listRoots(TurMicrothesaurus microthesaurus) {
        return repository.findByTurMicrothesaurusAndParentTermIdIsNull(microthesaurus).stream()
                .map(TurThesaurusTermService::toDto).toList();
    }

    /** Direct children (narrower terms) of a term. */
    @Transactional(readOnly = true)
    public List<TurThesaurusTermDto> listChildren(String parentTermId) {
        return repository.findByParentTermId(parentTermId).stream()
                .map(TurThesaurusTermService::toDto).toList();
    }

    @Transactional(readOnly = true)
    public Optional<TurThesaurusTermDto> get(String id) {
        return repository.findById(id).map(TurThesaurusTermService::toDto);
    }

    @Transactional
    public TurThesaurusTermDto create(TurMicrothesaurus microthesaurus, TurThesaurusTermDto dto) {
        TurThesaurusTerm entity = new TurThesaurusTerm();
        entity.setTurMicrothesaurus(microthesaurus);
        apply(entity, dto);
        TurThesaurusTermDto saved = toDto(repository.save(entity));
        dictionaryService.evictAll();
        return saved;
    }

    @Transactional
    public Optional<TurThesaurusTermDto> update(String id, TurThesaurusTermDto dto) {
        return repository.findById(id).map(entity -> {
            apply(entity, dto);
            TurThesaurusTermDto saved = toDto(repository.save(entity));
            dictionaryService.evictAll();
            return saved;
        });
    }

    @Transactional
    public boolean delete(String id) {
        if (!repository.existsById(id)) {
            return false;
        }
        repository.deleteById(id);
        dictionaryService.evictAll();
        return true;
    }

    private void apply(TurThesaurusTerm entity, TurThesaurusTermDto dto) {
        if (dto == null || StringUtils.isBlank(dto.label())) {
            throw new IllegalArgumentException("Thesaurus term label is required");
        }
        if (dto.parentTermId() != null && dto.parentTermId().equals(entity.getId())) {
            throw new IllegalArgumentException("A term cannot be its own parent");
        }
        entity.setLabel(dto.label().trim());
        entity.setEnabled(dto.enabled());
        entity.setTermOrder(dto.termOrder());
        entity.setScopeNote(dto.scopeNote());
        entity.setExternalId(dto.externalId());
        entity.setParentTermId(StringUtils.trimToNull(dto.parentTermId()));
        entity.setModificationDate(LocalDateTime.now());
        entity.setVariations(mapVariations(dto.variations()));
        entity.setRelations(mapRelations(entity, dto.relations()));
    }

    private List<TurThesaurusTermVariation> mapVariations(List<TurThesaurusTermVariationDto> dtos) {
        List<TurThesaurusTermVariation> variations = new ArrayList<>();
        if (dtos != null) {
            for (TurThesaurusTermVariationDto v : dtos) {
                if (v == null || StringUtils.isBlank(v.surfaceForm())) {
                    continue;
                }
                TurThesaurusTermVariation variation = new TurThesaurusTermVariation();
                variation.setSurfaceForm(v.surfaceForm().trim());
                variation.setWeight(v.weight() <= 0 ? 100.0d : v.weight());
                variation.setCaseSensitive(v.caseSensitive());
                variation.setAccentSensitive(v.accentSensitive());
                variation.setLanguage(v.language());
                variations.add(variation);
            }
        }
        return variations;
    }

    private Set<TurThesaurusTermRelation> mapRelations(TurThesaurusTerm owner,
            List<TurThesaurusTermRelationDto> dtos) {
        Set<TurThesaurusTermRelation> relations = new HashSet<>();
        if (dtos != null) {
            for (TurThesaurusTermRelationDto r : dtos) {
                if (r == null || r.type() == null || StringUtils.isBlank(r.targetTermId())) {
                    throw new IllegalArgumentException(
                            "A term relation requires a type and a target term");
                }
                TurThesaurusTermRelation relation = new TurThesaurusTermRelation();
                relation.setType(r.type());
                relation.setDirectionality(r.directionality() == null
                        ? TurThesaurusRelationDirectionality.UNIDIRECTIONAL
                        : r.directionality());
                relation.setTargetTermId(r.targetTermId());
                relation.setTurThesaurusTerm(owner);
                relations.add(relation);
            }
        }
        return relations;
    }

    static TurThesaurusTermDto toDto(TurThesaurusTerm entity) {
        List<TurThesaurusTermVariationDto> variations = entity.getVariations().stream()
                .map(v -> new TurThesaurusTermVariationDto(v.getSurfaceForm(), v.getWeight(),
                        v.isCaseSensitive(), v.isAccentSensitive(), v.getLanguage()))
                .toList();
        List<TurThesaurusTermRelationDto> relations = entity.getRelations().stream()
                .map(r -> new TurThesaurusTermRelationDto(r.getId(), r.getType(),
                        r.getDirectionality(), r.getTargetTermId()))
                .toList();
        return new TurThesaurusTermDto(
                entity.getId(),
                entity.getLabel(),
                entity.isEnabled(),
                entity.getTermOrder(),
                entity.getScopeNote(),
                entity.getExternalId(),
                entity.getParentTermId(),
                entity.getTurMicrothesaurus() == null ? null
                        : entity.getTurMicrothesaurus().getId(),
                variations,
                relations);
    }
}
