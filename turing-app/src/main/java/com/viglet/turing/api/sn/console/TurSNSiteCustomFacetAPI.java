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

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
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

import com.viglet.turing.commons.se.field.TurSEFieldType;
import com.viglet.turing.persistence.model.sn.TurSNSite;
import com.viglet.turing.persistence.model.sn.field.TurSNSiteCustomFacet;
import com.viglet.turing.persistence.model.sn.field.TurSNSiteCustomFacetItem;
import com.viglet.turing.persistence.model.sn.field.TurSNSiteFacetFieldEnum;
import com.viglet.turing.persistence.model.sn.field.TurSNSiteFieldExt;
import com.viglet.turing.persistence.repository.sn.TurSNSiteRepository;
import com.viglet.turing.persistence.repository.sn.field.TurSNSiteFieldExtRepository;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@RestController
@RequestMapping("/api/sn/{snSiteId}/custom-facet")
@Tag(name = "Semantic Navigation Custom Facet", description = "Semantic Navigation Custom Facet API")
@Transactional
public class TurSNSiteCustomFacetAPI {
    private static final String CUSTOM_FACET_NOT_FOUND = "Custom facet not found.";

    private final TurSNSiteRepository turSNSiteRepository;
    private final TurSNSiteFieldExtRepository turSNSiteFieldExtRepository;

    public TurSNSiteCustomFacetAPI(TurSNSiteRepository turSNSiteRepository,
            TurSNSiteFieldExtRepository turSNSiteFieldExtRepository) {
        this.turSNSiteRepository = turSNSiteRepository;
        this.turSNSiteFieldExtRepository = turSNSiteFieldExtRepository;
    }

    @Operation(summary = "Semantic Navigation Site Custom Facet List")
    @GetMapping
    public List<TurSNSiteCustomFacetDto> list(@PathVariable String snSiteId) {
        TurSNSite turSNSite = getSite(snSiteId);
        return getAllCustomFacets(turSNSite).stream()
                .map(this::toDto)
                .toList();
    }

    @Operation(summary = "Show a Semantic Navigation Site Custom Facet")
    @GetMapping("/{customFacetId}")
    public TurSNSiteCustomFacetDto get(@PathVariable String snSiteId,
            @PathVariable String customFacetId) {
        TurSNSite turSNSite = getSite(snSiteId);
        TurSNSiteCustomFacet turSNSiteCustomFacet = findCustomFacetById(turSNSite, customFacetId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, CUSTOM_FACET_NOT_FOUND));
        return toDto(turSNSiteCustomFacet);
    }

    @Operation(summary = "Create a Semantic Navigation Site Custom Facet")
    @PostMapping
    public TurSNSiteCustomFacetDto create(@PathVariable String snSiteId,
            @RequestBody TurSNSiteCustomFacetDto payload) {
        TurSNSite turSNSite = getSite(snSiteId);
        TurSNSiteFieldExt targetField = getTargetField(turSNSite, payload.getFieldExtId());

        TurSNSiteCustomFacet customFacet = TurSNSiteCustomFacet.builder().build();
        customFacet.setTurSNSiteFieldExt(targetField);
        applyPayload(customFacet, payload, turSNSite, true);

        Set<TurSNSiteCustomFacet> customFacets = new HashSet<>(
                Optional.ofNullable(targetField.getCustomFacets()).orElse(Set.of()));
        customFacets.add(customFacet);
        targetField.setCustomFacets(customFacets);

        TurSNSiteFieldExt savedField = turSNSiteFieldExtRepository.save(targetField);
        TurSNSiteCustomFacet savedFacet = Optional.ofNullable(savedField.getCustomFacets())
                .orElse(Set.of())
                .stream()
                .filter(facet -> facet.getName().equals(customFacet.getName()))
                .max(Comparator.comparing(this::safeFacetPosition)
                        .thenComparing(TurSNSiteCustomFacet::getId, Comparator.nullsLast(String::compareTo)))
                .orElse(customFacet);
        return toDto(savedFacet);
    }

    @Operation(summary = "Update a Semantic Navigation Site Custom Facet")
    @PutMapping("/{customFacetId}")
    public TurSNSiteCustomFacetDto update(@PathVariable String snSiteId,
            @PathVariable String customFacetId,
            @RequestBody TurSNSiteCustomFacetDto payload) {
        TurSNSite turSNSite = getSite(snSiteId);
        CustomFacetFieldBinding currentBinding = findCustomFacetBindingById(turSNSite, customFacetId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, CUSTOM_FACET_NOT_FOUND));

        TurSNSiteFieldExt sourceField = currentBinding.getFieldExt();
        TurSNSiteCustomFacet customFacet = currentBinding.getCustomFacet();

        TurSNSiteFieldExt targetField = Optional.ofNullable(payload.getFieldExtId())
                .map(fieldId -> getTargetField(turSNSite, fieldId))
                .orElse(sourceField);

        customFacet.setTurSNSiteFieldExt(targetField);
        applyPayload(customFacet, payload, turSNSite, false);

        if (!sourceField.getId().equals(targetField.getId())) {
            Set<TurSNSiteCustomFacet> targetFacets = new HashSet<>(
                    Optional.ofNullable(targetField.getCustomFacets()).orElse(Set.of()));
            customFacet.setTurSNSiteFieldExt(targetField);
            targetFacets.add(customFacet);

            targetField.setCustomFacets(targetFacets);

            TurSNSiteFieldExt savedTargetField = turSNSiteFieldExtRepository.save(targetField);
            return Optional.ofNullable(savedTargetField.getCustomFacets())
                    .orElse(Set.of())
                    .stream()
                    .filter(item -> customFacetId.equals(item.getId()))
                    .findFirst()
                    .map(this::toDto)
                    .orElseGet(() -> toDto(customFacet));
        }

        Set<TurSNSiteCustomFacet> sourceFacets = new HashSet<>(
                Optional.ofNullable(sourceField.getCustomFacets()).orElse(Set.of()));
        sourceField.setCustomFacets(sourceFacets);
        TurSNSiteFieldExt savedSourceField = turSNSiteFieldExtRepository.save(sourceField);

        return Optional.ofNullable(savedSourceField.getCustomFacets())
                .orElse(Set.of())
                .stream()
                .filter(item -> customFacetId.equals(item.getId()))
                .findFirst()
                .map(this::toDto)
                .orElseGet(() -> toDto(customFacet));
    }

    @Operation(summary = "Delete a Semantic Navigation Site Custom Facet")
    @DeleteMapping("/{customFacetId}")
    public boolean delete(@PathVariable String snSiteId,
            @PathVariable String customFacetId) {
        TurSNSite turSNSite = getSite(snSiteId);
        CustomFacetFieldBinding binding = findCustomFacetBindingById(turSNSite, customFacetId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, CUSTOM_FACET_NOT_FOUND));

        TurSNSiteFieldExt fieldExt = binding.getFieldExt();
        fieldExt.setCustomFacets(Optional.ofNullable(fieldExt.getCustomFacets())
                .orElse(Set.of())
                .stream()
                .filter(item -> !customFacetId.equals(item.getId()))
                .collect(Collectors.toSet()));

        turSNSiteFieldExtRepository.save(fieldExt);
        return true;
    }

    private void applyPayload(TurSNSiteCustomFacet target,
            TurSNSiteCustomFacetDto payload,
            TurSNSite turSNSite,
            boolean isCreate) {
        if (payload == null) {
            throw new ResponseStatusException(BAD_REQUEST, "Invalid custom facet payload.");
        }

        if (payload.getName() == null || payload.getName().isBlank()) {
            throw new ResponseStatusException(BAD_REQUEST, "Custom facet name is required.");
        }

        target.setName(payload.getName());
        target.setDefaultLabel(payload.getDefaultLabel());
        target.setLabel(Optional.ofNullable(payload.getLabel()).orElse(Map.of()));
        target.setFacetType(Optional.ofNullable(payload.getFacetType())
                .orElse(TurSNSiteFacetFieldEnum.DEFAULT));
        target.setFacetItemType(Optional.ofNullable(payload.getFacetItemType())
                .orElse(TurSNSiteFacetFieldEnum.DEFAULT));

        Integer facetPosition = payload.getFacetPosition();
        if (facetPosition == null || facetPosition <= 0) {
            facetPosition = isCreate ? nextFacetPosition(turSNSite) : target.getFacetPosition();
        }
        target.setFacetPosition(facetPosition);

        TurSEFieldType fieldType = Optional.ofNullable(target.getTurSNSiteFieldExt())
                .map(TurSNSiteFieldExt::getType)
                .orElse(TurSEFieldType.INT);

        Set<TurSNSiteCustomFacetItem> items = Optional.ofNullable(payload.getItems())
                .orElse(List.of())
                .stream()
                .sorted(Comparator.comparing(this::safeItemPositionDto)
                        .thenComparing(TurSNSiteCustomFacetItemDto::getLabel, Comparator.nullsLast(String::compareTo)))
                .map(itemDto -> toEntity(itemDto, fieldType))
                .collect(Collectors.toCollection(HashSet::new));
        target.setItems(items);
    }

    private TurSNSiteCustomFacetItem toEntity(TurSNSiteCustomFacetItemDto dto, TurSEFieldType fieldType) {
        TurSNSiteCustomFacetItem item = TurSNSiteCustomFacetItem.builder().build();
        item.setId(dto.getId());
        item.setLabel(dto.getLabel());
        item.setPosition(safeItemPositionDto(dto));

        if (TurSEFieldType.DATE.equals(fieldType)) {
            item.setRangeStart(null);
            item.setRangeEnd(null);
            item.setRangeStartDate(parseIsoDate(dto.getRangeStartDate()));
            item.setRangeEndDate(parseIsoDate(dto.getRangeEndDate()));
        } else {
            item.setRangeStart(dto.getRangeStart());
            item.setRangeEnd(dto.getRangeEnd());
            item.setRangeStartDate(null);
            item.setRangeEndDate(null);
        }
        return item;
    }

    private TurSNSiteCustomFacetDto toDto(TurSNSiteCustomFacet customFacet) {
        TurSNSiteFieldExt fieldExt = customFacet.getTurSNSiteFieldExt();
        Map<String, String> labels = new HashMap<>(Optional.ofNullable(customFacet.getLabel()).orElse(Map.of()));
        List<TurSNSiteCustomFacetItemDto> items = Optional.ofNullable(customFacet.getItems())
                .orElse(Set.of())
                .stream()
                .sorted(Comparator.comparing(this::safeItemPositionEntity)
                        .thenComparing(TurSNSiteCustomFacetItem::getLabel, Comparator.nullsLast(String::compareTo)))
                .map(item -> {
                    TurSNSiteCustomFacetItemDto dto = new TurSNSiteCustomFacetItemDto();
                    dto.setId(item.getId());
                    dto.setLabel(item.getLabel());
                    dto.setPosition(item.getPosition());
                    dto.setRangeStart(item.getRangeStart());
                    dto.setRangeEnd(item.getRangeEnd());
                    dto.setRangeStartDate(formatIsoDate(item.getRangeStartDate()));
                    dto.setRangeEndDate(formatIsoDate(item.getRangeEndDate()));
                    return dto;
                })
                .toList();

        TurSNSiteCustomFacetDto dto = new TurSNSiteCustomFacetDto();
        dto.setId(customFacet.getId());
        dto.setName(customFacet.getName());
        dto.setDefaultLabel(customFacet.getDefaultLabel());
        dto.setLabel(labels);
        dto.setFacetPosition(customFacet.getFacetPosition());
        dto.setFacetType(customFacet.getFacetType());
        dto.setFacetItemType(customFacet.getFacetItemType());
        dto.setItems(items);
        dto.setFieldExtId(fieldExt != null ? fieldExt.getId() : null);
        dto.setFieldExtName(fieldExt != null ? fieldExt.getName() : null);
        dto.setFieldExtType(fieldExt != null && fieldExt.getType() != null
                ? fieldExt.getType().toString()
                : null);
        return dto;
    }

    private Instant parseIsoDate(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        try {
            return Instant.parse(value);
        } catch (Exception ignored) {
            // Fallback to ISO_OFFSET_DATE_TIME below.
        }

        try {
            return OffsetDateTime.parse(value).toInstant();
        } catch (Exception ignored) {
            // Fallback to ISO_LOCAL_DATE below.
        }

        try {
            return LocalDate.parse(value).atStartOfDay(ZoneOffset.UTC).toInstant();
        } catch (Exception ignored) {
            // Parsed in all accepted ISO formats and failed.
        }

        throw new ResponseStatusException(BAD_REQUEST, "Invalid ISO date value: " + value);
    }

    private String formatIsoDate(Instant value) {
        return Optional.ofNullable(value)
                .map(Instant::toString)
                .orElse(null);
    }

    private List<TurSNSiteCustomFacet> getAllCustomFacets(TurSNSite turSNSite) {
        return getSiteFields(turSNSite).stream()
                .flatMap(fieldExt -> Optional.ofNullable(fieldExt.getCustomFacets())
                        .orElse(Set.of())
                        .stream())
                .sorted(Comparator.comparing(this::safeFacetPosition)
                        .thenComparing(TurSNSiteCustomFacet::getName, Comparator.nullsLast(String::compareTo)))
                .toList();
    }

    private Optional<TurSNSiteCustomFacet> findCustomFacetById(TurSNSite turSNSite, String customFacetId) {
        return getAllCustomFacets(turSNSite).stream()
                .filter(customFacet -> customFacetId.equals(customFacet.getId()))
                .findFirst();
    }

    private Optional<CustomFacetFieldBinding> findCustomFacetBindingById(TurSNSite turSNSite,
            String customFacetId) {
        for (TurSNSiteFieldExt fieldExt : getSiteFields(turSNSite)) {
            Optional<TurSNSiteCustomFacet> customFacet = Optional.ofNullable(fieldExt.getCustomFacets())
                    .orElse(Set.of())
                    .stream()
                    .filter(item -> customFacetId.equals(item.getId()))
                    .findFirst();
            if (customFacet.isPresent()) {
                return Optional.of(CustomFacetFieldBinding.builder()
                        .fieldExt(fieldExt)
                        .customFacet(customFacet.get())
                        .build());
            }
        }
        return Optional.empty();
    }

    private TurSNSiteFieldExt getTargetField(TurSNSite turSNSite, String fieldExtId) {
        if (fieldExtId == null || fieldExtId.isBlank()) {
            throw new ResponseStatusException(BAD_REQUEST, "fieldExtId is required.");
        }

        return getSiteFields(turSNSite).stream()
                .filter(field -> fieldExtId.equals(field.getId()))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(BAD_REQUEST, "Field not found for this site."));
    }

    private TurSNSite getSite(String snSiteId) {
        return turSNSiteRepository.findById(snSiteId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "SN Site not found."));
    }

    private List<TurSNSiteFieldExt> getSiteFields(TurSNSite turSNSite) {
        return turSNSiteFieldExtRepository.findByTurSNSite(Sort.by(Sort.Order.asc("name")), turSNSite);
    }

    private Integer nextFacetPosition(TurSNSite turSNSite) {
        return getAllCustomFacets(turSNSite).stream()
                .map(this::safeFacetPosition)
                .max(Integer::compareTo)
                .map(maxPosition -> maxPosition + 1)
                .orElse(1);
    }

    private Integer safeFacetPosition(TurSNSiteCustomFacet customFacet) {
        return Optional.ofNullable(customFacet.getFacetPosition()).orElse(Integer.MAX_VALUE);
    }

    private Integer safeItemPositionEntity(TurSNSiteCustomFacetItem item) {
        return Optional.ofNullable(item.getPosition()).orElse(Integer.MAX_VALUE);
    }

    private Integer safeItemPositionDto(TurSNSiteCustomFacetItemDto item) {
        return Optional.ofNullable(item.getPosition()).orElse(Integer.MAX_VALUE);
    }

    @Setter
    @Getter
    @Builder
    private static class CustomFacetFieldBinding {
        private TurSNSiteFieldExt fieldExt;
        private TurSNSiteCustomFacet customFacet;
    }

    @Setter
    @Getter
    @NoArgsConstructor
    public static class TurSNSiteCustomFacetItemDto {
        private String id;
        private String label;
        private Integer position;
        private BigDecimal rangeStart;
        private BigDecimal rangeEnd;
        private String rangeStartDate;
        private String rangeEndDate;
    }

    @Setter
    @Getter
    @NoArgsConstructor
    public static class TurSNSiteCustomFacetDto {
        private String id;
        private String name;
        private String defaultLabel;
        private Map<String, String> label;
        private Integer facetPosition;
        private TurSNSiteFacetFieldEnum facetType;
        private TurSNSiteFacetFieldEnum facetItemType;
        private List<TurSNSiteCustomFacetItemDto> items;
        private String fieldExtId;
        private String fieldExtName;
        private String fieldExtType;
    }
}
