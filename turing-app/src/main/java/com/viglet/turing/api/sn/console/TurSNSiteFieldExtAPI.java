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

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.jetbrains.annotations.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.viglet.turing.persistence.dto.sn.field.TurSNSiteFieldExtDto;
import com.viglet.turing.persistence.mapper.sn.field.TurSNSiteFieldExtMapper;
import com.viglet.turing.persistence.model.sn.TurSNSite;
import com.viglet.turing.persistence.model.sn.TurSNSiteFacetRangeEnum;
import com.viglet.turing.persistence.model.sn.field.TurSNSiteFacetFieldEnum;
import com.viglet.turing.persistence.model.sn.field.TurSNSiteFacetFieldSortEnum;
import com.viglet.turing.persistence.model.sn.field.TurSNSiteField;
import com.viglet.turing.persistence.model.sn.field.TurSNSiteFieldExt;
import com.viglet.turing.persistence.model.sn.field.TurSNSiteFieldExtFacet;
import com.viglet.turing.persistence.repository.se.TurSEInstanceRepository;
import com.viglet.turing.persistence.repository.sn.TurSNSiteRepository;
import com.viglet.turing.persistence.repository.sn.field.TurSNSiteFieldExtFacetRepository;
import com.viglet.turing.persistence.repository.sn.field.TurSNSiteFieldExtRepository;
import com.viglet.turing.persistence.repository.sn.field.TurSNSiteFieldRepository;
import com.viglet.turing.sn.TurSNFieldType;
import com.viglet.turing.sn.template.TurSNTemplate;
import com.viglet.turing.solr.TurSolrFieldAction;
import com.viglet.turing.solr.TurSolrUtils;
import com.viglet.turing.spring.utils.TurPersistenceUtils;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequestMapping("/api/sn/{snSiteId}/field/ext")
@Tag(name = "Semantic Navigation Field Ext", description = "Semantic Navigation Field Ext API")
public class TurSNSiteFieldExtAPI {
    public static final String TEXT_GENERAL = "text_general";
    public static final String MULTI_VALUED = "multiValued";
    public static final String STORED = "stored";
    public static final String INDEXED = "indexed";
    public static final String TYPE = "type";
    public static final String STRING = "string";
    public static final String ADD_FIELD = "add-field";
    public static final String TEXT = "_text_";
    public static final String SOURCE = "source";
    public static final String ADD_COPY_FIELD = "add-copy-field";
    public static final String DEST = "dest";
    public static final String PDATE = "pdate";
    public static final String NAME = "name";
    public static final String SOLR_SCHEMA_REQUEST = "http://%s:%d/solr/%s/schema";

    private final TurSNSiteRepository turSNSiteRepository;
    private final TurSNSiteFieldExtRepository turSNSiteFieldExtRepository;
    private final TurSNSiteFieldExtFacetRepository turSNSiteFieldExtFacetRepository;
    private final TurSNSiteFieldRepository turSNSiteFieldRepository;
    private final TurSEInstanceRepository turSEInstanceRepository;
    private final TurSNTemplate turSNTemplate;
    private final TurSNSiteFieldExtMapper turSNSiteFieldExtMapper;

    public TurSNSiteFieldExtAPI(TurSNSiteRepository turSNSiteRepository,
            TurSNSiteFieldExtRepository turSNSiteFieldExtRepository,
            TurSNSiteFieldExtFacetRepository turSNSiteFieldExtFacetRepository,
            TurSNSiteFieldRepository turSNSiteFieldRepository,
            TurSEInstanceRepository turSEInstanceRepository,
            TurSNTemplate turSNTemplate,
            TurSNSiteFieldExtMapper turSNSiteFieldExtMapper) {
        this.turSNSiteRepository = turSNSiteRepository;
        this.turSNSiteFieldExtRepository = turSNSiteFieldExtRepository;
        this.turSNSiteFieldExtFacetRepository = turSNSiteFieldExtFacetRepository;
        this.turSNSiteFieldRepository = turSNSiteFieldRepository;
        this.turSEInstanceRepository = turSEInstanceRepository;
        this.turSNTemplate = turSNTemplate;
        this.turSNSiteFieldExtMapper = turSNSiteFieldExtMapper;
    }

    @Operation(summary = "Semantic Navigation Site Field Ext List")
    @Transactional
    @GetMapping
    public List<TurSNSiteFieldExtDto> turSNSiteFieldExtList(@PathVariable String snSiteId) {
        return turSNSiteRepository.findById(snSiteId)
                .map(turSNSite -> {
                    updateFieldExtFromSNSite(turSNSite);
                    return turSNSiteFieldExtMapper.toDtoList(turSNSiteFieldExtRepository
                            .findByTurSNSite(TurPersistenceUtils.orderByNameIgnoreCase(), turSNSite));
                })
                .orElse(Collections.emptyList());
    }

    @Operation(summary = "Show a Semantic Navigation Site Field Ext")
    @GetMapping("/{id}")
    public TurSNSiteFieldExtDto turSNSiteFieldExtGet(@PathVariable String snSiteId, @PathVariable String id) {
        TurSNSiteFieldExt turSNSiteFieldExt = turSNSiteFieldExtRepository.findById(id)
                .orElse(TurSNSiteFieldExt.builder().build());
        turSNSiteFieldExt.setFacetLocales(
                new HashSet<>(turSNSiteFieldExtFacetRepository.findByTurSNSiteFieldExt(turSNSiteFieldExt)));
        return turSNSiteFieldExtMapper.toDto(turSNSiteFieldExt);
    }

    @Operation(summary = "Semantic Navigation Site Field Ext structure")
    @GetMapping("structure")
    public TurSNSiteFieldExtDto turSNSiteFieldExtStructure(@PathVariable String snSiteId) {
        return turSNSiteRepository.findById(snSiteId)
                .map(turSNSite -> {
                    TurSNSiteFieldExt fieldExt = new TurSNSiteFieldExt();
                    fieldExt.setTurSNSite(turSNSite);
                    return turSNSiteFieldExtMapper.toDto(fieldExt);
                })
                .orElseGet(TurSNSiteFieldExtDto::new);
    }

    @Operation(summary = "Create a Semantic Navigation Site Field Ext")
    @PostMapping
    public TurSNSiteFieldExtDto turSNSiteFieldExtAdd(@PathVariable String snSiteId,
            @RequestBody TurSNSiteFieldExtDto turSNSiteFieldExtDto) {
        TurSNSiteFieldExt turSNSiteFieldExt = turSNSiteFieldExtMapper.toEntity(turSNSiteFieldExtDto);
        return turSNSiteFieldExtMapper.toDto(createSEField(snSiteId, turSNSiteFieldExt));
    }

    @Operation(summary = "Update a Semantic Navigation Site Field Ext")
    @PutMapping("/{id}")
    @Transactional
    public TurSNSiteFieldExtDto turSNSiteFieldExtUpdate(@PathVariable String snSiteId, @PathVariable String id,
            @RequestBody TurSNSiteFieldExtDto payloadDto) {
        TurSNSiteFieldExt payload = turSNSiteFieldExtMapper.toEntity(payloadDto);
        return this.turSNSiteFieldExtRepository.findById(id).map(existing -> {
            updateFieldExtProperties(existing, payload);

            turSNSiteRepository.findById(snSiteId).ifPresent(turSNSite -> {
                existing.setFacetPosition(calculateFacetPositionForUpdate(payload, turSNSite));
                this.turSNSiteFieldExtRepository.save(existing);
                this.updateExternalField(payload, turSNSite);
            });
            return turSNSiteFieldExtMapper.toDto(existing);
        }).orElseGet(TurSNSiteFieldExtDto::new);
    }

    @Transactional
    @Operation(summary = "Delete a Semantic Navigation Site Field Ext")
    @DeleteMapping("/{id}")
    public boolean turSNSiteFieldExtDelete(@PathVariable String snSiteId, @PathVariable String id) {
        return this.turSNSiteFieldExtRepository.findById(id).map(turSNSiteFieldExt -> {
            turSNSiteRepository.findById(snSiteId)
                    .ifPresent(turSNSite -> turSNSiteFieldRepository.findById(turSNSiteFieldExt.getExternalId())
                            .ifPresent(turSNSiteField -> this.deleteSolrSchema(turSNSite, turSNSiteField)));
            if (TurSNFieldType.SE.equals(turSNSiteFieldExt.getSnType())) {
                this.turSNSiteFieldRepository.delete(turSNSiteFieldExt.getExternalId());
            }
            this.turSNSiteFieldExtRepository.delete(id);
            return true;
        }).orElse(false);
    }

    private void updateFieldExtFromSNSite(TurSNSite turSNSite) {
        turSNSiteFieldExtRepository.deleteByTurSNSiteAndSnType(turSNSite, TurSNFieldType.NER);
        Map<String, TurSNSiteField> fieldMap = createFieldMap(turSNSite);
        List<TurSNSiteFieldExt> fieldExtList = this.turSNSiteFieldExtRepository
                .findByTurSNSite(TurPersistenceUtils.orderByNameIgnoreCase(), turSNSite);
        removeDuplicatedFields(fieldMap, fieldExtList);
        fieldMap.values().forEach(field -> {
            TurSNSiteFieldExt fieldExt = saveSNSiteFieldExt(turSNSite, field);
            fieldExtList.add(fieldExt);
        });
    }

    private Map<String, TurSNSiteField> createFieldMap(TurSNSite turSNSite) {
        List<TurSNSiteField> fields = turSNSiteFieldRepository.findByTurSNSite(turSNSite);
        if (fields.isEmpty()) {
            turSNTemplate.createSEFields(turSNSite);
            fields = turSNSiteFieldRepository.findByTurSNSite(turSNSite);
        }
        return fields.stream().collect(Collectors.toMap(TurSNSiteField::getId, f -> f));
    }

    private void removeDuplicatedFields(Map<String, TurSNSiteField> fieldMap,
            List<TurSNSiteFieldExt> fieldExts) {
        fieldExts.stream()
                .filter(ext -> TurSNFieldType.SE.equals(ext.getSnType()))
                .map(TurSNSiteFieldExt::getExternalId)
                .forEach(fieldMap::remove);
    }

    private TurSNSiteFieldExt saveSNSiteFieldExt(TurSNSite turSNSite, TurSNSiteField field) {
        return turSNSiteFieldExtRepository.save(TurSNSiteFieldExt.builder()
                .enabled(0)
                .name(field.getName())
                .description(field.getDescription())
                .facet(0)
                .facetName(field.getName())
                .facetRange(TurSNSiteFacetRangeEnum.DISABLED)
                .facetType(TurSNSiteFacetFieldEnum.DEFAULT)
                .facetItemType(TurSNSiteFacetFieldEnum.DEFAULT)
                .facetSort(TurSNSiteFacetFieldSortEnum.COUNT)
                .showAllFacetItems(false)
                .secondaryFacet(false)
                .hl(0)
                .multiValued(field.getMultiValued())
                .facetPosition(0)
                .mlt(0)
                .externalId(field.getId())
                .snType(TurSNFieldType.SE)
                .type(field.getType())
                .turSNSite(turSNSite)
                .build());
    }

    private void updateFieldExtProperties(TurSNSiteFieldExt existing, TurSNSiteFieldExt payload) {
        if (payload == null || existing == null) {
            return;
        }

        existing.setName(payload.getName());
        existing.setDescription(payload.getDescription());
        existing.setType(payload.getType());
        existing.setFacet(payload.getFacet());
        existing.setFacetName(payload.getFacetName());
        existing.setMultiValued(payload.getMultiValued());
        existing.setFacetRange(payload.getFacetRange());
        existing.setFacetSort(payload.getFacetSort());
        existing.setFacetType(payload.getFacetType());
        existing.setFacetItemType(payload.getFacetItemType());
        existing.setSecondaryFacet(Boolean.TRUE.equals(payload.getSecondaryFacet()));
        existing.setShowAllFacetItems(Boolean.TRUE.equals(payload.getShowAllFacetItems()));
        existing.setHl(payload.getHl());
        existing.setEnabled(payload.getEnabled());
        existing.setMlt(payload.getMlt());
        existing.setExternalId(payload.getExternalId());
        existing.setRequired(payload.getRequired());
        existing.setDefaultValue(payload.getDefaultValue());
        existing.setSnType(payload.getSnType());

        if (payload.getFacetLocales() != null) {
            java.util.Set<java.util.Locale> incomingLocales = payload.getFacetLocales().stream()
                    .map(TurSNSiteFieldExtFacet::getLocale)
                    .collect(java.util.stream.Collectors.toSet());

            existing.getFacetLocales().removeIf(facet -> !incomingLocales.contains(facet.getLocale()));

            for (TurSNSiteFieldExtFacet incomingFacet : payload.getFacetLocales()) {
                existing.getFacetLocales().stream()
                        .filter(f -> f.getLocale() != null && incomingFacet.getLocale() != null
                                && f.getLocale().equals(incomingFacet.getLocale()))
                        .findFirst()
                        .ifPresentOrElse(
                                existingFacet -> existingFacet.setLabel(incomingFacet.getLabel()),
                                () -> {
                                    incomingFacet.setId(null);
                                    incomingFacet.setTurSNSiteFieldExt(existing);
                                    existing.getFacetLocales().add(incomingFacet);
                                });
            }
        } else {
            existing.getFacetLocales().clear();
        }
    }

    private Integer calculateFacetPositionForUpdate(TurSNSiteFieldExt fieldExt, TurSNSite turSNSite) {
        if (fieldExt.getFacet() == 1) {
            Integer position = fieldExt.getFacetPosition();
            return (position != null && position > 0) ? position : getFacetPositionIncrement(turSNSite);
        }
        return 0;
    }

    @NotNull
    private static Set<TurSNSiteFieldExtFacet> getTurSNSiteFieldExtFacets(TurSNSiteFieldExt existing,
            TurSNSiteFieldExt payload) {
        Set<TurSNSiteFieldExtFacet> facetLocales = new HashSet<>();
        if (payload == null || payload.getFacetLocales() == null)
            return facetLocales;
        for (TurSNSiteFieldExtFacet fieldExtFacet : payload.getFacetLocales()) {
            fieldExtFacet.setTurSNSiteFieldExt(existing);
            facetLocales.add(fieldExtFacet);
        }
        return facetLocales;
    }

    private static boolean isFacetEnabled(TurSNSiteFieldExt fieldExt) {
        return fieldExt.getFacet() == 1;
    }

    @NotNull
    private Integer getFacetPositionIncrement(TurSNSite turSNSite) {
        return this.turSNSiteFieldExtRepository.findMaxFacetPosition(turSNSite)
                .map(max -> max + 1)
                .orElse(1);
    }

    private TurSNSiteFieldExt createSEField(String snSiteId, TurSNSiteFieldExt fieldExt) {
        return turSNSiteRepository.findById(snSiteId)
                .map(turSNSite -> createSEFieldForSite(turSNSite, fieldExt))
                .orElse(TurSNSiteFieldExt.builder().build());
    }

    private TurSNSiteFieldExt createSEFieldForSite(TurSNSite turSNSite, TurSNSiteFieldExt fieldExt) {
        if (isDuplicateFieldName(turSNSite, fieldExt)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Field name already exists for this site");
        }
        TurSNSiteField field = buildAndSaveSiteField(turSNSite, fieldExt);
        TurSNSiteFieldExt savedFieldExt = buildAndSaveFieldExt(turSNSite, fieldExt, field);
        updateSolrSchema(turSNSite, field);
        return savedFieldExt;
    }

    private boolean isDuplicateFieldName(TurSNSite turSNSite, TurSNSiteFieldExt fieldExt) {
        String name = fieldExt.getName();
        if (name == null || name.isBlank())
            return true;
        return turSNSiteFieldExtRepository.existsByTurSNSiteAndName(turSNSite, name)
                || turSNSiteFieldRepository.existsByTurSNSiteAndName(turSNSite, name);
    }

    private TurSNSiteField buildAndSaveSiteField(TurSNSite turSNSite, TurSNSiteFieldExt fieldExt) {
        TurSNSiteField field = new TurSNSiteField();
        field.setDescription(fieldExt.getDescription());
        field.setMultiValued(fieldExt.getMultiValued());
        field.setName(fieldExt.getName());
        field.setType(fieldExt.getType());
        field.setTurSNSite(turSNSite);
        return turSNSiteFieldRepository.save(field);
    }

    private TurSNSiteFieldExt buildAndSaveFieldExt(TurSNSite turSNSite, TurSNSiteFieldExt fieldExt,
            TurSNSiteField field) {
        fieldExt.setId(null);
        fieldExt.setTurSNSite(turSNSite);
        fieldExt.setSnType(TurSNFieldType.SE);
        fieldExt.setExternalId(field.getId());
        fieldExt.setFacetPosition(calculateFacetPosition(fieldExt, turSNSite));

        if (fieldExt.getFacetLocales() != null) {
            fieldExt.getFacetLocales().forEach(facet -> {
                facet.setId(null);
                facet.setTurSNSiteFieldExt(fieldExt);
            });
        }
        return turSNSiteFieldExtRepository.save(fieldExt);
    }

    private Integer calculateFacetPosition(TurSNSiteFieldExt fieldExt, TurSNSite turSNSite) {
        return isFacetEnabled(fieldExt) ? getFacetPositionIncrement(turSNSite) : 0;
    }

    private void deleteSolrSchema(TurSNSite turSNSite, TurSNSiteField field) {
        if (turSNSite.getTurSEInstance() == null || turSNSite.getTurSNSiteLocales() == null
                || turSNSite.getTurSNSiteLocales().isEmpty())
            return;
        turSEInstanceRepository.findById(turSNSite.getTurSEInstance().getId())
                .ifPresent(turSEInstance -> turSNSite.getTurSNSiteLocales().forEach(
                        locale -> TurSolrUtils.deleteField(
                                turSEInstance,
                                locale.getCore(),
                                field.getName(),
                                field.getType())));
    }

    private void updateSolrSchema(TurSNSite turSNSite, TurSNSiteField field) {
        if (turSNSite.getTurSEInstance() == null || turSNSite.getTurSNSiteLocales() == null
                || turSNSite.getTurSNSiteLocales().isEmpty())
            return;
        turSEInstanceRepository.findById(turSNSite.getTurSEInstance().getId())
                .ifPresent(turSEInstance -> turSNSite.getTurSNSiteLocales().forEach(
                        locale -> TurSolrUtils.addOrUpdateField(
                                TurSolrFieldAction.ADD,
                                turSEInstance,
                                locale.getCore(),
                                field.getName(),
                                field.getType(),
                                true,
                                field.getMultiValued() == 1)));
    }

    private void updateExternalField(TurSNSiteFieldExt fieldExt, TurSNSite turSNSite) {
        if (TurSNFieldType.SE.equals(fieldExt.getSnType())) {
            turSNSiteFieldRepository.findById(fieldExt.getExternalId()).ifPresent(field -> {
                field.setDescription(fieldExt.getDescription());
                field.setMultiValued(fieldExt.getMultiValued());
                field.setName(fieldExt.getName());
                field.setType(fieldExt.getType());
                this.turSNSiteFieldRepository.save(field);
                updateSolrSchema(turSNSite, field);
            });
        }
    }
}
