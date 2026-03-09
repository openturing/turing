package com.viglet.turing.client.sn.facet;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TurSNFacetFieldValueTest {

    @Test
    void shouldStoreFacetValueDataAndApiUrl() {
        TurSNFacetFieldValue value = new TurSNFacetFieldValue();
        value.setLabel("Article");
        value.setCount(12);
        value.setApiURL("http://localhost/search?fq=type:Article");

        assertThat(value.getLabel()).isEqualTo("Article");
        assertThat(value.getCount()).isEqualTo(12);
        assertThat(value.getApiURL()).contains("http://localhost/search?fq=type:Article");
    }
}
