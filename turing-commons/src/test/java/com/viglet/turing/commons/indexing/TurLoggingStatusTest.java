package com.viglet.turing.commons.indexing;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TurLoggingStatusTest {

    @Test
    void shouldContainSuccessAndErrorOnly() {
        assertThat(TurLoggingStatus.values()).containsExactly(TurLoggingStatus.SUCCESS, TurLoggingStatus.ERROR);
    }
}
