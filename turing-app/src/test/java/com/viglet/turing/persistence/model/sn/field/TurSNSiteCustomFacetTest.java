package com.viglet.turing.persistence.model.sn.field;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;

import org.junit.jupiter.api.Test;

class TurSNSiteCustomFacetTest {

    @Test
    void shouldKeepBuilderDefaults() {
        TurSNSiteCustomFacet facet = TurSNSiteCustomFacet.builder().build();

        assertThat(facet.getFacetType()).isEqualTo(TurSNSiteFacetFieldEnum.DEFAULT);
        assertThat(facet.getFacetItemType()).isEqualTo(TurSNSiteFacetFieldEnum.DEFAULT);
        assertThat(facet.getLabel()).isNotNull().isEmpty();
        assertThat(facet.getItems()).isNotNull().isEmpty();
    }

    @Test
    void shouldSetItemsAndBackReference() {
        TurSNSiteCustomFacet facet = TurSNSiteCustomFacet.builder().id("facet-1").build();
        TurSNSiteCustomFacetItem itemOne = TurSNSiteCustomFacetItem.builder().id("i1").label("One").build();
        TurSNSiteCustomFacetItem itemTwo = TurSNSiteCustomFacetItem.builder().id("i2").label("Two").build();

        facet.setItems(Set.of(itemOne, itemTwo));

        assertThat(facet.getItems()).hasSize(2);
        assertThat(itemOne.getTurSNSiteCustomFacet()).isSameAs(facet);
        assertThat(itemTwo.getTurSNSiteCustomFacet()).isSameAs(facet);
    }

    @Test
    void shouldClearItemsWhenNullAssigned() {
        TurSNSiteCustomFacet facet = TurSNSiteCustomFacet.builder().build();
        TurSNSiteCustomFacetItem item = TurSNSiteCustomFacetItem.builder().id("i1").build();
        facet.setItems(Set.of(item));

        facet.setItems(null);

        assertThat(facet.getItems()).isEmpty();
    }
}
