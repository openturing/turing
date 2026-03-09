package com.viglet.turing.system;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;

import org.junit.jupiter.api.Test;

class TurGlobalDecimalSeparatorTest {

    @Test
    void shouldContainExpectedEnumValues() {
        assertThat(Arrays.asList(TurGlobalDecimalSeparator.values()))
                .containsExactly(TurGlobalDecimalSeparator.DOT, TurGlobalDecimalSeparator.COMMA);
    }

    @Test
    void shouldResolveByName() {
        assertThat(TurGlobalDecimalSeparator.valueOf("DOT")).isEqualTo(TurGlobalDecimalSeparator.DOT);
        assertThat(TurGlobalDecimalSeparator.valueOf("COMMA")).isEqualTo(TurGlobalDecimalSeparator.COMMA);
    }
}
