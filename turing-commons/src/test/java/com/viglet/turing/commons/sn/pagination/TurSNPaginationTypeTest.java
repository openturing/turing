package com.viglet.turing.commons.sn.pagination;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TurSNPaginationTypeTest {

    @Test
    void shouldExposeExpectedTypeStrings() {
        assertThat(TurSNPaginationType.FIRST).hasToString("FIRST");
        assertThat(TurSNPaginationType.LAST).hasToString("LAST");
        assertThat(TurSNPaginationType.PREVIOUS).hasToString("PREVIOUS");
        assertThat(TurSNPaginationType.NEXT).hasToString("NEXT");
        assertThat(TurSNPaginationType.CURRENT).hasToString("CURRENT");
        assertThat(TurSNPaginationType.PAGE).hasToString("PAGE");
    }
}
