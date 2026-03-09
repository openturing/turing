package com.viglet.turing.persistence.model.sn.merge;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TurSNSiteMergeProvidersFieldTest {

    private TurSNSiteMergeProvidersField field;

    @BeforeEach
    void setUp() {
        field = new TurSNSiteMergeProvidersField();
    }

    @Test
    void shouldSetAndGetAllProperties() {
        TurSNSiteMergeProviders provider = new TurSNSiteMergeProviders();

        field.setId("field-id");
        field.setName("title");
        field.setTurSNSiteMergeProviders(provider);

        assertThat(field.getId()).isEqualTo("field-id");
        assertThat(field.getName()).isEqualTo("title");
        assertThat(field.getTurSNSiteMergeProviders()).isEqualTo(provider);
    }

    @Test
    void shouldExposeDefaultValuesOnNoArgsConstructor() {
        assertThat(field.getId()).isNull();
        assertThat(field.getName()).isNull();
        assertThat(field.getTurSNSiteMergeProviders()).isNull();
    }
}
