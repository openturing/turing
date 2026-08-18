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

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.viglet.turing.api.sn.bean.TurProfileFieldConfigRequest;
import com.viglet.turing.api.sn.bean.TurSNSiteFieldProfileConfigDto;
import com.viglet.turing.api.sn.bean.TurSNSiteFieldProfileConfigDto.FacetConfig;
import com.viglet.turing.api.sn.bean.TurSNSiteFieldProfileConfigDto.OverrideFlags;
import com.viglet.turing.persistence.model.profile.TurProfile;
import com.viglet.turing.persistence.model.profile.TurProfileFieldConfig;
import com.viglet.turing.persistence.model.sn.TurSNSite;
import com.viglet.turing.persistence.model.sn.field.TurSNSiteFieldExt;
import com.viglet.turing.persistence.repository.profile.TurProfileFieldConfigRepository;
import com.viglet.turing.persistence.repository.profile.TurProfileRepository;
import com.viglet.turing.persistence.repository.sn.TurSNSiteRepository;
import com.viglet.turing.persistence.repository.sn.field.TurSNSiteFieldExtRepository;
import com.viglet.turing.spring.utils.TurPersistenceUtils;

/**
 * Per-profile, per-field facet configuration (spec 003): the 2nd configuration
 * type attached to a global {@link TurProfile}, edited from the "Campos" console
 * page with a profile selected.
 * <p>
 * Effective config = override ∪ global: for each facet attribute, use the
 * profile's override when set, else the field's global value
 * ({@link TurSNSiteFieldExt}). The <b>default</b> profile never has overrides
 * (D3): editing "Campos" under the default writes straight to the global field
 * (mirror, like DT-6 of the ordering), and the runtime fallback (no/unknown
 * profile) resolves to the global — a single source of truth for "global".
 *
 * @author Arthur Nascimento
 * @since 2026.2.7
 */
@Service
public class TurProfileFieldConfigService {

    private final TurProfileRepository turProfileRepository;
    private final TurProfileFieldConfigRepository turProfileFieldConfigRepository;
    private final TurSNSiteRepository turSNSiteRepository;
    private final TurSNSiteFieldExtRepository turSNSiteFieldExtRepository;

    public TurProfileFieldConfigService(TurProfileRepository turProfileRepository,
            TurProfileFieldConfigRepository turProfileFieldConfigRepository,
            TurSNSiteRepository turSNSiteRepository,
            TurSNSiteFieldExtRepository turSNSiteFieldExtRepository) {
        this.turProfileRepository = turProfileRepository;
        this.turProfileFieldConfigRepository = turProfileFieldConfigRepository;
        this.turSNSiteRepository = turSNSiteRepository;
        this.turSNSiteFieldExtRepository = turSNSiteFieldExtRepository;
    }

    // -------------------------------------------------------------------- reads

    /** All fields of the site with their effective/overridden/global facet config for the profile. */
    @Transactional
    public List<TurSNSiteFieldProfileConfigDto> getEffectiveConfigs(String snSiteId, String profileId) {
        TurSNSite site = requireSite(snSiteId);
        TurProfile profile = requireProfile(profileId);
        Map<String, TurProfileFieldConfig> overrides = indexOverrides(profile);
        return turSNSiteFieldExtRepository
                .findByTurSNSite(TurPersistenceUtils.orderByNameIgnoreCase(), site).stream()
                .map(field -> toDto(field, overrides.get(field.getId())))
                .toList();
    }

    /**
     * Effective facet config per field id for a profile — reused by the search runtime (spec 003
     * Fase 3) to overlay the profile's config before facets are built. {@code null}/default profile
     * resolves to the global values.
     */
    @Transactional(readOnly = true)
    public Map<String, FacetConfig> resolveEffective(TurProfile profile,
            Collection<TurSNSiteFieldExt> fields) {
        Map<String, TurProfileFieldConfig> overrides = indexOverrides(profile);
        Map<String, FacetConfig> result = new HashMap<>();
        for (TurSNSiteFieldExt field : fields) {
            result.put(field.getId(), effectiveConfig(overrides.get(field.getId()), field));
        }
        return result;
    }

    // ------------------------------------------------------------------- writes

    /**
     * Upserts the profile's override for a field. Default profile (D3) writes the facet subset onto
     * the global field instead; a non-default profile with no override left removes the row.
     */
    @Transactional
    public TurSNSiteFieldProfileConfigDto saveOverride(String snSiteId, String profileId,
            String fieldExtId, TurProfileFieldConfigRequest request) {
        TurSNSite site = requireSite(snSiteId);
        TurProfile profile = requireProfile(profileId);
        TurSNSiteFieldExt field = requireField(fieldExtId, site);

        if (profile.isDefaultProfile()) {
            applyToGlobal(field, request, site);
            turSNSiteFieldExtRepository.save(field);
            return toDto(field, null);
        }

        Optional<TurProfileFieldConfig> existing = turProfileFieldConfigRepository
                .findByTurProfileAndTurSNSiteFieldExt(profile, field);
        if (request == null || !request.hasAnyOverride()) {
            existing.ifPresent(turProfileFieldConfigRepository::delete);
            return toDto(field, null);
        }
        TurProfileFieldConfig config = existing.orElseGet(() -> TurProfileFieldConfig.builder()
                .turProfile(profile).turSNSiteFieldExt(field).build());
        config.setFacet(request.facet());
        config.setFacetName(request.facetName());
        config.setFacetSort(request.facetSort());
        config.setFacetType(request.facetType());
        config.setFacetItemType(request.facetItemType());
        config.setFacetRange(request.facetRange());
        config.setSecondaryFacet(request.secondaryFacet());
        config.setShowAllFacetItems(request.showAllFacetItems());
        turProfileFieldConfigRepository.save(config);
        return toDto(field, config);
    }

    /** Removes all of a field's overrides for a (non-default) profile — back to inheriting the global. */
    @Transactional
    public boolean clearOverride(String snSiteId, String profileId, String fieldExtId) {
        TurSNSite site = requireSite(snSiteId);
        TurProfile profile = requireProfile(profileId);
        if (profile.isDefaultProfile()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "The default profile has no per-profile override to clear; "
                            + "edit the global field configuration instead");
        }
        TurSNSiteFieldExt field = requireField(fieldExtId, site);
        return turProfileFieldConfigRepository.findByTurProfileAndTurSNSiteFieldExt(profile, field)
                .map(config -> {
                    turProfileFieldConfigRepository.delete(config);
                    return true;
                })
                .orElse(false);
    }

    // ------------------------------------------------------------------ helpers

    private Map<String, TurProfileFieldConfig> indexOverrides(TurProfile profile) {
        // The default profile never has overrides (D3), so its effective config is always the global.
        if (profile == null || profile.isDefaultProfile()) {
            return Map.of();
        }
        return turProfileFieldConfigRepository.findByProfileId(profile.getId()).stream()
                .collect(Collectors.toMap(config -> config.getTurSNSiteFieldExt().getId(),
                        config -> config, (a, b) -> a));
    }

    /** Writes the overridable facet subset onto the global field (default profile, D3). */
    private void applyToGlobal(TurSNSiteFieldExt field, TurProfileFieldConfigRequest request,
            TurSNSite site) {
        if (request == null) {
            return;
        }
        if (request.facet() != null) {
            field.setFacet(request.facet());
            field.setFacetPosition(facetPositionFor(field, site));
        }
        if (request.facetName() != null) {
            field.setFacetName(request.facetName());
        }
        if (request.facetSort() != null) {
            field.setFacetSort(request.facetSort());
        }
        if (request.facetType() != null) {
            field.setFacetType(request.facetType());
        }
        if (request.facetItemType() != null) {
            field.setFacetItemType(request.facetItemType());
        }
        if (request.facetRange() != null) {
            field.setFacetRange(request.facetRange());
        }
        if (request.secondaryFacet() != null) {
            field.setSecondaryFacet(request.secondaryFacet());
        }
        if (request.showAllFacetItems() != null) {
            field.setShowAllFacetItems(request.showAllFacetItems());
        }
    }

    /** Mirrors the facet-position bookkeeping of the field-ext update path when toggling facet on/off. */
    private Integer facetPositionFor(TurSNSiteFieldExt field, TurSNSite site) {
        if (field.getFacet() != 1) {
            return 0;
        }
        Integer position = field.getFacetPosition();
        return (position != null && position > 0) ? position
                : turSNSiteFieldExtRepository.findMaxFacetPosition(site).map(max -> max + 1).orElse(1);
    }

    private static TurSNSiteFieldProfileConfigDto toDto(TurSNSiteFieldExt field,
            TurProfileFieldConfig override) {
        return new TurSNSiteFieldProfileConfigDto(
                field.getId(),
                field.getName(),
                field.getType() != null ? field.getType().name() : null,
                field.getEnabled(),
                effectiveConfig(override, field),
                overrideFlags(override),
                globalConfig(field));
    }

    private static FacetConfig globalConfig(TurSNSiteFieldExt field) {
        return new FacetConfig(field.getFacet(), field.getFacetName(), field.getFacetSort(),
                field.getFacetType(), field.getFacetItemType(), field.getFacetRange(),
                field.getSecondaryFacet(), field.getShowAllFacetItems());
    }

    private static FacetConfig effectiveConfig(TurProfileFieldConfig override,
            TurSNSiteFieldExt field) {
        if (override == null) {
            return globalConfig(field);
        }
        return new FacetConfig(
                override.getFacet() != null ? override.getFacet() : field.getFacet(),
                override.getFacetName() != null ? override.getFacetName() : field.getFacetName(),
                override.getFacetSort() != null ? override.getFacetSort() : field.getFacetSort(),
                override.getFacetType() != null ? override.getFacetType() : field.getFacetType(),
                override.getFacetItemType() != null ? override.getFacetItemType()
                        : field.getFacetItemType(),
                override.getFacetRange() != null ? override.getFacetRange() : field.getFacetRange(),
                override.getSecondaryFacet() != null ? override.getSecondaryFacet()
                        : field.getSecondaryFacet(),
                override.getShowAllFacetItems() != null ? override.getShowAllFacetItems()
                        : field.getShowAllFacetItems());
    }

    private static OverrideFlags overrideFlags(TurProfileFieldConfig override) {
        if (override == null) {
            return new OverrideFlags(false, false, false, false, false, false, false, false);
        }
        return new OverrideFlags(
                override.getFacet() != null,
                override.getFacetName() != null,
                override.getFacetSort() != null,
                override.getFacetType() != null,
                override.getFacetItemType() != null,
                override.getFacetRange() != null,
                override.getSecondaryFacet() != null,
                override.getShowAllFacetItems() != null);
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

    private TurSNSiteFieldExt requireField(String fieldExtId, TurSNSite site) {
        TurSNSiteFieldExt field = turSNSiteFieldExtRepository.findById(fieldExtId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Field not found"));
        if (field.getTurSNSite() == null || !site.getId().equals(field.getTurSNSite().getId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "Field does not belong to this site");
        }
        return field;
    }
}
