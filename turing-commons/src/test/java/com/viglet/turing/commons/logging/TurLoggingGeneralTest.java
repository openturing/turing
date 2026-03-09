package com.viglet.turing.commons.logging;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Date;

import org.junit.jupiter.api.Test;

class TurLoggingGeneralTest {

    @Test
    void shouldBuildAndExposeGeneralLogFields() {
        Date now = new Date();
        TurLoggingGeneral log = TurLoggingGeneral.builder()
                .clusterNode("node-1")
                .level("INFO")
                .logger("com.viglet.Logger")
                .message("message")
                .stackTrace("stack")
                .date(now)
                .build();

        assertThat(log.getClusterNode()).isEqualTo("node-1");
        assertThat(log.getLevel()).isEqualTo("INFO");
        assertThat(log.getLogger()).isEqualTo("com.viglet.Logger");
        assertThat(log.getMessage()).isEqualTo("message");
        assertThat(log.getStackTrace()).isEqualTo("stack");
        assertThat(log.getDate()).isEqualTo(now);
    }
}
