package com.viglet.turing.commons.sn.search;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TurSNFilterQueryOperatorTest {

    @Test
    void shouldExposeExpectedOperatorStrings() {
        assertThat(TurSNFilterQueryOperator.AND).hasToString("AND");
        assertThat(TurSNFilterQueryOperator.OR).hasToString("OR");
        assertThat(TurSNFilterQueryOperator.NONE).hasToString("NONE");
    }
}
