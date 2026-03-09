package com.viglet.turing.client.sn.job;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.Test;

import com.viglet.turing.commons.se.field.TurSEFieldType;

class TurSNAttributeSpecTest {

    @Test
    void shouldBuildSubclassFieldsAndToString() {
        TurSNAttributeSpec spec = new TurSNAttributeSpec();
        spec.setName("content");
        spec.setType(TurSEFieldType.TEXT);
        spec.setMandatory(false);
        spec.setMultiValued(true);
        spec.setDescription("Main content");
        spec.setFacet(false);
        spec.setFacetName(Map.of());
        spec.setClassName("com.example.Content");

        assertThat(spec.getName()).isEqualTo("content");
        assertThat(spec.getClassName()).isEqualTo("com.example.Content");
        assertThat(spec.toString()).contains("className='com.example.Content'");
    }
}
