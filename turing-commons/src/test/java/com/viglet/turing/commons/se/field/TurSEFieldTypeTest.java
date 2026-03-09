package com.viglet.turing.commons.se.field;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TurSEFieldTypeTest {

    @Test
    void shouldExposeStableIds() {
        assertThat(TurSEFieldType.INT.id()).isEqualTo(1);
        assertThat(TurSEFieldType.LONG.id()).isEqualTo(2);
        assertThat(TurSEFieldType.STRING.id()).isEqualTo(3);
        assertThat(TurSEFieldType.TEXT.id()).isEqualTo(4);
        assertThat(TurSEFieldType.ARRAY.id()).isEqualTo(5);
        assertThat(TurSEFieldType.DATE.id()).isEqualTo(6);
        assertThat(TurSEFieldType.BOOL.id()).isEqualTo(7);
    }
}
