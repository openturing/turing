package com.viglet.turing.spring.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TurAuditableTest {

    private static class TestAuditable extends TurAuditable<String> {
    }

    @Test
    void shouldExposeNullAuditFieldsByDefault() {
        TestAuditable auditable = new TestAuditable();

        assertThat(auditable.getCreatedBy()).isNull();
        assertThat(auditable.getCreationDate()).isNull();
        assertThat(auditable.getLastModifiedBy()).isNull();
        assertThat(auditable.getLastModifiedDate()).isNull();
    }
}
