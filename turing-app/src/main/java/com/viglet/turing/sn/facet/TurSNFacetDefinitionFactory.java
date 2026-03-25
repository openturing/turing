package com.viglet.turing.sn.facet;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.viglet.turing.persistence.model.sn.field.TurSNSiteCustomFacet;
import com.viglet.turing.persistence.model.sn.field.TurSNSiteFieldExt;
import com.viglet.turing.persistence.model.sn.field.TurSNSiteFieldExtFacet;
import com.viglet.turing.persistence.repository.sn.field.TurSNSiteCustomFacetRepository;

@Component
public class TurSNFacetDefinitionFactory {

    private final TurSNSiteCustomFacetRepository turSNSiteCustomFacetRepository;

    public TurSNFacetDefinitionFactory(TurSNSiteCustomFacetRepository turSNSiteCustomFacetRepository) {
        this.turSNSiteCustomFacetRepository = turSNSiteCustomFacetRepository;
    }

    public List<TurSNFacetDefinition> fromFields(List<TurSNSiteFieldExt> fields, Locale locale) {
        return fromFields(fields, locale,
                field -> Optional.ofNullable(field.getFacetLocales()).orElse(Collections.emptySet()));
    }

    public List<TurSNFacetDefinition> fromFields(List<TurSNSiteFieldExt> fields,
            Locale locale,
            Function<TurSNSiteFieldExt, Set<TurSNSiteFieldExtFacet>> fieldFacetLocaleProvider) {
        Map<String, List<TurSNSiteCustomFacet>> customFacetsByFieldId = loadCustomFacetsByFieldId(fields);
        return Optional.ofNullable(fields).orElse(Collections.emptyList()).stream()
                .flatMap(field -> fromField(field, locale, fieldFacetLocaleProvider.apply(field),
                        customFacetsByFieldId.getOrDefault(field.getId(), Collections.emptyList())).stream())
                .toList();
    }

    public List<TurSNFacetDefinition> fromField(TurSNSiteFieldExt fieldExt,
            Locale locale,
            Set<TurSNSiteFieldExtFacet> fieldFacetLocales) {
        List<TurSNSiteCustomFacet> customFacets = turSNSiteCustomFacetRepository
                .findByFieldExtWithDetails(fieldExt);
        return fromField(fieldExt, locale, fieldFacetLocales, customFacets);
    }

    public List<TurSNFacetDefinition> fromField(TurSNSiteFieldExt fieldExt,
            Locale locale,
            Set<TurSNSiteFieldExtFacet> fieldFacetLocales,
            Collection<TurSNSiteCustomFacet> customFacets) {
        List<TurSNFacetDefinition> facetDefinitions = new ArrayList<>();
        if (fieldExt.getFacet() == 1) {
            facetDefinitions.add(new TurSNFieldFacetDefinition(fieldExt, fieldFacetLocales));
        }
        Optional.ofNullable(customFacets).orElse(Collections.emptyList())
                .forEach(customFacet -> facetDefinitions
                        .add(new TurSNCustomFacetDefinition(fieldExt, customFacet, locale)));
        return facetDefinitions;
    }

    private Map<String, List<TurSNSiteCustomFacet>> loadCustomFacetsByFieldId(
            List<TurSNSiteFieldExt> fields) {
        if (fields == null || fields.isEmpty()) {
            return Collections.emptyMap();
        }
        return turSNSiteCustomFacetRepository.findByFieldExtsWithDetails(fields).stream()
                .collect(Collectors.groupingBy(cf -> cf.getTurSNSiteFieldExt().getId()));
    }
}