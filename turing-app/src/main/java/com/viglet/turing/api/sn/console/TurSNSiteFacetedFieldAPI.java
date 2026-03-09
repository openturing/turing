/*
 * Copyright (C) 2016-2024 the original author or authors.
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
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.viglet.turing.api.sn.bean.TurSNSiteFacetOrderingDto;
import com.viglet.turing.persistence.model.sn.field.TurSNSiteCustomFacet;
import com.viglet.turing.persistence.model.sn.field.TurSNSiteFieldExt;
import com.viglet.turing.persistence.repository.sn.TurSNSiteRepository;
import com.viglet.turing.persistence.repository.sn.field.TurSNSiteFieldExtRepository;
import com.viglet.turing.sn.TurSNFieldProcess;
import com.viglet.turing.sn.facet.TurSNFacetDefinition;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;

/**
 * @author Alexandre Oliveira
 * @since 0.3.9
 */
@Slf4j
@RestController
@RequestMapping("/api/sn/{snSiteId}/facet")
@Tag(name = "Semantic Navigation Faceted Field", description = "Semantic Navigation Faceted Field API")
public class TurSNSiteFacetedFieldAPI {
        private final TurSNSiteFieldExtRepository turSNSiteFieldExtRepository;
        private final TurSNSiteRepository turSNSiteRepository;
        private final TurSNFieldProcess turSNFieldProcess;

        public TurSNSiteFacetedFieldAPI(TurSNSiteFieldExtRepository turSNSiteFieldExtRepository,
                        TurSNSiteRepository turSNSiteRepository,
                        TurSNFieldProcess turSNFieldProcess) {
                this.turSNSiteFieldExtRepository = turSNSiteFieldExtRepository;
                this.turSNSiteRepository = turSNSiteRepository;
                this.turSNFieldProcess = turSNFieldProcess;
        }

        @Operation(summary = "Semantic Navigation Site Faceted Field List")
        @GetMapping
        public List<TurSNSiteFacetOrderingDto> turSNSiteFacetdFieldExtList(@PathVariable String snSiteId) {
                return getAllFacetEntries(snSiteId);
        }

        @Operation(summary = "Update a Semantic Navigation Site Faceted Field Ordering")
        @PutMapping("/ordering")
        @CacheEvict(value = { "findByTurSNSiteAndFacetAndEnabledOrderByFacetPosition" }, allEntries = true)
        public List<TurSNSiteFacetOrderingDto> turSNSiteFieldUpdate(@PathVariable String snSiteId,
                        @RequestBody List<TurSNSiteFacetOrderingDto> turSNSiteFacetOrderings) {
                return turSNSiteRepository.findById(snSiteId)
                                .map(turSNSite -> {
                                        List<TurSNSiteFieldExt> enabledFields = turSNSiteFieldExtRepository
                                                        .findByTurSNSiteAndEnabled(turSNSite, 1);

                                        Map<String, TurSNSiteFieldExt> facetedFieldById = enabledFields.stream()
                                                        .filter(field -> field.getFacet() == 1)
                                                        .collect(HashMap::new,
                                                                        (map, field) -> map.put(field.getId(), field),
                                                                        HashMap::putAll);

                                        Map<String, TurSNSiteFieldExt> customFacetFieldById = new HashMap<>();
                                        Map<String, TurSNSiteCustomFacet> customFacetById = new HashMap<>();
                                        enabledFields.forEach(field -> Optional.ofNullable(field.getCustomFacets())
                                                        .orElse(Collections.emptySet())
                                                        .forEach(customFacet -> {
                                                                customFacetFieldById.put(customFacet.getId(), field);
                                                                customFacetById.put(customFacet.getId(), customFacet);
                                                        }));

                                        Set<TurSNSiteFieldExt> fieldsToSave = new HashSet<>();
                                        turSNSiteFacetOrderings.stream()
                                                        .filter(fieldsFromRequest -> fieldsFromRequest
                                                                        .getFacetPosition() != null
                                                                        && fieldsFromRequest
                                                                                        .getFacetPosition() > 0)
                                                        .forEach(fieldsFromRequest -> {
                                                                TurSNSiteFieldExt fieldExtension = facetedFieldById
                                                                                .get(fieldsFromRequest.getId());
                                                                if (fieldExtension != null) {
                                                                        fieldExtension.setFacetPosition(
                                                                                        fieldsFromRequest
                                                                                                        .getFacetPosition());
                                                                        fieldsToSave.add(fieldExtension);
                                                                        return;
                                                                }

                                                                TurSNSiteCustomFacet customFacet = customFacetById
                                                                                .get(fieldsFromRequest.getId());
                                                                TurSNSiteFieldExt customFieldExtension = customFacetFieldById
                                                                                .get(fieldsFromRequest.getId());
                                                                if (customFacet != null
                                                                                && customFieldExtension != null) {
                                                                        customFacet.setFacetPosition(
                                                                                        fieldsFromRequest
                                                                                                        .getFacetPosition());
                                                                        fieldsToSave.add(customFieldExtension);
                                                                }
                                                        });

                                        if (!fieldsToSave.isEmpty()) {
                                                turSNSiteFieldExtRepository.saveAll(fieldsToSave);
                                        }

                                        return getAllFacetEntries(snSiteId);
                                })
                                .orElseGet(Collections::emptyList);
        }

        private List<TurSNSiteFacetOrderingDto> getAllFacetEntries(String snSiteId) {
                return turSNFieldProcess.getTurSNSiteFacetOrdering(snSiteId)
                                .map(facetDefinitions -> facetDefinitions.stream()
                                                .map(this::toFacetOrderingDto)
                                                .toList())
                                .orElseGet(Collections::emptyList);
        }

        private TurSNSiteFacetOrderingDto toFacetOrderingDto(TurSNFacetDefinition facetDefinition) {
                TurSNSiteFieldExt fieldExt = facetDefinition.getFieldExt();
                return new TurSNSiteFacetOrderingDto()
                                .setId(facetDefinition.getId())
                                .setName(facetDefinition.getName())
                                .setFacetName(facetDefinition.getLabel())
                                .setFacetPosition(facetDefinition.getPosition())
                                .setFieldExtId(fieldExt != null ? fieldExt.getId() : null)
                                .setFieldExtName(fieldExt != null ? fieldExt.getName() : null)
                                .setCustomFacet(facetDefinition.isCustomFacet());
        }
}
