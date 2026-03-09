package com.viglet.turing.sn.facet;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

import org.springframework.stereotype.Component;

import com.viglet.turing.persistence.model.sn.field.TurSNSiteFieldExt;
import com.viglet.turing.persistence.model.sn.field.TurSNSiteFieldExtFacet;

@Component
public class TurSNFacetDefinitionFactory {

    public List<TurSNFacetDefinition> fromFields(List<TurSNSiteFieldExt> fields, Locale locale) {
        return fromFields(fields, locale,
                field -> Optional.ofNullable(field.getFacetLocales()).orElse(Collections.emptySet()));
    }

    public List<TurSNFacetDefinition> fromFields(List<TurSNSiteFieldExt> fields,
            Locale locale,
            Function<TurSNSiteFieldExt, Set<TurSNSiteFieldExtFacet>> fieldFacetLocaleProvider) {
        return Optional.ofNullable(fields).orElse(Collections.emptyList()).stream()
                .flatMap(field -> fromField(field, locale, fieldFacetLocaleProvider.apply(field)).stream())
                .toList();
    }

    public List<TurSNFacetDefinition> fromField(TurSNSiteFieldExt fieldExt,
            Locale locale,
            Set<TurSNSiteFieldExtFacet> fieldFacetLocales) {
        List<TurSNFacetDefinition> facetDefinitions = new ArrayList<>();
        if (fieldExt.getFacet() == 1) {
            facetDefinitions.add(new TurSNFieldFacetDefinition(fieldExt, fieldFacetLocales));
        }
        Optional.ofNullable(fieldExt.getCustomFacets())
                .orElse(Collections.emptySet())
                .forEach(customFacet -> facetDefinitions
                        .add(new TurSNCustomFacetDefinition(fieldExt, customFacet, locale)));
        return facetDefinitions;
    }
}