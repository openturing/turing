package com.viglet.turing.sn.facet;

import java.util.Collections;
import java.util.Optional;
import java.util.Set;

import com.viglet.turing.persistence.model.sn.field.TurSNSiteFacetFieldEnum;
import com.viglet.turing.persistence.model.sn.field.TurSNSiteFieldExt;
import com.viglet.turing.persistence.model.sn.field.TurSNSiteFieldExtFacet;

public class TurSNFieldFacetDefinition implements TurSNFacetDefinition {
    private final TurSNSiteFieldExt fieldExt;
    private final Set<TurSNSiteFieldExtFacet> facetLocales;

    public TurSNFieldFacetDefinition(TurSNSiteFieldExt fieldExt,
            Set<TurSNSiteFieldExtFacet> facetLocales) {
        this.fieldExt = fieldExt;
        this.facetLocales = Optional.ofNullable(facetLocales).orElse(Collections.emptySet());
    }

    @Override
    public String getId() {
        return fieldExt.getId();
    }

    @Override
    public String getName() {
        return fieldExt.getName();
    }

    @Override
    public String getLabel() {
        return hasText(fieldExt.getFacetName()) ? fieldExt.getFacetName() : fieldExt.getName();
    }

    @Override
    public Integer getPosition() {
        return Optional.ofNullable(fieldExt.getFacetPosition())
                .filter(position -> position > 0)
                .orElse(Integer.MAX_VALUE);
    }

    @Override
    public TurSNSiteFacetFieldEnum getFacetType() {
        return Optional.ofNullable(fieldExt.getFacetType()).orElse(TurSNSiteFacetFieldEnum.DEFAULT);
    }

    @Override
    public TurSNSiteFacetFieldEnum getFacetItemType() {
        return Optional.ofNullable(fieldExt.getFacetItemType())
                .orElse(TurSNSiteFacetFieldEnum.DEFAULT);
    }

    @Override
    public TurSNSiteFieldExt getFieldExt() {
        return fieldExt;
    }

    @Override
    public Set<TurSNSiteFieldExtFacet> getFacetLocales() {
        return facetLocales;
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}