/*
 * Copyright (C) 2016-2026 the original author or authors.
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

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.NOT_FOUND;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.data.domain.Sort;
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

import com.viglet.turing.persistence.model.sn.TurSNSite;
import com.viglet.turing.persistence.model.sn.sort.TurSNSiteCustomSort;
import com.viglet.turing.persistence.model.sn.sort.TurSNSiteCustomSortItem;
import com.viglet.turing.persistence.model.sn.sort.TurSNSiteCustomSortOrderEnum;
import com.viglet.turing.persistence.model.sn.field.TurSNSiteFieldExt;
import com.viglet.turing.persistence.repository.sn.TurSNSiteRepository;
import com.viglet.turing.persistence.repository.sn.sort.TurSNSiteCustomSortRepository;
import com.viglet.turing.persistence.repository.sn.field.TurSNSiteFieldExtRepository;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * REST API for managing custom sort definitions on a Semantic Navigation site.
 *
 * @author Alexandre Oliveira
 * @since 2026.2.4
 */
@RestController
@RequestMapping("/api/sn/{snSiteId}/custom-sort")
@Tag(name = "Semantic Navigation Custom Sort", description = "Semantic Navigation Custom Sort API")
@Transactional
public class TurSNSiteCustomSortAPI {
    private static final String CUSTOM_SORT_NOT_FOUND = "Custom sort not found.";

    private final TurSNSiteRepository turSNSiteRepository;
    private final TurSNSiteCustomSortRepository turSNSiteCustomSortRepository;
    private final TurSNSiteFieldExtRepository turSNSiteFieldExtRepository;

    public TurSNSiteCustomSortAPI(TurSNSiteRepository turSNSiteRepository,
                                   TurSNSiteCustomSortRepository turSNSiteCustomSortRepository,
                                   TurSNSiteFieldExtRepository turSNSiteFieldExtRepository) {
        this.turSNSiteRepository = turSNSiteRepository;
        this.turSNSiteCustomSortRepository = turSNSiteCustomSortRepository;
        this.turSNSiteFieldExtRepository = turSNSiteFieldExtRepository;
    }

    @Operation(summary = "Semantic Navigation Site Custom Sort List")
    @GetMapping
    public List<TurSNSiteCustomSortDto> list(@PathVariable String snSiteId) {
        TurSNSite turSNSite = getSite(snSiteId);
        return turSNSiteCustomSortRepository.findByTurSNSiteWithItems(turSNSite).stream()
                .map(this::toDto)
                .toList();
    }

    @Operation(summary = "Show a Semantic Navigation Site Custom Sort")
    @GetMapping("/{customSortId}")
    public TurSNSiteCustomSortDto get(@PathVariable String snSiteId,
                                       @PathVariable String customSortId) {
        TurSNSite turSNSite = getSite(snSiteId);
        return turSNSiteCustomSortRepository.findByTurSNSiteAndId(turSNSite, customSortId)
                .map(this::toDto)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, CUSTOM_SORT_NOT_FOUND));
    }

    @Operation(summary = "Get available fields for custom sort")
    @GetMapping("/fields")
    public List<TurSNSiteCustomSortFieldOptionDto> getFieldOptions(@PathVariable String snSiteId) {
        TurSNSite turSNSite = getSite(snSiteId);
        return turSNSiteFieldExtRepository.findByTurSNSite(Sort.by(Sort.Order.asc("name")), turSNSite).stream()
                .map(field -> {
                    TurSNSiteCustomSortFieldOptionDto dto = new TurSNSiteCustomSortFieldOptionDto();
                    dto.setId(field.getId());
                    dto.setName(field.getName());
                    dto.setType(field.getType() != null ? field.getType().toString() : null);
                    return dto;
                })
                .toList();
    }

    @Operation(summary = "Create a Semantic Navigation Site Custom Sort")
    @PostMapping
    public TurSNSiteCustomSortDto create(@PathVariable String snSiteId,
                                          @RequestBody TurSNSiteCustomSortDto payload) {
        TurSNSite turSNSite = getSite(snSiteId);
        validatePayload(payload);

        TurSNSiteCustomSort customSort = TurSNSiteCustomSort.builder().build();
        customSort.setTurSNSite(turSNSite);
        applyPayload(customSort, payload);

        TurSNSiteCustomSort saved = turSNSiteCustomSortRepository.save(customSort);
        return toDto(saved);
    }

    @Operation(summary = "Update a Semantic Navigation Site Custom Sort")
    @PutMapping("/{customSortId}")
    public TurSNSiteCustomSortDto update(@PathVariable String snSiteId,
                                          @PathVariable String customSortId,
                                          @RequestBody TurSNSiteCustomSortDto payload) {
        TurSNSite turSNSite = getSite(snSiteId);
        validatePayload(payload);

        TurSNSiteCustomSort customSort = turSNSiteCustomSortRepository.findByTurSNSiteAndId(turSNSite, customSortId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, CUSTOM_SORT_NOT_FOUND));

        applyPayload(customSort, payload);
        TurSNSiteCustomSort saved = turSNSiteCustomSortRepository.save(customSort);
        return toDto(saved);
    }

    @Operation(summary = "Delete a Semantic Navigation Site Custom Sort")
    @DeleteMapping("/{customSortId}")
    public boolean delete(@PathVariable String snSiteId,
                          @PathVariable String customSortId) {
        TurSNSite turSNSite = getSite(snSiteId);
        TurSNSiteCustomSort customSort = turSNSiteCustomSortRepository.findByTurSNSiteAndId(turSNSite, customSortId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, CUSTOM_SORT_NOT_FOUND));
        turSNSiteCustomSortRepository.delete(customSort);
        return true;
    }

    private void validatePayload(TurSNSiteCustomSortDto payload) {
        if (payload == null) {
            throw new ResponseStatusException(BAD_REQUEST, "Invalid custom sort payload.");
        }
        if (payload.getName() == null || payload.getName().isBlank()) {
            throw new ResponseStatusException(BAD_REQUEST, "Custom sort name is required.");
        }
        if (payload.getItems() == null || payload.getItems().isEmpty()) {
            throw new ResponseStatusException(BAD_REQUEST, "At least one sort level is required.");
        }
    }

    private void applyPayload(TurSNSiteCustomSort target, TurSNSiteCustomSortDto payload) {
        target.setName(payload.getName());
        target.setDescription(payload.getDescription());

        Set<TurSNSiteCustomSortItem> items = Optional.ofNullable(payload.getItems())
                .orElse(List.of())
                .stream()
                .map(this::toItemEntity)
                .collect(Collectors.toCollection(HashSet::new));
        target.setItems(items);
    }

    private TurSNSiteCustomSortItem toItemEntity(TurSNSiteCustomSortItemDto dto) {
        TurSNSiteCustomSortItem item = TurSNSiteCustomSortItem.builder().build();
        item.setId(dto.getId());
        item.setFieldName(dto.getFieldName());
        item.setSortOrder(Optional.ofNullable(dto.getSortOrder())
                .orElse(TurSNSiteCustomSortOrderEnum.ASC));
        item.setPosition(Optional.ofNullable(dto.getPosition()).orElse(0));
        return item;
    }

    private TurSNSiteCustomSortDto toDto(TurSNSiteCustomSort customSort) {
        List<TurSNSiteCustomSortItemDto> items = Optional.ofNullable(customSort.getItems())
                .orElse(Set.of())
                .stream()
                .sorted(Comparator.comparing(TurSNSiteCustomSortItem::getPosition,
                        Comparator.nullsLast(Integer::compareTo)))
                .map(item -> {
                    TurSNSiteCustomSortItemDto dto = new TurSNSiteCustomSortItemDto();
                    dto.setId(item.getId());
                    dto.setFieldName(item.getFieldName());
                    dto.setSortOrder(item.getSortOrder());
                    dto.setPosition(item.getPosition());
                    return dto;
                })
                .toList();

        TurSNSiteCustomSortDto dto = new TurSNSiteCustomSortDto();
        dto.setId(customSort.getId());
        dto.setName(customSort.getName());
        dto.setDescription(customSort.getDescription());
        dto.setItems(items);
        return dto;
    }

    private TurSNSite getSite(String snSiteId) {
        return turSNSiteRepository.findById(snSiteId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "SN Site not found."));
    }

    @Setter
    @Getter
    @NoArgsConstructor
    public static class TurSNSiteCustomSortItemDto {
        private String id;
        private String fieldName;
        private TurSNSiteCustomSortOrderEnum sortOrder;
        private Integer position;
    }

    @Setter
    @Getter
    @NoArgsConstructor
    public static class TurSNSiteCustomSortDto {
        private String id;
        private String name;
        private String description;
        private List<TurSNSiteCustomSortItemDto> items;
    }

    @Setter
    @Getter
    @NoArgsConstructor
    public static class TurSNSiteCustomSortFieldOptionDto {
        private String id;
        private String name;
        private String type;
    }
}
