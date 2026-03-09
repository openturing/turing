package com.viglet.turing.persistence.model.sn.field;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;

import org.junit.jupiter.api.Test;

class TurSNSiteCustomFacetItemTest {

    @Test
    void shouldBuildAndExposeAllProperties() {
        Instant start = Instant.parse("2026-03-01T00:00:00Z");
        Instant end = Instant.parse("2026-03-31T23:59:59Z");
        TurSNSiteCustomFacet parent = TurSNSiteCustomFacet.builder().id("facet-1").build();

        TurSNSiteCustomFacetItem item = TurSNSiteCustomFacetItem.builder()
                .id("item-1")
                .label("From 100 to 200")
                .position(2)
                .rangeStart(new BigDecimal("100.00"))
                .rangeEnd(new BigDecimal("200.00"))
                .rangeStartDate(start)
                .rangeEndDate(end)
                .turSNSiteCustomFacet(parent)
                .build();

        assertThat(item.getId()).isEqualTo("item-1");
        assertThat(item.getLabel()).isEqualTo("From 100 to 200");
        assertThat(item.getPosition()).isEqualTo(2);
        assertThat(item.getRangeStart()).isEqualByComparingTo("100.00");
        assertThat(item.getRangeEnd()).isEqualByComparingTo("200.00");
        assertThat(item.getRangeStartDate()).isEqualTo(start);
        assertThat(item.getRangeEndDate()).isEqualTo(end);
        assertThat(item.getTurSNSiteCustomFacet()).isSameAs(parent);
    }

    @Test
    void shouldSupportToBuilderMutation() {
        TurSNSiteCustomFacetItem original = TurSNSiteCustomFacetItem.builder()
                .id("item-1")
                .label("Old Label")
                .position(1)
                .build();

        TurSNSiteCustomFacetItem copy = original.toBuilder()
                .label("New Label")
                .position(3)
                .build();

        assertThat(copy.getId()).isEqualTo("item-1");
        assertThat(copy.getLabel()).isEqualTo("New Label");
        assertThat(copy.getPosition()).isEqualTo(3);
    }
}
