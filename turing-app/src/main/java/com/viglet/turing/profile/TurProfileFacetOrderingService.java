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
package com.viglet.turing.profile;

import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.viglet.turing.api.profile.bean.TurProfileDto;
import com.viglet.turing.api.sn.bean.TurSNSiteFacetOrderingDto;
import com.viglet.turing.persistence.model.profile.TurProfile;
import com.viglet.turing.persistence.model.profile.TurProfileFacetOrdering;
import com.viglet.turing.persistence.model.profile.TurProfileFacetOrderingItem;
import com.viglet.turing.persistence.model.sn.TurSNSite;
import com.viglet.turing.persistence.model.sn.field.TurSNSiteCustomFacet;
import com.viglet.turing.persistence.model.sn.field.TurSNSiteFieldExt;
import com.viglet.turing.persistence.repository.profile.TurProfileFacetOrderingRepository;
import com.viglet.turing.persistence.repository.profile.TurProfileRepository;
import com.viglet.turing.persistence.repository.sn.TurSNSiteRepository;
import com.viglet.turing.persistence.repository.sn.field.TurSNSiteCustomFacetRepository;
import com.viglet.turing.persistence.repository.sn.field.TurSNSiteFieldExtRepository;
import com.viglet.turing.sn.TurSNFieldProcess;
import com.viglet.turing.sn.facet.TurSNFacetDefinition;

/**
 * Manages the facet-ordering configuration of a global {@link TurProfile} for a
 * Semantic Navigation site — one {@link TurProfileFacetOrdering} per
 * (profile × site), attached from the site's "Facets" console page (spec 002).
 * <p>
 * Read fallback: the profile's own (profile, site) ordering → the default
 * profile's ordering for the site → the legacy {@code facetPosition} columns.
 * <p>
 * DT-6: the legacy {@code facetPosition} columns are kept as a mirror of the
 * <b>default</b> profile's per-site ordering, so Elasticsearch/Lucene (which
 * still read the legacy columns) follow the default. The mirror is rewritten
 * when the default's ordering is saved and when a profile is promoted to
 * default. Custom facets are saved one by one through the proxied
 * {@code save()} so their {@code @CacheEvict} fires ({@code saveAll} bypasses
 * the caching aspect).
 *
 * @author Arthur Nascimento
 * @since 2026.2.7
 */
@Service
public class TurProfileFacetOrderingService {

    private final TurProfileService turProfileService;
    private final TurProfileRepository turProfileRepository;
    private final TurProfileFacetOrderingRepository turProfileFacetOrderingRepository;
    private final TurSNSiteRepository turSNSiteRepository;
    private final TurSNSiteFieldExtRepository turSNSiteFieldExtRepository;
    private final TurSNSiteCustomFacetRepository turSNSiteCustomFacetRepository;
    private final TurSNFieldProcess turSNFieldProcess;

    public TurProfileFacetOrderingService(TurProfileService turProfileService,
            TurProfileRepository turProfileRepository,
            TurProfileFacetOrderingRepository turProfileFacetOrderingRepository,
            TurSNSiteRepository turSNSiteRepository,
            TurSNSiteFieldExtRepository turSNSiteFieldExtRepository,
            TurSNSiteCustomFacetRepository turSNSiteCustomFacetRepository,
            TurSNFieldProcess turSNFieldProcess) {
        this.turProfileService = turProfileService;
        this.turProfileRepository = turProfileRepository;
        this.turProfileFacetOrderingRepository = turProfileFacetOrderingRepository;
        this.turSNSiteRepository = turSNSiteRepository;
        this.turSNSiteFieldExtRepository = turSNSiteFieldExtRepository;
        this.turSNSiteCustomFacetRepository = turSNSiteCustomFacetRepository;
        this.turSNFieldProcess = turSNFieldProcess;
    }

    // ----------------------------------------------------------------- ordering

    @Transactional
    public List<TurSNSiteFacetOrderingDto> getOrdering(String snSiteId, String profileId) {
        TurSNSite site = requireSite(snSiteId);
        TurProfile profile = requireProfile(profileId);
        return buildOrdering(snSiteId, profile, resolveReadPositions(profile, site));
    }

    @Transactional
    public List<TurSNSiteFacetOrderingDto> saveOrdering(String snSiteId, String profileId,
            List<TurSNSiteFacetOrderingDto> orderings) {
        TurSNSite site = requireSite(snSiteId);
        return persistOrdering(snSiteId, site, requireProfile(profileId), orderings);
    }

    @Transactional
    public List<TurSNSiteFacetOrderingDto> getDefaultOrdering(String snSiteId) {
        TurSNSite site = requireSite(snSiteId);
        TurProfile defaultProfile = turProfileService.ensureDefaultProfile();
        return buildOrdering(snSiteId, defaultProfile, resolveReadPositions(defaultProfile, site));
    }

    @Transactional
    public List<TurSNSiteFacetOrderingDto> saveDefaultOrdering(String snSiteId,
            List<TurSNSiteFacetOrderingDto> orderings) {
        TurSNSite site = requireSite(snSiteId);
        return persistOrdering(snSiteId, site, turProfileService.ensureDefaultProfile(), orderings);
    }

    // ------------------------------------------------------- default promotion

    /**
     * Promotes a profile to system default and rewrites the legacy
     * {@code facetPosition} mirror on every site where the profile has an
     * ordering (DT-6).
     */
    @Transactional
    public List<TurProfileDto> setDefault(String profileId) {
        List<TurProfileDto> result = turProfileService.setDefault(profileId);
        turProfileRepository.findById(profileId)
                .ifPresent(newDefault -> turProfileFacetOrderingRepository
                        .findByTurProfile(newDefault)
                        .forEach(this::syncLegacyPositions));
        return result;
    }

    // ----------------------------------------------------------------- internal

    private List<TurSNSiteFacetOrderingDto> buildOrdering(String snSiteId, TurProfile profile,
            Map<String, Integer> positions) {
        // spec 003: definitions follow the profile's effective field-facet config, so a facet turned
        // off for this profile in "Campos" is absent here (and one turned on appears).
        return turSNFieldProcess.getTurSNSiteFacetOrdering(snSiteId, profile)
                .orElseGet(Collections::emptyList).stream()
                .map(def -> toOrderingDto(def, positions.get(def.getId())))
                .sorted(Comparator.comparing(TurSNSiteFacetOrderingDto::getFacetPosition,
                        Comparator.nullsLast(Integer::compareTo))
                        .thenComparing(TurSNSiteFacetOrderingDto::getFacetName,
                                Comparator.nullsLast(String::compareToIgnoreCase)))
                .toList();
    }

    private List<TurSNSiteFacetOrderingDto> persistOrdering(String snSiteId, TurSNSite site,
            TurProfile profile, List<TurSNSiteFacetOrderingDto> orderings) {
        List<TurSNSiteFieldExt> enabledFields = turSNSiteFieldExtRepository
                .findByTurSNSiteAndEnabled(site, 1);
        Map<String, TurSNSiteFieldExt> fieldFacetById = enabledFields.stream()
                .filter(field -> field.getFacet() == 1)
                .collect(Collectors.toMap(TurSNSiteFieldExt::getId, field -> field, (a, b) -> a));
        Map<String, TurSNSiteCustomFacet> customFacetById = loadCustomFacets(enabledFields).stream()
                .collect(Collectors.toMap(TurSNSiteCustomFacet::getId, facet -> facet, (a, b) -> a));

        boolean isDefault = profile.isDefaultProfile();
        Set<TurProfileFacetOrderingItem> items = new HashSet<>();
        Set<TurSNSiteFieldExt> fieldsToSave = new HashSet<>();
        Set<TurSNSiteCustomFacet> customFacetsToSave = new HashSet<>();

        for (TurSNSiteFacetOrderingDto dto : Optional.ofNullable(orderings)
                .orElseGet(Collections::emptyList)) {
            Integer position = dto.getFacetPosition();
            if (position == null || position <= 0) {
                continue;
            }
            TurSNSiteFieldExt field = fieldFacetById.get(dto.getId());
            if (field != null) {
                items.add(buildItem(field, null, position));
                if (isDefault) {
                    field.setFacetPosition(position);
                    fieldsToSave.add(field);
                }
                continue;
            }
            TurSNSiteCustomFacet customFacet = customFacetById.get(dto.getId());
            if (customFacet != null) {
                items.add(buildItem(null, customFacet, position));
                if (isDefault) {
                    customFacet.setFacetPosition(position);
                    customFacetsToSave.add(customFacet);
                }
            }
        }

        TurProfileFacetOrdering ordering = turProfileFacetOrderingRepository
                .findByTurProfileAndTurSNSite(profile, site)
                .orElseGet(() -> TurProfileFacetOrdering.builder()
                        .turProfile(profile).turSNSite(site).build());
        ordering.setItems(items);
        turProfileFacetOrderingRepository.save(ordering);

        if (!fieldsToSave.isEmpty()) {
            turSNSiteFieldExtRepository.saveAll(fieldsToSave);
        }
        // Proxied save() per custom facet so its designed @CacheEvict fires (NFR-03).
        customFacetsToSave.forEach(turSNSiteCustomFacetRepository::save);

        return buildOrdering(snSiteId, profile, positionsByFacetId(ordering));
    }

    /**
     * Read fallback (FR-13, console read): the profile's own ordering for the
     * site → the default profile's ordering → the legacy positions.
     */
    private Map<String, Integer> resolveReadPositions(TurProfile profile, TurSNSite site) {
        Optional<TurProfileFacetOrdering> own = turProfileFacetOrderingRepository
                .findByTurProfileAndTurSNSite(profile, site);
        if (own.isPresent()) {
            return positionsByFacetId(own.get());
        }
        if (!profile.isDefaultProfile()) {
            Optional<Map<String, Integer>> fromDefault = turProfileRepository
                    .findByDefaultProfileTrue()
                    .flatMap(def -> turProfileFacetOrderingRepository
                            .findByTurProfileAndTurSNSite(def, site))
                    .map(TurProfileFacetOrderingService::positionsByFacetId);
            if (fromDefault.isPresent()) {
                return fromDefault.get();
            }
        }
        return legacyPositions(site);
    }

    /** Legacy fallback: positions straight from the facetPosition columns. */
    private Map<String, Integer> legacyPositions(TurSNSite site) {
        List<TurSNSiteFieldExt> enabledFields = turSNSiteFieldExtRepository
                .findByTurSNSiteAndEnabled(site, 1);
        Map<String, Integer> positions = new HashMap<>();
        enabledFields.stream()
                .filter(field -> field.getFacet() == 1 && field.getFacetPosition() != null)
                .forEach(field -> positions.put(field.getId(), field.getFacetPosition()));
        loadCustomFacets(enabledFields).stream()
                .filter(customFacet -> customFacet.getFacetPosition() != null)
                .forEach(customFacet -> positions.put(customFacet.getId(),
                        customFacet.getFacetPosition()));
        return positions;
    }

    /** Writes the ordering's positions back onto the legacy facetPosition columns (DT-6). */
    private void syncLegacyPositions(TurProfileFacetOrdering ordering) {
        Set<TurSNSiteFieldExt> fieldsToSave = new HashSet<>();
        ordering.getItems().forEach(item -> {
            Integer position = item.getFacetPosition();
            if (position == null) {
                return;
            }
            if (item.getTurSNSiteFieldExt() != null) {
                item.getTurSNSiteFieldExt().setFacetPosition(position);
                fieldsToSave.add(item.getTurSNSiteFieldExt());
            } else if (item.getTurSNSiteCustomFacet() != null) {
                TurSNSiteCustomFacet customFacet = item.getTurSNSiteCustomFacet();
                customFacet.setFacetPosition(position);
                // Proxied save() so the custom facet @CacheEvict fires (NFR-03).
                turSNSiteCustomFacetRepository.save(customFacet);
            }
        });
        if (!fieldsToSave.isEmpty()) {
            turSNSiteFieldExtRepository.saveAll(fieldsToSave);
        }
    }

    // ------------------------------------------------------------------ helpers

    /**
     * Loads custom facets via a fetch query rather than the lazy
     * {@code fieldExt.customFacets} collection: {@code findByTurSNSiteAndEnabled} is
     * {@code @Cacheable}, so its entities may be detached (no Hibernate session) and touching the
     * lazy collection throws {@code LazyInitializationException}.
     */
    private List<TurSNSiteCustomFacet> loadCustomFacets(List<TurSNSiteFieldExt> enabledFields) {
        return enabledFields.isEmpty() ? Collections.emptyList()
                : turSNSiteCustomFacetRepository.findByFieldExtsWithDetails(enabledFields);
    }

    private static Map<String, Integer> positionsByFacetId(TurProfileFacetOrdering ordering) {
        Map<String, Integer> positions = new HashMap<>();
        ordering.getItems().forEach(item -> {
            String facetId = item.getTurSNSiteFieldExt() != null
                    ? item.getTurSNSiteFieldExt().getId()
                    : item.getTurSNSiteCustomFacet() != null
                            ? item.getTurSNSiteCustomFacet().getId()
                            : null;
            if (facetId != null && item.getFacetPosition() != null) {
                positions.put(facetId, item.getFacetPosition());
            }
        });
        return positions;
    }

    private static TurSNSiteFacetOrderingDto toOrderingDto(TurSNFacetDefinition def,
            Integer profilePosition) {
        TurSNSiteFieldExt fieldExt = def.getFieldExt();
        return new TurSNSiteFacetOrderingDto()
                .setId(def.getId())
                .setName(def.getName())
                .setFacetName(def.getLabel())
                .setFacetPosition(profilePosition)
                .setFieldExtId(fieldExt != null ? fieldExt.getId() : null)
                .setFieldExtName(fieldExt != null ? fieldExt.getName() : null)
                .setCustomFacet(def.isCustomFacet());
    }

    private static TurProfileFacetOrderingItem buildItem(TurSNSiteFieldExt field,
            TurSNSiteCustomFacet customFacet, Integer position) {
        return TurProfileFacetOrderingItem.builder()
                .turSNSiteFieldExt(field)
                .turSNSiteCustomFacet(customFacet)
                .facetPosition(position)
                .build();
    }

    private TurSNSite requireSite(String snSiteId) {
        return turSNSiteRepository.findById(snSiteId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Semantic Navigation site not found"));
    }

    private TurProfile requireProfile(String profileId) {
        return turProfileRepository.findById(profileId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Profile not found"));
    }
}
