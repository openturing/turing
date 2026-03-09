package com.viglet.turing.client.sn.job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.Objects;

import org.junit.jupiter.api.Test;

import com.viglet.turing.commons.se.field.TurSEFieldType;

class TurSNJobAttributeSpecTest {

    @Test
    void shouldBuildAndRenderReadableToString() {
        TurSNJobAttributeSpec spec = new TurSNJobAttributeSpec();
        spec.setName("title");
        spec.setType(TurSEFieldType.TEXT);
        spec.setMandatory(true);
        spec.setMultiValued(false);
        spec.setDescription("Document title");
        spec.setFacet(true);
        spec.setFacetName(Map.of("en", "Title"));

        assertThat(spec.getName()).isEqualTo("title");
        assertThat(spec.getType()).isEqualTo(TurSEFieldType.TEXT);
        assertThat(spec.isMandatory()).isTrue();
        assertThat(spec.isFacet()).isTrue();
        String specText = Objects.toString(spec.toString(), "");
        assertTrue(specText.contains("name='title'"));
        assertTrue(specText.contains("Document title"));
    }
}
