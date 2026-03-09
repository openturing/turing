package com.viglet.turing.persistence.model.sn;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class TurSNSiteFacetRangeEnumTest {

    @Test
    void shouldContainExpectedRangeOptions() {
        assertEquals(4, TurSNSiteFacetRangeEnum.values().length);
        assertEquals(TurSNSiteFacetRangeEnum.DAY, TurSNSiteFacetRangeEnum.valueOf("DAY"));
        assertEquals(TurSNSiteFacetRangeEnum.MONTH, TurSNSiteFacetRangeEnum.valueOf("MONTH"));
        assertEquals(TurSNSiteFacetRangeEnum.YEAR, TurSNSiteFacetRangeEnum.valueOf("YEAR"));
    }
}
