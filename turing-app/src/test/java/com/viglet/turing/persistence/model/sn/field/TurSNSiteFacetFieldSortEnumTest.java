package com.viglet.turing.persistence.model.sn.field;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class TurSNSiteFacetFieldSortEnumTest {

    @Test
    void shouldContainExpectedFacetFieldSortValues() {
        assertArrayEquals(
                new TurSNSiteFacetFieldSortEnum[] { TurSNSiteFacetFieldSortEnum.DEFAULT,
                        TurSNSiteFacetFieldSortEnum.COUNT, TurSNSiteFacetFieldSortEnum.ALPHABETICAL },
                TurSNSiteFacetFieldSortEnum.values());
        assertEquals(TurSNSiteFacetFieldSortEnum.COUNT, TurSNSiteFacetFieldSortEnum.valueOf("COUNT"));
    }
}
