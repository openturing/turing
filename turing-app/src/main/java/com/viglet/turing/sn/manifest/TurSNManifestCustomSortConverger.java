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

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.viglet.core.manifest.VigletFieldManifest;
import com.viglet.core.manifest.VigletFieldSpec;
import com.viglet.turing.persistence.model.sn.TurSNSite;
import com.viglet.turing.persistence.model.sn.sort.TurSNSiteCustomSort;
import com.viglet.turing.persistence.model.sn.sort.TurSNSiteCustomSortItem;
import com.viglet.turing.persistence.model.sn.sort.TurSNSiteCustomSortMissingEnum;
import com.viglet.turing.persistence.model.sn.sort.TurSNSiteCustomSortOrderEnum;
import com.viglet.turing.persistence.repository.sn.field.TurSNSiteFieldExtRepository;
import com.viglet.turing.persistence.repository.sn.sort.TurSNSiteCustomSortRepository;

import lombok.RequiredArgsConstructor;

/**
 * Converges the custom sorts an SN manifest declares onto the site (T1104), the
 * same way {@link TurSNManifestCustomFacetConverger} converges custom facets.
 *
 * <p>A declared sort is matched to the live one by name. Absent, it is created;
 * present with another description or other levels, those are replaced; equal,
 * it is left alone, so re-posting a manifest is a no-op. A sort the manifest does
 * not declare is never touched.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.5
 */
@Component
@RequiredArgsConstructor
public class TurSNManifestCustomSortConverger {

    private static final int MAX_NAME_LENGTH = 100;
    private static final int MAX_DESCRIPTION_LENGTH = 500;
    /** The names a search's {@code sort} answers before it looks for a custom sort. */
    private static final Set<String> RESERVED_NAMES = Set.of("relevance", "newest", "oldest");

    private final TurSNSiteFieldExtRepository turSNSiteFieldExtRepository;
    private final TurSNSiteCustomSortRepository turSNSiteCustomSortRepository;

    /**
     * Refuses a declaration that cannot be stored or would never be reached: no
     * name, a reserved or duplicate name, no levels, or a level on a field neither
     * the manifest nor the site declares.
     *
     * @param site the live site, or {@code null} when the manifest creates it
     */
    public void validate(TurSNSite site, VigletFieldManifest manifest,
            List<TurSNManifestCustomSort> customSorts) {
        Set<String> names = new HashSet<>();
        for (TurSNManifestCustomSort sort : customSorts) {
            validateHead(sort);
            if (!names.add(sort.name())) {
                throw new IllegalArgumentException("Custom sort '" + sort.name() + "' is declared twice");
            }
            for (TurSNManifestCustomSortItem item : sort.items()) {
                if (item == null || !StringUtils.hasText(item.field())) {
                    throw new IllegalArgumentException("Every level of custom sort '" + sort.name()
                            + "' must declare a 'field'");
                }
                if (!declared(site, manifest, item.field())) {
                    throw new IllegalArgumentException("Custom sort '" + sort.name() + "' names field '"
                            + item.field() + "', which neither the manifest nor the site declares");
                }
            }
        }
    }

    private static void validateHead(TurSNManifestCustomSort sort) {
        if (sort == null || !StringUtils.hasText(sort.name())) {
            throw new IllegalArgumentException("Every manifest custom sort must declare a 'name'");
        }
        if (sort.name().length() > MAX_NAME_LENGTH) {
            throw new IllegalArgumentException("Custom sort name is longer than " + MAX_NAME_LENGTH
                    + " characters: " + sort.name());
        }
        if (RESERVED_NAMES.contains(sort.name().toLowerCase())) {
            throw new IllegalArgumentException("Custom sort name '" + sort.name()
                    + "' is reserved: a search answers it before looking for a custom sort");
        }
        if (sort.description() != null && sort.description().length() > MAX_DESCRIPTION_LENGTH) {
            throw new IllegalArgumentException("Custom sort '" + sort.name() + "' has a description longer than "
                    + MAX_DESCRIPTION_LENGTH + " characters");
        }
        if (sort.items() == null || sort.items().isEmpty()) {
            throw new IllegalArgumentException("Custom sort '" + sort.name() + "' must declare at least one level");
        }
    }

    /** The custom sorts {@link #converge} would create or change. Read-only. */
    public List<String> plan(TurSNSite site, List<TurSNManifestCustomSort> customSorts) {
        List<String> changes = new ArrayList<>();
        for (TurSNManifestCustomSort sort : customSorts) {
            TurSNSiteCustomSort live = site == null ? null
                    : turSNSiteCustomSortRepository.findByTurSNSiteAndName(site, sort.name()).orElse(null);
            if (live == null || !matches(live, sort)) {
                changes.add(sort.name());
            }
        }
        return changes;
    }

    /**
     * Creates or rewrites each declared sort that differs from the live one. The
     * manifest's fields must already exist.
     *
     * @return the names of the sorts created or changed
     */
    public List<String> converge(TurSNSite site, List<TurSNManifestCustomSort> customSorts) {
        List<String> changed = new ArrayList<>();
        for (TurSNManifestCustomSort sort : customSorts) {
            TurSNSiteCustomSort live = turSNSiteCustomSortRepository.findByTurSNSiteAndName(site, sort.name())
                    .orElse(null);
            if (live != null && matches(live, sort)) {
                continue;
            }
            TurSNSiteCustomSort target = live != null ? live
                    : TurSNSiteCustomSort.builder().name(sort.name()).turSNSite(site).build();
            target.setDescription(sort.description());
            target.setItems(new HashSet<>(desiredItems(sort)));
            turSNSiteCustomSortRepository.save(target);
            changed.add(sort.name());
        }
        return changed;
    }

    private static boolean matches(TurSNSiteCustomSort live, TurSNManifestCustomSort sort) {
        if (!Objects.equals(live.getDescription(), sort.description())) {
            return false;
        }
        List<Level> liveLevels = Optional.ofNullable(live.getItems()).orElse(Set.of()).stream()
                .sorted(Comparator.comparing(TurSNSiteCustomSortItem::getPosition,
                        Comparator.nullsLast(Integer::compareTo)))
                .map(Level::of).toList();
        return liveLevels.equals(desiredItems(sort).stream().map(Level::of).toList());
    }

    private static List<TurSNSiteCustomSortItem> desiredItems(TurSNManifestCustomSort sort) {
        List<TurSNSiteCustomSortItem> items = new ArrayList<>();
        for (int i = 0; i < sort.items().size(); i++) {
            TurSNManifestCustomSortItem item = sort.items().get(i);
            items.add(TurSNSiteCustomSortItem.builder()
                    .fieldName(item.field())
                    .sortOrder(Optional.ofNullable(item.order()).orElse(TurSNSiteCustomSortOrderEnum.ASC))
                    .missing(item.missing())
                    .position(i + 1)
                    .build());
        }
        return items;
    }

    private boolean declared(TurSNSite site, VigletFieldManifest manifest, String field) {
        if (manifest.fields() != null) {
            for (VigletFieldSpec spec : manifest.fields()) {
                if (spec != null && field.equals(spec.name())) {
                    return true;
                }
            }
        }
        return site != null && !turSNSiteFieldExtRepository.findByTurSNSiteAndName(site, field).isEmpty();
    }

    /** What makes two levels the same, ignoring their ids. */
    private record Level(String field, TurSNSiteCustomSortOrderEnum order,
            TurSNSiteCustomSortMissingEnum missing, Integer position) {

        static Level of(TurSNSiteCustomSortItem item) {
            return new Level(item.getFieldName(),
                    Optional.ofNullable(item.getSortOrder()).orElse(TurSNSiteCustomSortOrderEnum.ASC),
                    item.getMissing(), item.getPosition());
        }
    }
}
