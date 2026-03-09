package com.viglet.turing.api.sn.bean;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TurSNSiteFacetOrderingDtoTest {

    @Test
    void shouldSupportChainableSetters() {
        TurSNSiteFacetOrderingDto dto = new TurSNSiteFacetOrderingDto()
                .setId("facet-1")
                .setName("price")
                .setFacetName("Price")
                .setFacetPosition(10)
                .setFieldExtId("field-1")
                .setFieldExtName("Price Field")
                .setCustomFacet(true);

        assertThat(dto.getId()).isEqualTo("facet-1");
        assertThat(dto.getName()).isEqualTo("price");
        assertThat(dto.getFacetName()).isEqualTo("Price");
        assertThat(dto.getFacetPosition()).isEqualTo(10);
        assertThat(dto.getFieldExtId()).isEqualTo("field-1");
        assertThat(dto.getFieldExtName()).isEqualTo("Price Field");
        assertThat(dto.getCustomFacet()).isTrue();
    }

    @Test
    void shouldAllowNullAssignments() {
        TurSNSiteFacetOrderingDto dto = new TurSNSiteFacetOrderingDto()
                .setId(null)
                .setName(null)
                .setFacetName(null)
                .setFacetPosition(null)
                .setFieldExtId(null)
                .setFieldExtName(null)
                .setCustomFacet(null);

        assertThat(dto.getId()).isNull();
        assertThat(dto.getName()).isNull();
        assertThat(dto.getFacetName()).isNull();
        assertThat(dto.getFacetPosition()).isNull();
        assertThat(dto.getFieldExtId()).isNull();
        assertThat(dto.getFieldExtName()).isNull();
        assertThat(dto.getCustomFacet()).isNull();
    }
}
