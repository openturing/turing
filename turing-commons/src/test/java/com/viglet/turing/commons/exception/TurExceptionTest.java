package com.viglet.turing.commons.exception;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TurExceptionTest {

    @Test
    void shouldCreateFromMessage() {
        TurException ex = new TurException("error message");
        assertThat(ex.getMessage()).isEqualTo("error message");
    }

    @Test
    void shouldCreateFromCause() {
        RuntimeException cause = new RuntimeException("root");
        TurException ex = new TurException(cause);

        assertThat(ex.getCause()).isSameAs(cause);
    }
}
