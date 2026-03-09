package com.viglet.turing.persistence.model.sn;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class TurSNSiteFacetEnumTest {

    @Test
    void shouldContainExpectedFacetOperators() {
        assertArrayEquals(new TurSNSiteFacetEnum[] { TurSNSiteFacetEnum.AND, TurSNSiteFacetEnum.OR },
                TurSNSiteFacetEnum.values());
        assertEquals(TurSNSiteFacetEnum.AND, TurSNSiteFacetEnum.valueOf("AND"));
    }
}
