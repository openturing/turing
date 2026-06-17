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
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.apache.commons.lang3.LocaleUtils;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.viglet.turing.client.sn.job.TurSNJobAttributeSpec;
import com.viglet.turing.persistence.model.sn.TurSNSite;
import com.viglet.turing.persistence.model.sn.field.TurSNSiteFieldExt;
import com.viglet.turing.persistence.repository.sn.TurSNSiteRepository;
import com.viglet.turing.persistence.repository.sn.field.TurSNSiteFieldExtRepository;
import com.viglet.turing.persistence.repository.sn.locale.TurSNSiteLocaleRepository;

import lombok.RequiredArgsConstructor;

/**
 * Computes the {@link TurSNManifestDiff} between a {@link TurSNSiteManifest} and
 * the live site — the diff half of schema-as-code (T386 / §XX.6).
 *
 * <p>Classification mirrors the Liquibase model the team already trusts:</p>
 * <ul>
 *   <li><b>Additive</b> — a field or locale the manifest declares but the live
 *       site lacks. Converges silently.</li>
 *   <li><b>Breaking</b> — a {@code type} or {@code multiValued} change on a field
 *       that already exists. These alter the search-engine schema and invalidate
 *       indexed values, so they must be declared via a {@link TurSNFieldMigration}
 *       rather than inferred.</li>
 *   <li><b>Unchanged</b> — a field already matching the manifest (no-op).</li>
 * </ul>
 *
 * <p>Facet/mandatory/description metadata is intentionally <em>not</em> treated
 * as a breaking change: it does not require reindexing and the indexing-time
 * auto-create path never rewrote it either.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Component
@RequiredArgsConstructor
public class TurSNManifestPlanner {

    private final TurSNSiteRepository turSNSiteRepository;
    private final TurSNSiteFieldExtRepository turSNSiteFieldExtRepository;
    private final TurSNSiteLocaleRepository turSNSiteLocaleRepository;

    /**
     * Diffs the manifest against the current state of the site it names.
     * Read-only: never mutates.
     */
    public TurSNManifestDiff diff(TurSNSiteManifest manifest) {
        Optional<TurSNSite> existing = StringUtils.hasText(manifest.name())
                ? turSNSiteRepository.findByNameIgnoreCase(manifest.name())
                : Optional.empty();

        if (existing.isEmpty()) {
            // Brand-new site: every declared field/locale is additive, nothing can break.
            return new TurSNManifestDiff(manifest.schemaVersion(), false,
                    fieldNames(manifest.fields()), List.of(), List.of(),
                    localeCodes(manifest.locales()));
        }

        TurSNSite site = existing.get();
        return diff(manifest, site);
    }

    /**
     * Diffs the manifest against an already-resolved site. Used by the
     * provisioning path, which has already loaded the (uncached) site.
     */
    public TurSNManifestDiff diff(TurSNSiteManifest manifest, TurSNSite site) {
        Map<String, TurSNSiteFieldExt> liveFields = liveFieldsByName(site);
        Set<String> migratedFields = declaredMigrationFields(manifest);

        List<String> fieldsToAdd = new ArrayList<>();
        List<TurSNManifestFieldChange> breaking = new ArrayList<>();
        List<String> unchanged = new ArrayList<>();

        for (TurSNJobAttributeSpec spec : safe(manifest.fields())) {
            if (spec == null || !StringUtils.hasText(spec.getName())) {
                continue;
            }
            TurSNSiteFieldExt live = liveFields.get(spec.getName());
            if (live == null) {
                fieldsToAdd.add(spec.getName());
                continue;
            }
            TurSNManifestFieldChange change = breakingChange(spec, live, migratedFields);
            if (change != null) {
                breaking.add(change);
            } else {
                unchanged.add(spec.getName());
            }
        }

        return new TurSNManifestDiff(manifest.schemaVersion(), true, fieldsToAdd, breaking,
                unchanged, localesToAdd(site, manifest.locales()));
    }

    private TurSNManifestFieldChange breakingChange(TurSNJobAttributeSpec spec,
            TurSNSiteFieldExt live, Set<String> migratedFields) {
        boolean declared = migratedFields.contains(spec.getName());
        if (spec.getType() != null && live.getType() != spec.getType()) {
            return new TurSNManifestFieldChange(spec.getName(), "type",
                    String.valueOf(live.getType()), String.valueOf(spec.getType()), declared);
        }
        boolean liveMulti = live.getMultiValued() == 1;
        if (liveMulti != spec.isMultiValued()) {
            return new TurSNManifestFieldChange(spec.getName(), "multiValued",
                    String.valueOf(liveMulti), String.valueOf(spec.isMultiValued()), declared);
        }
        return null;
    }

    private Map<String, TurSNSiteFieldExt> liveFieldsByName(TurSNSite site) {
        Map<String, TurSNSiteFieldExt> byName = new LinkedHashMap<>();
        for (TurSNSiteFieldExt ext : turSNSiteFieldExtRepository.findByTurSNSite(Sort.unsorted(), site)) {
            byName.putIfAbsent(ext.getName(), ext);
        }
        return byName;
    }

    private List<String> localesToAdd(TurSNSite site, List<String> codes) {
        List<String> toAdd = new ArrayList<>();
        for (String code : safe(codes)) {
            if (!StringUtils.hasText(code)) {
                continue;
            }
            Locale locale = LocaleUtils.toLocale(code.trim());
            if (!turSNSiteLocaleRepository.existsByTurSNSiteAndLanguage(site, locale)) {
                toAdd.add(locale.toString());
            }
        }
        return toAdd;
    }

    private static Set<String> declaredMigrationFields(TurSNSiteManifest manifest) {
        Set<String> names = new HashSet<>();
        if (manifest.migrations() != null) {
            for (TurSNFieldMigration migration : manifest.migrations()) {
                if (migration != null && StringUtils.hasText(migration.field())) {
                    names.add(migration.field());
                }
            }
        }
        return names;
    }

    private static List<String> fieldNames(List<TurSNJobAttributeSpec> fields) {
        return safe(fields).stream()
                .filter(spec -> spec != null && StringUtils.hasText(spec.getName()))
                .map(TurSNJobAttributeSpec::getName)
                .toList();
    }

    private static List<String> localeCodes(List<String> codes) {
        return safe(codes).stream()
                .filter(StringUtils::hasText)
                .map(code -> LocaleUtils.toLocale(code.trim()).toString())
                .toList();
    }

    private static <T> List<T> safe(List<T> list) {
        return list == null ? List.of() : list;
    }
}
