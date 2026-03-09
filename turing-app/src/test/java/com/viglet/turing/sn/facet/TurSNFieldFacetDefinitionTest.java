package com.viglet.turing.sn.facet;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;

import org.junit.jupiter.api.Test;

import com.viglet.turing.persistence.model.sn.field.TurSNSiteFacetFieldEnum;
import com.viglet.turing.persistence.model.sn.field.TurSNSiteFieldExt;
import com.viglet.turing.persistence.model.sn.field.TurSNSiteFieldExtFacet;

class TurSNFieldFacetDefinitionTest {

    @Test
    void shouldApplyFallbacksWhenFacetMetadataIsMissing() {
        TurSNSiteFieldExt fieldExt = TurSNSiteFieldExt.builder()
                .id("field-1")
                .name("price")
                .facetName(" ")
                .facetPosition(0)
                .build();

        TurSNFieldFacetDefinition definition = new TurSNFieldFacetDefinition(fieldExt, null);

        assertThat(definition.getId()).isEqualTo("field-1");
        assertThat(definition.getName()).isEqualTo("price");
        assertThat(definition.getLabel()).isEqualTo("price");
        assertThat(definition.getPosition()).isEqualTo(Integer.MAX_VALUE);
        assertThat(definition.getFacetType()).isEqualTo(TurSNSiteFacetFieldEnum.DEFAULT);
        assertThat(definition.getFacetItemType()).isEqualTo(TurSNSiteFacetFieldEnum.DEFAULT);
        assertThat(definition.getFacetLocales()).isEmpty();
    }

    @Test
    void shouldUseConfiguredFacetMetadata() {
        TurSNSiteFieldExtFacet localeFacet = TurSNSiteFieldExtFacet.builder().label("Preço").build();
        TurSNSiteFieldExt fieldExt = TurSNSiteFieldExt.builder()
                .id("field-2")
                .name("price")
                .facetName("Price")
                .facetPosition(4)
                .facetType(TurSNSiteFacetFieldEnum.AND)
                .facetItemType(TurSNSiteFacetFieldEnum.OR)
                .build();

        TurSNFieldFacetDefinition definition = new TurSNFieldFacetDefinition(fieldExt, Set.of(localeFacet));

        assertThat(definition.getLabel()).isEqualTo("Price");
        assertThat(definition.getPosition()).isEqualTo(4);
        assertThat(definition.getFacetType()).isEqualTo(TurSNSiteFacetFieldEnum.AND);
        assertThat(definition.getFacetItemType()).isEqualTo(TurSNSiteFacetFieldEnum.OR);
        assertThat(definition.getFacetLocales()).containsExactly(localeFacet);
    }
}
