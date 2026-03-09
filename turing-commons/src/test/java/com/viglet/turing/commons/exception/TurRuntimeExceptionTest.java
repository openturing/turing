package com.viglet.turing.commons.exception;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TurRuntimeExceptionTest {

    @Test
    void shouldCreateFromMessage() {
        TurRuntimeException ex = new TurRuntimeException("runtime");
        assertThat(ex.getMessage()).isEqualTo("runtime");
    }

    @Test
    void shouldCreateFromCause() {
        IllegalStateException cause = new IllegalStateException("state");
        TurRuntimeException ex = new TurRuntimeException(cause);

        assertThat(ex.getCause()).isSameAs(cause);
    }

    @Test
    void shouldCreateFromMessageAndException() {
        Exception cause = new Exception("cause");
        TurRuntimeException ex = new TurRuntimeException("runtime", cause);

        assertThat(ex.getMessage()).isEqualTo("runtime");
        assertThat(ex.getCause()).isSameAs(cause);
    }
}
