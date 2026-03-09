package com.viglet.turing.sn;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;

import com.viglet.turing.persistence.model.sn.field.TurSNSiteFieldExt;
import com.viglet.turing.persistence.repository.sn.TurSNSiteRepository;
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

        public TurSNFieldProcess(TurSNSiteRepository turSNSiteRepository,
                        TurSNSiteFieldExtRepository turSNSiteFieldExtRepository,
                        TurSNFacetDefinitionFactory turSNFacetDefinitionFactory) {
                this.turSNSiteRepository = turSNSiteRepository;
                this.turSNSiteFieldExtRepository = turSNSiteFieldExtRepository;
                this.turSNFacetDefinitionFactory = turSNFacetDefinitionFactory;
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
                return getTurSNSiteFacetOrdering(snSiteId)
                                .map(facetDefinitions -> facetDefinitions.stream()
                                                .map(TurSNFacetDefinition::toFacetOrderingFieldExt)
                                                .toList());
        }
}
