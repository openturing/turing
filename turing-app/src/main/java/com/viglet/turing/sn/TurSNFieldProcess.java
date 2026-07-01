package com.viglet.turing.sn;

import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;

import com.viglet.turing.persistence.model.sn.TurSNSite;
import com.viglet.turing.persistence.model.sn.field.TurSNSiteFacetProfile;
import com.viglet.turing.persistence.model.sn.field.TurSNSiteFieldExt;
import com.viglet.turing.persistence.repository.sn.TurSNSiteRepository;
import com.viglet.turing.persistence.repository.sn.field.TurSNSiteFacetProfileRepository;
import com.viglet.turing.persistence.repository.sn.field.TurSNSiteFieldExtRepository;
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
        private final TurSNSiteFacetProfileRepository turSNSiteFacetProfileRepository;

        public TurSNFieldProcess(TurSNSiteRepository turSNSiteRepository,
                        TurSNSiteFieldExtRepository turSNSiteFieldExtRepository,
                        TurSNFacetDefinitionFactory turSNFacetDefinitionFactory,
                        TurSNSiteFacetProfileRepository turSNSiteFacetProfileRepository) {
                this.turSNSiteRepository = turSNSiteRepository;
                this.turSNSiteFieldExtRepository = turSNSiteFieldExtRepository;
                this.turSNFacetDefinitionFactory = turSNFacetDefinitionFactory;
                this.turSNSiteFacetProfileRepository = turSNSiteFacetProfileRepository;
        }

        @NotNull
        public Optional<List<TurSNFacetDefinition>> getTurSNSiteFacetOrdering(String snSiteId) {
                return turSNSiteRepository.findById(snSiteId)
                                .map(turSNSite -> {
                                        List<TurSNSiteFieldExt> enabledFields = turSNSiteFieldExtRepository
                                                        .findByTurSNSiteAndEnabled(turSNSite, 1);

                                        return turSNFacetDefinitionFactory.fromFields(enabledFields, null).stream()
                                                        .sorted(Comparator.comparing(TurSNFacetDefinition::getPosition)
                                                                        .thenComparing(TurSNFacetDefinition::getLabel,
                                                                                        Comparator.nullsLast(
                                                                                                        String::compareToIgnoreCase)))
                                                        .toList();
                                });
        }

        @NotNull
        public Optional<List<TurSNSiteFieldExt>> getTurSNSiteFieldOrdering(String snSiteId) {
                return getTurSNSiteFieldOrdering(snSiteId, null);
        }

        /**
         * Facet ordering resolved by a facet profile (by name or id). When the
         * profile is absent/blank or not found, the site's default profile is used;
         * if there is no profile at all, the legacy {@code facetPosition} is applied.
         *
         * @param snSiteId     the SN site id
         * @param facetProfile the requested profile name or id (may be {@code null})
         * @since 2026.2.7
         */
        @NotNull
        public Optional<List<TurSNSiteFieldExt>> getTurSNSiteFieldOrdering(String snSiteId,
                        String facetProfile) {
                return turSNSiteRepository.findById(snSiteId).map(turSNSite -> {
                        List<TurSNSiteFieldExt> enabledFields = turSNSiteFieldExtRepository
                                        .findByTurSNSiteAndEnabled(turSNSite, 1);
                        Map<String, Integer> profilePositions = resolveProfilePositions(turSNSite, facetProfile);
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

        private Map<String, Integer> resolveProfilePositions(TurSNSite site, String facetProfile) {
                Optional<TurSNSiteFacetProfile> profile = Optional.empty();
                if (facetProfile != null && !facetProfile.isBlank()) {
                        String key = facetProfile.trim();
                        profile = turSNSiteFacetProfileRepository.findByTurSNSiteAndNameIgnoreCase(site, key);
                        if (profile.isEmpty()) {
                                profile = turSNSiteFacetProfileRepository.findById(key)
                                                .filter(candidate -> candidate.getTurSNSite() != null
                                                                && candidate.getTurSNSite().getId()
                                                                                .equals(site.getId()));
                        }
                }
                if (profile.isEmpty()) {
                        profile = turSNSiteFacetProfileRepository.findByTurSNSiteAndDefaultProfileTrue(site);
                }
                return profile.map(TurSNFieldProcess::positionsByFacetId).orElseGet(Collections::emptyMap);
        }

        private static Map<String, Integer> positionsByFacetId(TurSNSiteFacetProfile profile) {
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
}
