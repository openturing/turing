package com.viglet.turing.sn.facet;

import java.util.Collections;
import java.util.Set;

import com.viglet.turing.persistence.dto.sn.field.TurSNSiteFieldExtDto;
import com.viglet.turing.persistence.model.sn.field.TurSNSiteCustomFacetItem;
import com.viglet.turing.persistence.model.sn.field.TurSNSiteFacetFieldEnum;
import com.viglet.turing.persistence.model.sn.field.TurSNSiteFieldExt;
import com.viglet.turing.persistence.model.sn.field.TurSNSiteFieldExtFacet;

public interface TurSNFacetDefinition {
    String getId();

    String getName();

    String getLabel();

    Integer getPosition();

    TurSNSiteFacetFieldEnum getFacetType();

    TurSNSiteFacetFieldEnum getFacetItemType();

    TurSNSiteFieldExt getFieldExt();

    Set<TurSNSiteFieldExtFacet> getFacetLocales();

    default boolean isCustomFacet() {
        return false;
    }

    default Set<TurSNSiteCustomFacetItem> getItems() {
        return Collections.emptySet();
    }

    default Boolean getSecondaryFacet() {
        return getFieldExt().getSecondaryFacet();
    }

    default Boolean getShowAllFacetItems() {
        return getFieldExt().getShowAllFacetItems();
    }

    default TurSNSiteFieldExt toFacetOrderingFieldExt() {
        TurSNSiteFieldExt facetEntry = new TurSNSiteFieldExt();
        facetEntry.setId(getId());
        facetEntry.setName(getName());
        facetEntry.setFacetName(getLabel());
        facetEntry.setFacetPosition(getPosition());
        facetEntry.setFacet(1);
        facetEntry.setEnabled(1);
        facetEntry.setTurSNSite(getFieldExt().getTurSNSite());
        facetEntry.setType(getFieldExt().getType());
        facetEntry.setSnType(getFieldExt().getSnType());
        facetEntry.setSecondaryFacet(getSecondaryFacet());
        facetEntry.setShowAllFacetItems(getShowAllFacetItems());
        return facetEntry;
    }

    default TurSNSiteFieldExtDto toFacetFieldExtDto() {
        TurSNSiteFieldExtDto facetFieldExtDto = new TurSNSiteFieldExtDto(getFieldExt());
        facetFieldExtDto.setName(getName());
        facetFieldExtDto.setFacetType(getFacetType());
        facetFieldExtDto.setFacetItemType(getFacetItemType());
        facetFieldExtDto.setFacetName(getLabel());
        facetFieldExtDto.setFacetLocales(getFacetLocales());
        facetFieldExtDto.setSecondaryFacet(getSecondaryFacet());
        facetFieldExtDto.setShowAllFacetItems(getShowAllFacetItems());
        return facetFieldExtDto;
    }
}