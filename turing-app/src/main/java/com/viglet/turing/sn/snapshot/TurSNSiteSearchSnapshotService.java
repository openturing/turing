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
package com.viglet.turing.sn.snapshot;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import com.viglet.turing.observability.TurMeterNames;
import com.viglet.turing.observability.TurSearchPipelineObservation;

import com.viglet.turing.persistence.model.sn.TurSNSite;
import com.viglet.turing.persistence.model.sn.field.TurSNSiteCustomFacet;
import com.viglet.turing.persistence.model.sn.field.TurSNSiteFieldExt;
import com.viglet.turing.persistence.model.sn.field.TurSNSiteFieldExtFacet;
import com.viglet.turing.persistence.model.sn.locale.TurSNSiteLocale;
import com.viglet.turing.persistence.model.sn.sort.TurSNSiteCustomSort;
import com.viglet.turing.persistence.repository.sn.TurSNSiteRepository;
import com.viglet.turing.persistence.repository.sn.field.TurSNSiteCustomFacetRepository;
import com.viglet.turing.persistence.repository.sn.field.TurSNSiteFieldExtFacetRepository;
import com.viglet.turing.persistence.repository.sn.field.TurSNSiteFieldExtRepository;
import com.viglet.turing.persistence.repository.sn.locale.TurSNSiteLocaleRepository;
import com.viglet.turing.persistence.repository.sn.sort.TurSNSiteCustomSortRepository;

/**
 * Builds and caches {@link TurSNSiteSearchSnapshot} objects keyed by
 * {@code siteName + locale}. The build is lazy: a snapshot for a given pair is
 * materialized only on first call and then served from cache until a write to
 * any of the underlying entities triggers eviction via
 * {@link TurSNSiteSnapshotEvictionListener}.
 *
 * <p>The service intentionally goes through the JPA repositories rather than
 * domain ports so the existing per-method {@code @Cacheable} annotations on
 * those repositories continue to absorb concurrent admin reads — the snapshot
 * sits on top, not in place, of those caches.
 *
 * @author Alexandre Oliveira
 * @since 2026.2.4
 */
@Service
public class TurSNSiteSearchSnapshotService {

    public static final String CACHE_NAME = "turSNSiteSearchSnapshot";

    private final TurSNSiteRepository turSNSiteRepository;
    private final TurSNSiteFieldExtRepository turSNSiteFieldExtRepository;
    private final TurSNSiteFieldExtFacetRepository turSNSiteFieldExtFacetRepository;
    private final TurSNSiteCustomFacetRepository turSNSiteCustomFacetRepository;
    private final TurSNSiteLocaleRepository turSNSiteLocaleRepository;
    private final TurSNSiteCustomSortRepository turSNSiteCustomSortRepository;
    private final TurSearchPipelineObservation pipelineObservation;

    public TurSNSiteSearchSnapshotService(TurSNSiteRepository turSNSiteRepository,
            TurSNSiteFieldExtRepository turSNSiteFieldExtRepository,
            TurSNSiteFieldExtFacetRepository turSNSiteFieldExtFacetRepository,
            TurSNSiteCustomFacetRepository turSNSiteCustomFacetRepository,
            TurSNSiteLocaleRepository turSNSiteLocaleRepository,
            TurSNSiteCustomSortRepository turSNSiteCustomSortRepository,
            TurSearchPipelineObservation pipelineObservation) {
        this.turSNSiteRepository = turSNSiteRepository;
        this.turSNSiteFieldExtRepository = turSNSiteFieldExtRepository;
        this.turSNSiteFieldExtFacetRepository = turSNSiteFieldExtFacetRepository;
        this.turSNSiteCustomFacetRepository = turSNSiteCustomFacetRepository;
        this.turSNSiteLocaleRepository = turSNSiteLocaleRepository;
        this.turSNSiteCustomSortRepository = turSNSiteCustomSortRepository;
        this.pipelineObservation = pipelineObservation;
    }

    /**
     * Returns the snapshot for the given site/locale pair, building it on cache
     * miss. Returns {@link Optional#empty()} when the site does not exist; the
     * empty result itself is cached so repeated 404s do not roundtrip the DB.
     */
    @Cacheable(value = CACHE_NAME,
            key = "T(com.viglet.turing.sn.snapshot.TurSNSiteSearchSnapshotService).cacheKey(#siteName, #locale)")
    public Optional<TurSNSiteSearchSnapshot> getSnapshot(String siteName, Locale locale) {
        // Reaching this method means the cache lookup missed — Spring's @Cacheable
        // proxy short-circuits hits before invoking the method.
        pipelineObservation.recordSnapshotOutcome(TurMeterNames.OUTCOME_MISS);
        return pipelineObservation.record(TurMeterNames.STAGE_SNAPSHOT_BUILD,
                () -> turSNSiteRepository.findByNameIgnoreCase(siteName)
                        .map(site -> build(site, locale)));
    }

    /**
     * Composite cache key. Centralised here so callers (and the SpEL on
     * {@link #getSnapshot}) share the same shape: {@code lower(siteName):locale}.
     * A null locale collapses to {@code "*"} so endpoints that need only
     * site-level config (no facet labels) reuse the same cache slot.
     */
    public static String cacheKey(String siteName, Locale locale) {
        String name = StringUtils.lowerCase(siteName);
        String loc = locale == null ? "*" : locale.toString();
        return name + ":" + loc;
    }

    private TurSNSiteSearchSnapshot build(TurSNSite site, Locale locale) {
        List<TurSNSiteFieldExt> enabledFields = turSNSiteFieldExtRepository
                .findByTurSNSiteAndEnabled(site, 1);

        List<TurSNSiteCustomFacet> customFacets = enabledFields.isEmpty()
                ? Collections.emptyList()
                : turSNSiteCustomFacetRepository.findByFieldExtsWithDetails(enabledFields);

        Map<String, List<TurSNSiteCustomFacet>> customFacetsByFieldId = customFacets.stream()
                .collect(Collectors.groupingBy(cf -> cf.getTurSNSiteFieldExt().getId()));

        Set<String> fieldIdsWithCustomFacets = new HashSet<>(customFacetsByFieldId.keySet());

        Map<String, Set<TurSNSiteFieldExtFacet>> facetLabelsByFieldId = new HashMap<>();
        if (locale != null) {
            for (TurSNSiteFieldExt field : enabledFields) {
                facetLabelsByFieldId.put(field.getId(), turSNSiteFieldExtFacetRepository
                        .findByTurSNSiteFieldExtAndLocale(field, locale));
            }
        }

        boolean hasHlFields = enabledFields.stream().anyMatch(f -> f.getHl() == 1);

        List<TurSNSiteLocale> allLocales = turSNSiteLocaleRepository
                .findByTurSNSiteOrderByPositionAsc(site);

        List<TurSNSiteCustomSort> customSorts = turSNSiteCustomSortRepository
                .findByTurSNSiteWithItems(site);

        String pluginType = resolvePluginType(site);

        return new TurSNSiteSearchSnapshot(site, locale, pluginType, hasHlFields,
                List.copyOf(enabledFields), Set.copyOf(fieldIdsWithCustomFacets),
                Map.copyOf(customFacetsByFieldId), Map.copyOf(facetLabelsByFieldId),
                List.copyOf(allLocales), List.copyOf(customSorts));
    }

    private static String resolvePluginType(TurSNSite site) {
        return Optional.ofNullable(site.getTurSEInstance())
                .map(i -> i.getTurSEVendor())
                .map(v -> StringUtils.isNotBlank(v.getPlugin()) ? v.getPlugin() : v.getId())
                .orElse("solr");
    }
}
