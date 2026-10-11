package com.viglet.turing.sn.facet;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;

import org.junit.jupiter.api.Test;

import com.viglet.turing.commons.se.field.TurSEFieldType;
import com.viglet.turing.persistence.model.sn.field.TurSNSiteCustomFacetItem;
import com.viglet.turing.persistence.model.sn.field.TurSNSiteCustomFacetOperatorEnum;

/**
 * T1083 / T1075 — the filter query a custom facet item stands for, which both
 * engines read.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
class TurSNCustomFacetFilterQueryTest {

    private static TurSNSiteCustomFacetItem item(TurSNSiteCustomFacetOperatorEnum operator) {
        TurSNSiteCustomFacetItem item = new TurSNSiteCustomFacetItem();
        item.setLabel("x");
        item.setOperator(operator);
        return item;
    }

    @Test
    void aNumericItemIsARange() {
        TurSNSiteCustomFacetItem item = item(TurSNSiteCustomFacetOperatorEnum.BETWEEN_INCLUSIVE);
        item.setRangeStart(new BigDecimal("1000"));
        item.setRangeEnd(new BigDecimal("3000"));

        assertThat(TurSNCustomFacetFilterQuery.of("salary", TurSEFieldType.INT, item))
                .isEqualTo("salary:[1000 TO 3000]");
    }

    @Test
    void aFixedDateItemUsesItsInstants() {
        TurSNSiteCustomFacetItem item = item(TurSNSiteCustomFacetOperatorEnum.GREATER_THAN_OR_EQUAL);
        item.setRangeStartDate(Instant.parse("2026-10-01T00:00:00Z"));

        assertThat(TurSNCustomFacetFilterQuery.of("publishedAt", TurSEFieldType.DATE, item))
                .isEqualTo("publishedAt:[2026-10-01T00:00:00Z TO *]");
    }

    @Test
    void aRelativeBoundWinsOverAFixedOne() {
        TurSNSiteCustomFacetItem item = item(TurSNSiteCustomFacetOperatorEnum.GREATER_THAN_OR_EQUAL);
        item.setRangeStartDate(Instant.parse("2026-10-01T00:00:00Z"));
        item.setRangeStartExpression("NOW-7DAYS");

        assertThat(TurSNCustomFacetFilterQuery.of("publishedAt", TurSEFieldType.DATE, item))
                .isEqualTo("publishedAt:[NOW-7DAYS TO *]");
    }

    @Test
    void dateMathIsRecognisedByItsNowAnchor() {
        assertThat(TurSNCustomFacetFilterQuery.isDateMath("now-1day")).isTrue();
        assertThat(TurSNCustomFacetFilterQuery.isDateMath("2026-10-01")).isFalse();
        assertThat(TurSNCustomFacetFilterQuery.isDateMath(null)).isFalse();
    }
}
