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
package com.viglet.turing.sn.facet;

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

import com.viglet.turing.api.sn.bean.TurSNSiteFacetOrderingDto;
import com.viglet.turing.api.sn.bean.TurSNSiteFacetProfileDto;
import com.viglet.turing.api.sn.bean.TurSNSiteFacetProfileRequest;
import com.viglet.turing.persistence.model.sn.TurSNSite;
import com.viglet.turing.persistence.model.sn.field.TurSNSiteCustomFacet;
import com.viglet.turing.persistence.model.sn.field.TurSNSiteFacetProfile;
import com.viglet.turing.persistence.model.sn.field.TurSNSiteFacetProfileItem;
import com.viglet.turing.persistence.model.sn.field.TurSNSiteFieldExt;
import com.viglet.turing.persistence.repository.sn.TurSNSiteRepository;
import com.viglet.turing.persistence.repository.sn.field.TurSNSiteCustomFacetRepository;
import com.viglet.turing.persistence.repository.sn.field.TurSNSiteFacetProfileRepository;
import com.viglet.turing.persistence.repository.sn.field.TurSNSiteFieldExtRepository;
import com.viglet.turing.sn.TurSNFieldProcess;

/**
 * Manages facet ordering profiles for a Semantic Navigation site.
 * <p>
 * Profiles hold the per-facet positions; exactly one profile per site is the
 * default ("Geral"). The legacy {@code facetPosition} columns are kept in sync
 * with the default profile so non-Solr engines keep reflecting it (DT-6).
 *
 * @author Arthur Nascimento
 * @since 2026.2.7
 */
@Service
public class TurSNSiteFacetProfileService {

    public static final String DEFAULT_PROFILE_NAME = "Geral";

    private final TurSNSiteRepository turSNSiteRepository;
    private final TurSNSiteFacetProfileRepository turSNSiteFacetProfileRepository;
    private final TurSNSiteFieldExtRepository turSNSiteFieldExtRepository;
    private final TurSNSiteCustomFacetRepository turSNSiteCustomFacetRepository;
    private final TurSNFieldProcess turSNFieldProcess;

    public TurSNSiteFacetProfileService(TurSNSiteRepository turSNSiteRepository,
            TurSNSiteFacetProfileRepository turSNSiteFacetProfileRepository,
            TurSNSiteFieldExtRepository turSNSiteFieldExtRepository,
            TurSNSiteCustomFacetRepository turSNSiteCustomFacetRepository,
            TurSNFieldProcess turSNFieldProcess) {
        this.turSNSiteRepository = turSNSiteRepository;
        this.turSNSiteFacetProfileRepository = turSNSiteFacetProfileRepository;
        this.turSNSiteFieldExtRepository = turSNSiteFieldExtRepository;
        this.turSNSiteCustomFacetRepository = turSNSiteCustomFacetRepository;
        this.turSNFieldProcess = turSNFieldProcess;
    }

    // ---------------------------------------------------------------- bootstrap

    /**
     * Idempotently ensures the site has a default profile, seeding it from the
     * legacy {@code facetPosition} columns when absent.
     */
    @Transactional
    public TurSNSiteFacetProfile ensureDefaultProfile(TurSNSite site) {
        return turSNSiteFacetProfileRepository.findByTurSNSiteAndDefaultProfileTrue(site)
                .orElseGet(() -> seedDefaultProfile(site));
    }

    private TurSNSiteFacetProfile seedDefaultProfile(TurSNSite site) {
        TurSNSiteFacetProfile profile = TurSNSiteFacetProfile.builder()
                .name(DEFAULT_PROFILE_NAME)
                .defaultProfile(true)
                .turSNSite(site)
                .build();

        List<TurSNSiteFieldExt> enabledFields = turSNSiteFieldExtRepository
                .findByTurSNSiteAndEnabled(site, 1);
        Set<TurSNSiteFacetProfileItem> items = new HashSet<>();
        enabledFields.stream()
                .filter(field -> field.getFacet() == 1 && field.getFacetPosition() != null)
                .forEach(field -> items.add(buildItem(profile, field, null, field.getFacetPosition())));
        loadCustomFacets(enabledFields).forEach(customFacet -> {
            if (customFacet.getFacetPosition() != null) {
                items.add(buildItem(profile, null, customFacet, customFacet.getFacetPosition()));
            }
        });
        profile.setItems(items);
        return turSNSiteFacetProfileRepository.save(profile);
    }

    // --------------------------------------------------------------------- CRUD

    @Transactional
    public List<TurSNSiteFacetProfileDto> list(String snSiteId) {
        TurSNSite site = requireSite(snSiteId);
        // Guarantees the default profile always exists, so the console never shows an empty
        // selector and "no profile selected" always maps to a real (default) profile.
        ensureDefaultProfile(site);
        return turSNSiteFacetProfileRepository.findByTurSNSite(site).stream()
                .sorted(Comparator.comparing(TurSNSiteFacetProfile::isDefaultProfile).reversed()
                        .thenComparing(TurSNSiteFacetProfile::getName,
                                Comparator.nullsLast(String::compareToIgnoreCase)))
                .map(TurSNSiteFacetProfileDto::from)
                .toList();
    }

    @Transactional
    public TurSNSiteFacetProfileDto create(String snSiteId, TurSNSiteFacetProfileRequest request) {
        TurSNSite site = requireSite(snSiteId);
        String name = requireName(request);
        if (turSNSiteFacetProfileRepository.existsByTurSNSiteAndNameIgnoreCase(site, name)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "A facet profile named '%s' already exists for this site".formatted(name));
        }
        Optional<TurSNSiteFacetProfile> currentDefault = turSNSiteFacetProfileRepository
                .findByTurSNSiteAndDefaultProfileTrue(site);

        TurSNSiteFacetProfile profile = TurSNSiteFacetProfile.builder()
                .name(name)
                .description(request.description())
                .defaultProfile(currentDefault.isEmpty())
                .turSNSite(site)
                .build();

        // A new (non-default) profile starts as a copy of the default ordering.
        Set<TurSNSiteFacetProfileItem> items = new HashSet<>();
        currentDefault.ifPresent(def -> def.getItems().forEach(item -> items.add(buildItem(profile,
                item.getTurSNSiteFieldExt(), item.getTurSNSiteCustomFacet(),
                item.getFacetPosition()))));
        profile.setItems(items);

        return TurSNSiteFacetProfileDto.from(turSNSiteFacetProfileRepository.save(profile));
    }

    @Transactional
    public TurSNSiteFacetProfileDto update(String snSiteId, String profileId,
            TurSNSiteFacetProfileRequest request) {
        TurSNSite site = requireSite(snSiteId);
        TurSNSiteFacetProfile profile = requireProfile(site, profileId);
        String name = requireName(request);
        // The default profile is a fixed, protected profile: it cannot be renamed (nor deleted).
        if (profile.isDefaultProfile() && !name.equals(profile.getName())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "The default facet profile cannot be renamed");
        }
        turSNSiteFacetProfileRepository.findByTurSNSiteAndNameIgnoreCase(site, name)
                .filter(other -> !other.getId().equals(profile.getId()))
                .ifPresent(other -> {
                    throw new ResponseStatusException(HttpStatus.CONFLICT,
                            "A facet profile named '%s' already exists for this site".formatted(name));
                });
        profile.setName(name);
        profile.setDescription(request.description());
        return TurSNSiteFacetProfileDto.from(turSNSiteFacetProfileRepository.save(profile));
    }

    @Transactional
    public List<TurSNSiteFacetProfileDto> setDefault(String snSiteId, String profileId) {
        TurSNSite site = requireSite(snSiteId);
        TurSNSiteFacetProfile newDefault = requireProfile(site, profileId);
        turSNSiteFacetProfileRepository.findByTurSNSite(site).forEach(profile -> {
            boolean shouldBeDefault = profile.getId().equals(newDefault.getId());
            if (profile.isDefaultProfile() != shouldBeDefault) {
                profile.setDefaultProfile(shouldBeDefault);
                turSNSiteFacetProfileRepository.save(profile);
            }
        });
        // DT-6: the legacy positions must mirror the (new) default profile.
        syncLegacyPositions(newDefault);
        return list(snSiteId);
    }

    @Transactional
    public void delete(String snSiteId, String profileId) {
        TurSNSite site = requireSite(snSiteId);
        TurSNSiteFacetProfile profile = requireProfile(site, profileId);
        if (profile.isDefaultProfile()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "The default facet profile cannot be deleted; set another profile as default first");
        }
        turSNSiteFacetProfileRepository.delete(profile);
    }

    // ----------------------------------------------------------------- ordering

    @Transactional(readOnly = true)
    public List<TurSNSiteFacetOrderingDto> getOrdering(String snSiteId, String profileId) {
        TurSNSite site = requireSite(snSiteId);
        return buildOrdering(snSiteId, requireProfile(site, profileId));
    }

    @Transactional
    public List<TurSNSiteFacetOrderingDto> saveOrdering(String snSiteId, String profileId,
            List<TurSNSiteFacetOrderingDto> orderings) {
        TurSNSite site = requireSite(snSiteId);
        return persistOrdering(snSiteId, site, requireProfile(site, profileId), orderings);
    }

    @Transactional
    public List<TurSNSiteFacetOrderingDto> getDefaultOrdering(String snSiteId) {
        TurSNSite site = requireSite(snSiteId);
        return buildOrdering(snSiteId, ensureDefaultProfile(site));
    }

    @Transactional
    public List<TurSNSiteFacetOrderingDto> saveDefaultOrdering(String snSiteId,
            List<TurSNSiteFacetOrderingDto> orderings) {
        TurSNSite site = requireSite(snSiteId);
        return persistOrdering(snSiteId, site, ensureDefaultProfile(site), orderings);
    }

    private List<TurSNSiteFacetOrderingDto> buildOrdering(String snSiteId,
            TurSNSiteFacetProfile profile) {
        Map<String, Integer> positions = profilePositions(profile);
        return turSNFieldProcess.getTurSNSiteFacetOrdering(snSiteId)
                .orElseGet(Collections::emptyList).stream()
                .map(def -> toOrderingDto(def, positions.get(def.getId())))
                .sorted(Comparator.comparing(TurSNSiteFacetOrderingDto::getFacetPosition,
                        Comparator.nullsLast(Integer::compareTo))
                        .thenComparing(TurSNSiteFacetOrderingDto::getFacetName,
                                Comparator.nullsLast(String::compareToIgnoreCase)))
                .toList();
    }

    private List<TurSNSiteFacetOrderingDto> persistOrdering(String snSiteId, TurSNSite site,
            TurSNSiteFacetProfile profile, List<TurSNSiteFacetOrderingDto> orderings) {
        List<TurSNSiteFieldExt> enabledFields = turSNSiteFieldExtRepository
                .findByTurSNSiteAndEnabled(site, 1);

        Map<String, TurSNSiteFieldExt> fieldFacetById = enabledFields.stream()
                .filter(field -> field.getFacet() == 1)
                .collect(Collectors.toMap(TurSNSiteFieldExt::getId, field -> field, (a, b) -> a));
        Map<String, TurSNSiteCustomFacet> customFacetById = loadCustomFacets(enabledFields).stream()
                .collect(Collectors.toMap(TurSNSiteCustomFacet::getId, facet -> facet, (a, b) -> a));

        boolean isDefault = profile.isDefaultProfile();
        Set<TurSNSiteFacetProfileItem> items = new HashSet<>();
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
                items.add(buildItem(profile, field, null, position));
                if (isDefault) {
                    field.setFacetPosition(position);
                    fieldsToSave.add(field);
                }
                continue;
            }
            TurSNSiteCustomFacet customFacet = customFacetById.get(dto.getId());
            if (customFacet != null) {
                items.add(buildItem(profile, null, customFacet, position));
                if (isDefault) {
                    customFacet.setFacetPosition(position);
                    customFacetsToSave.add(customFacet);
                }
            }
        }

        profile.setItems(items);
        turSNSiteFacetProfileRepository.save(profile);
        if (!fieldsToSave.isEmpty()) {
            turSNSiteFieldExtRepository.saveAll(fieldsToSave);
        }
        // Save each custom facet through the proxied save() so its designed @CacheEvict fires;
        // saveAll() bypasses the caching aspect and would leave findByFieldExtsWithDetails stale.
        customFacetsToSave.forEach(turSNSiteCustomFacetRepository::save);
        return buildOrdering(snSiteId, profile);
    }

    /** Writes the profile's positions back onto the legacy facetPosition columns. */
    private void syncLegacyPositions(TurSNSiteFacetProfile profile) {
        Set<TurSNSiteFieldExt> legacyToSave = new HashSet<>();
        profile.getItems().forEach(item -> {
            Integer position = item.getFacetPosition();
            if (position == null) {
                return;
            }
            if (item.getTurSNSiteFieldExt() != null) {
                item.getTurSNSiteFieldExt().setFacetPosition(position);
                legacyToSave.add(item.getTurSNSiteFieldExt());
            } else if (item.getTurSNSiteCustomFacet() != null) {
                TurSNSiteCustomFacet customFacet = item.getTurSNSiteCustomFacet();
                customFacet.setFacetPosition(position);
                legacyToSave.add(customFacet.getTurSNSiteFieldExt());
            }
        });
        if (!legacyToSave.isEmpty()) {
            turSNSiteFieldExtRepository.saveAll(legacyToSave);
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

    private Map<String, Integer> profilePositions(TurSNSiteFacetProfile profile) {
        Map<String, Integer> positions = new HashMap<>();
        profile.getItems().forEach(item -> {
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

    private static TurSNSiteFacetProfileItem buildItem(TurSNSiteFacetProfile profile,
            TurSNSiteFieldExt field, TurSNSiteCustomFacet customFacet, Integer position) {
        return TurSNSiteFacetProfileItem.builder()
                .turSNSiteFacetProfile(profile)
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

    private TurSNSiteFacetProfile requireProfile(TurSNSite site, String profileId) {
        return turSNSiteFacetProfileRepository.findById(profileId)
                .filter(profile -> profile.getTurSNSite() != null
                        && profile.getTurSNSite().getId().equals(site.getId()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Facet profile not found for this site"));
    }

    private static String requireName(TurSNSiteFacetProfileRequest request) {
        String name = request != null && request.name() != null ? request.name().trim() : "";
        if (name.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Profile name is required");
        }
        return name;
    }
}
