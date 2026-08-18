package com.viglet.turing.sn;

import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;

import com.viglet.turing.persistence.model.profile.TurProfile;
import com.viglet.turing.persistence.model.profile.TurProfileFacetOrdering;
import com.viglet.turing.persistence.model.sn.TurSNSite;
import com.viglet.turing.persistence.model.sn.field.TurSNSiteFieldExt;
import com.viglet.turing.api.sn.bean.TurSNSiteFieldProfileConfigDto.FacetConfig;
import com.viglet.turing.persistence.repository.profile.TurProfileFacetOrderingRepository;
import com.viglet.turing.persistence.repository.profile.TurProfileRepository;
import com.viglet.turing.persistence.repository.sn.TurSNSiteRepository;
import com.viglet.turing.persistence.repository.sn.field.TurSNSiteFieldExtRepository;
import com.viglet.turing.profile.TurProfileFieldConfigService;
import com.viglet.turing.sn.facet.TurSNFacetDefinition;
import com.viglet.turing.sn.facet.TurSNFacetDefinitionFactory;

/**
 * @author Alexandre Oliveira
 * @since 0.3.9
 */
@Component
public class TurSNFieldProcess {
        private final TurSNSiteRepository turSNSiteRepository;
        private final TurSNSiteFieldExtRepository turSNSiteFieldExtRepository;
        private final TurSNFacetDefinitionFactory turSNFacetDefinitionFactory;
        private final TurProfileRepository turProfileRepository;
        private final TurProfileFacetOrderingRepository turProfileFacetOrderingRepository;
        private final TurProfileFieldConfigService turProfileFieldConfigService;

        public TurSNFieldProcess(TurSNSiteRepository turSNSiteRepository,
                        TurSNSiteFieldExtRepository turSNSiteFieldExtRepository,
                        TurSNFacetDefinitionFactory turSNFacetDefinitionFactory,
                        TurProfileRepository turProfileRepository,
                        TurProfileFacetOrderingRepository turProfileFacetOrderingRepository,
                        TurProfileFieldConfigService turProfileFieldConfigService) {
                this.turSNSiteRepository = turSNSiteRepository;
                this.turSNSiteFieldExtRepository = turSNSiteFieldExtRepository;
                this.turSNFacetDefinitionFactory = turSNFacetDefinitionFactory;
                this.turProfileRepository = turProfileRepository;
                this.turProfileFacetOrderingRepository = turProfileFacetOrderingRepository;
                this.turProfileFieldConfigService = turProfileFieldConfigService;
        }

        @NotNull
        public Optional<List<TurSNFacetDefinition>> getTurSNSiteFacetOrdering(String snSiteId) {
                return getTurSNSiteFacetOrdering(snSiteId, (TurProfile) null);
        }

        /**
         * Facet definitions of a site for a given profile (spec 003): the per-profile field-facet
         * config is overlaid before the definitions are built, so a facet turned off for the profile
         * (in "Campos") disappears from "Facetas" for that profile — and one turned on appears —
         * keeping "Campos" and "Facetas" interlinked. A {@code null}/default profile uses the global
         * config (unchanged behaviour).
         *
         * @since 2026.2.7
         */
        @NotNull
        public Optional<List<TurSNFacetDefinition>> getTurSNSiteFacetOrdering(String snSiteId,
                        TurProfile profile) {
                return turSNSiteRepository.findById(snSiteId)
                                .map(turSNSite -> {
                                        List<TurSNSiteFieldExt> enabledFields = turSNSiteFieldExtRepository
                                                        .findByTurSNSiteAndEnabled(turSNSite, 1);

                                        return turSNFacetDefinitionFactory
                                                        .fromFields(applyProfileFacetOverlay(enabledFields, profile),
                                                                        null)
                                                        .stream()
                                                        .sorted(Comparator.comparing(TurSNFacetDefinition::getPosition)
                                                                        .thenComparing(TurSNFacetDefinition::getLabel,
                                                                                        Comparator.nullsLast(
                                                                                                        String::compareToIgnoreCase)))
                                                        .toList();
                                });
        }

        /**
         * Overlays a profile's effective facet config onto copies of the fields (never mutating the
         * originals). No-op for a {@code null}/default profile (its config mirrors the global).
         */
        private List<TurSNSiteFieldExt> applyProfileFacetOverlay(List<TurSNSiteFieldExt> fields,
                        TurProfile profile) {
                if (profile == null || profile.isDefaultProfile() || turProfileFieldConfigService == null) {
                        return fields;
                }
                Map<String, FacetConfig> effective = turProfileFieldConfigService
                                .resolveEffective(profile, fields);
                return fields.stream()
                                .map(field -> overlayFacetConfig(field, effective.get(field.getId())))
                                .toList();
        }

        private static TurSNSiteFieldExt overlayFacetConfig(TurSNSiteFieldExt field,
                        FacetConfig config) {
                if (config == null) {
                        return field;
                }
                return field.toBuilder()
                                .facet(config.facet() != null ? config.facet() : field.getFacet())
                                .facetName(config.facetName())
                                .facetSort(config.facetSort() != null ? config.facetSort()
                                                : field.getFacetSort())
                                .facetType(config.facetType() != null ? config.facetType()
                                                : field.getFacetType())
                                .facetItemType(config.facetItemType() != null ? config.facetItemType()
                                                : field.getFacetItemType())
                                .facetRange(config.facetRange() != null ? config.facetRange()
                                                : field.getFacetRange())
                                .secondaryFacet(config.secondaryFacet())
                                .showAllFacetItems(config.showAllFacetItems())
                                .build();
        }

        @NotNull
        public Optional<List<TurSNSiteFieldExt>> getTurSNSiteFieldOrdering(String snSiteId) {
                return getTurSNSiteFieldOrdering(snSiteId, null);
        }

        /**
         * Facet ordering resolved by a global profile (by name or id) and the
         * searched site. When the profile is absent/blank/not found, or has no
         * configuration for the site, the default profile's configuration for the
         * site is used; if there is none, the legacy {@code facetPosition} applies.
         *
         * @param snSiteId     the SN site id
         * @param profileNameOrId the requested (global) profile name or id (may be {@code null})
         * @since 2026.2.7
         */
        @NotNull
        public Optional<List<TurSNSiteFieldExt>> getTurSNSiteFieldOrdering(String snSiteId,
                        String profileNameOrId) {
                return turSNSiteRepository.findById(snSiteId).map(turSNSite -> {
                        List<TurSNSiteFieldExt> enabledFields = turSNSiteFieldExtRepository
                                        .findByTurSNSiteAndEnabled(turSNSite, 1);
                        Map<String, Integer> profilePositions = resolveProfilePositions(turSNSite, profileNameOrId);
                        boolean hasProfile = !profilePositions.isEmpty();
                        return turSNFacetDefinitionFactory.fromFields(enabledFields, null).stream()
                                        .map(definition -> {
                                                TurSNSiteFieldExt facetEntry = definition.toFacetOrderingFieldExt();
                                                facetEntry.setFacetPosition(hasProfile
                                                                ? profilePositions.getOrDefault(definition.getId(),
                                                                                Integer.MAX_VALUE)
                                                                : definition.getPosition());
                                                return facetEntry;
                                        })
                                        .sorted(Comparator.comparing(TurSNSiteFieldExt::getFacetPosition,
                                                        Comparator.nullsLast(Comparator.naturalOrder()))
                                                        .thenComparing(
                                                                        facetEntry -> Optional
                                                                                        .ofNullable(facetEntry
                                                                                                        .getFacetName())
                                                                                        .orElse(facetEntry.getName()),
                                                                        Comparator.nullsLast(
                                                                                        String::compareToIgnoreCase)))
                                        .toList();
                });
        }

        private Map<String, Integer> resolveProfilePositions(TurSNSite site, String profileNameOrId) {
                Optional<TurProfile> profile = Optional.empty();
                if (profileNameOrId != null && !profileNameOrId.isBlank()) {
                        String key = profileNameOrId.trim();
                        profile = turProfileRepository.findByNameIgnoreCase(key);
                        if (profile.isEmpty()) {
                                profile = turProfileRepository.findById(key);
                        }
                }
                // The requested profile's ordering for this site, else the default profile's.
                Optional<TurProfileFacetOrdering> ordering = profile.flatMap(
                                p -> turProfileFacetOrderingRepository.findByTurProfileAndTurSNSiteWithItems(p, site));
                if (ordering.isEmpty()) {
                        ordering = turProfileRepository.findByDefaultProfileTrue().flatMap(
                                        def -> turProfileFacetOrderingRepository
                                                        .findByTurProfileAndTurSNSiteWithItems(def, site));
                }
                return ordering.map(TurSNFieldProcess::positionsByFacetId).orElseGet(Collections::emptyMap);
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
}
