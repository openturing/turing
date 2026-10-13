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

package com.viglet.turing.sn.manifest;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.viglet.core.manifest.VigletFieldManifest;
import com.viglet.core.manifest.VigletFieldSpec;
import com.viglet.turing.commons.se.field.TurSEFieldType;
import com.viglet.turing.persistence.model.sn.TurSNSite;
import com.viglet.turing.persistence.model.sn.field.TurSNSiteCustomFacet;
import com.viglet.turing.persistence.model.sn.field.TurSNSiteCustomFacetItem;
import com.viglet.turing.persistence.model.sn.field.TurSNSiteCustomFacetOperatorEnum;
import com.viglet.turing.persistence.model.sn.field.TurSNSiteFacetFieldEnum;
import com.viglet.turing.persistence.model.sn.field.TurSNSiteFieldExt;
import com.viglet.turing.persistence.repository.sn.field.TurSNSiteCustomFacetRepository;
import com.viglet.turing.persistence.repository.sn.field.TurSNSiteFieldExtRepository;
import com.viglet.turing.sn.facet.TurSNCustomFacetItems;

import lombok.RequiredArgsConstructor;

/**
 * Converges the custom facets an SN manifest declares onto the site (T1084).
 *
 * <p>A declared facet is matched to the live one by field and name. Absent, it
 * is created; present with other labels or buckets, its labels and buckets are
 * replaced by the declared ones; equal, it is left alone, so re-posting a
 * manifest is a no-op. A facet the manifest does not declare is never touched,
 * the same additive rule the manifest applies to fields.
 *
 * <p>Buckets are built by {@link TurSNCustomFacetItems}, the conversion the
 * console uses, so a date-math bound ({@code NOW-7DAYS}) is validated and stored
 * the same way on both paths.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Component
@RequiredArgsConstructor
public class TurSNManifestCustomFacetConverger {

    private static final int MAX_NAME_LENGTH = 100;

    private final TurSNSiteFieldExtRepository turSNSiteFieldExtRepository;
    private final TurSNSiteCustomFacetRepository turSNSiteCustomFacetRepository;

    /**
     * Refuses a declaration that cannot be stored: no field or name, a duplicate,
     * an item without a label, or a bound the field's type cannot read.
     *
     * @param site the live site, or {@code null} when the manifest creates it
     */
    public void validate(TurSNSite site, VigletFieldManifest manifest,
            List<TurSNManifestCustomFacet> customFacets) {
        Set<String> keys = new HashSet<>();
        for (TurSNManifestCustomFacet facet : customFacets) {
            if (facet == null || !StringUtils.hasText(facet.field()) || !StringUtils.hasText(facet.name())) {
                throw new IllegalArgumentException("Every manifest custom facet must declare 'field' and 'name'");
            }
            if (facet.name().length() > MAX_NAME_LENGTH) {
                throw new IllegalArgumentException("Custom facet name is longer than " + MAX_NAME_LENGTH
                        + " characters: " + facet.name());
            }
            if (!keys.add(key(facet))) {
                throw new IllegalArgumentException("Custom facet '" + facet.name() + "' is declared twice on field '"
                        + facet.field() + "'");
            }
            TurSEFieldType type = fieldType(site, manifest, facet);
            desiredItems(facet, type);
        }
    }

    /** The custom facets {@link #converge} would create or change. Read-only. */
    public List<String> plan(TurSNSite site, VigletFieldManifest manifest,
            List<TurSNManifestCustomFacet> customFacets) {
        List<String> changes = new ArrayList<>();
        for (TurSNManifestCustomFacet facet : customFacets) {
            TurSNSiteFieldExt fieldExt = site == null ? null : liveField(site, facet.field());
            TurSNSiteCustomFacet live = fieldExt == null ? null : liveFacet(fieldExt, facet.name());
            if (live == null || !matches(live, facet, fieldType(site, manifest, facet))) {
                changes.add(facet.name());
            }
        }
        return changes;
    }

    /**
     * Creates or rewrites each declared facet that differs from the live one.
     * The manifest's fields must already exist.
     *
     * @return the names of the facets created or changed
     */
    public List<String> converge(TurSNSite site, List<TurSNManifestCustomFacet> customFacets) {
        List<String> changed = new ArrayList<>();
        for (TurSNManifestCustomFacet facet : customFacets) {
            TurSNSiteFieldExt fieldExt = liveField(site, facet.field());
            if (fieldExt == null) {
                throw new IllegalArgumentException("Custom facet '" + facet.name() + "' names field '"
                        + facet.field() + "', which the site does not have");
            }
            TurSNSiteCustomFacet live = liveFacet(fieldExt, facet.name());
            if (live != null && matches(live, facet, fieldExt.getType())) {
                continue;
            }
            TurSNSiteCustomFacet target = live != null ? live : TurSNSiteCustomFacet.builder()
                    .name(facet.name())
                    .facetType(TurSNSiteFacetFieldEnum.DEFAULT)
                    .facetItemType(TurSNSiteFacetFieldEnum.DEFAULT)
                    .turSNSiteFieldExt(fieldExt)
                    .facetPosition(nextFacetPosition(site))
                    .build();
            target.setDefaultLabel(facet.defaultLabel());
            target.setLabel(new HashMap<>(Optional.ofNullable(facet.label()).orElse(Map.of())));
            if (facet.position() != null) {
                target.setFacetPosition(facet.position());
            }
            target.setItems(new HashSet<>(desiredItems(facet, fieldExt.getType())));
            turSNSiteCustomFacetRepository.save(target);
            changed.add(facet.name());
        }
        return changed;
    }

    private boolean matches(TurSNSiteCustomFacet live, TurSNManifestCustomFacet facet, TurSEFieldType type) {
        if (!Objects.equals(live.getDefaultLabel(), facet.defaultLabel())
                || !Objects.equals(orEmpty(live.getLabel()), orEmpty(facet.label()))
                || (facet.position() != null && !facet.position().equals(live.getFacetPosition()))) {
            return false;
        }
        Set<ItemSignature> liveItems = Optional.ofNullable(live.getItems()).orElse(Set.of()).stream()
                .map(ItemSignature::of).collect(Collectors.toSet());
        Set<ItemSignature> declared = desiredItems(facet, type).stream()
                .map(ItemSignature::of).collect(Collectors.toSet());
        return liveItems.equals(declared);
    }

    private static List<TurSNSiteCustomFacetItem> desiredItems(TurSNManifestCustomFacet facet,
            TurSEFieldType type) {
        List<TurSNManifestCustomFacetItem> items = Optional.ofNullable(facet.items()).orElse(List.of());
        List<TurSNSiteCustomFacetItem> entities = new ArrayList<>();
        for (int i = 0; i < items.size(); i++) {
            TurSNManifestCustomFacetItem item = items.get(i);
            if (item == null || !StringUtils.hasText(item.label())) {
                throw new IllegalArgumentException("Every item of custom facet '" + facet.name()
                        + "' must declare a 'label'");
            }
            Integer position = item.position() != null ? item.position() : i + 1;
            try {
                entities.add(TurSNCustomFacetItems.toEntity(null, item.label(), item.labels(), position,
                        item.operator(), item.rangeStart(), item.rangeEnd(), item.rangeStartDate(),
                        item.rangeEndDate(), type));
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("Custom facet '" + facet.name() + "', item '"
                        + item.label() + "': " + e.getMessage(), e);
            }
        }
        return entities;
    }

    private TurSEFieldType fieldType(TurSNSite site, VigletFieldManifest manifest,
            TurSNManifestCustomFacet facet) {
        if (manifest.fields() != null) {
            for (VigletFieldSpec spec : manifest.fields()) {
                if (spec != null && facet.field().equals(spec.name())) {
                    return TurSNManifestMapper.toSeType(spec.type());
                }
            }
        }
        TurSNSiteFieldExt live = site == null ? null : liveField(site, facet.field());
        if (live == null) {
            throw new IllegalArgumentException("Custom facet '" + facet.name() + "' names field '"
                    + facet.field() + "', which neither the manifest nor the site declares");
        }
        return live.getType();
    }

    private TurSNSiteFieldExt liveField(TurSNSite site, String name) {
        return turSNSiteFieldExtRepository.findByTurSNSiteAndName(site, name).stream()
                .findFirst().orElse(null);
    }

    private TurSNSiteCustomFacet liveFacet(TurSNSiteFieldExt fieldExt, String name) {
        return turSNSiteCustomFacetRepository.findByFieldExtWithDetails(fieldExt).stream()
                .filter(facet -> name.equals(facet.getName()))
                .findFirst().orElse(null);
    }

    private Integer nextFacetPosition(TurSNSite site) {
        List<TurSNSiteFieldExt> fields = turSNSiteFieldExtRepository.findByTurSNSite(Sort.unsorted(), site);
        if (fields.isEmpty()) {
            return 1;
        }
        return turSNSiteCustomFacetRepository.findByFieldExtsWithDetails(fields).stream()
                .map(TurSNSiteCustomFacet::getFacetPosition)
                .filter(Objects::nonNull)
                .max(Integer::compareTo)
                .map(max -> max + 1)
                .orElse(1);
    }

    private static String key(TurSNManifestCustomFacet facet) {
        return facet.field() + "\u0000" + facet.name();
    }

    private static Map<String, String> orEmpty(Map<String, String> map) {
        return map == null ? Map.of() : map;
    }

    /** What makes two buckets the same, ignoring their ids. */
    private record ItemSignature(String label, Map<String, String> labels, Integer position,
            TurSNSiteCustomFacetOperatorEnum operator, BigDecimal rangeStart, BigDecimal rangeEnd,
            Instant rangeStartDate, Instant rangeEndDate, String rangeStartExpression,
            String rangeEndExpression) {

        static ItemSignature of(TurSNSiteCustomFacetItem item) {
            return new ItemSignature(item.getLabel(), Map.copyOf(orEmpty(item.getLabels())), item.getPosition(),
                    Optional.ofNullable(item.getOperator())
                            .orElse(TurSNSiteCustomFacetOperatorEnum.BETWEEN_INCLUSIVE),
                    normalize(item.getRangeStart()), normalize(item.getRangeEnd()), item.getRangeStartDate(),
                    item.getRangeEndDate(), item.getRangeStartExpression(), item.getRangeEndExpression());
        }

        private static BigDecimal normalize(BigDecimal value) {
            return value == null ? null : value.stripTrailingZeros();
        }
    }
}
