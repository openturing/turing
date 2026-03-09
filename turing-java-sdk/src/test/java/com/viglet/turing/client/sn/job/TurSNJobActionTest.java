package com.viglet.turing.client.sn.job;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TurSNJobActionTest {

    @Test
    void shouldContainExpectedEnumValues() {
        assertThat(TurSNJobAction.values())
                .containsExactly(TurSNJobAction.CREATE, TurSNJobAction.DELETE, TurSNJobAction.COMMIT);
    }
}
