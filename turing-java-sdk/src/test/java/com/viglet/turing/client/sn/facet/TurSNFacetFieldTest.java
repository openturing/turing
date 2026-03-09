package com.viglet.turing.client.sn.facet;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Collections;

import org.junit.jupiter.api.Test;

import com.viglet.turing.commons.se.field.TurSEFieldType;

class TurSNFacetFieldTest {

    @Test
    void shouldExposeFacetMetadataAndValueCount() {
        TurSNFacetFieldValueList values = new TurSNFacetFieldValueList(Collections.emptyList());
        TurSNFacetField facetField = new TurSNFacetField();

        facetField.setLabel("Type");
        facetField.setName("type");
        facetField.setDescription("Document Type");
        facetField.setMultiValued(true);
        facetField.setType(TurSEFieldType.TEXT);
        facetField.setValues(values);

        assertThat(facetField.getLabel()).isEqualTo("Type");
        assertThat(facetField.getName()).isEqualTo("type");
        assertThat(facetField.getDescription()).isEqualTo("Document Type");
        assertThat(facetField.isMultiValued()).isTrue();
        assertThat(facetField.getType()).isEqualTo(TurSEFieldType.TEXT);
        assertThat(facetField.getValueCount()).isZero();
    }
}
