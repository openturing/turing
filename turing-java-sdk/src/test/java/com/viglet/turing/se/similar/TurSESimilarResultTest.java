package com.viglet.turing.se.similar;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TurSESimilarResultTest {

    @Test
    void shouldStoreAndExposeSimilarResultFields() {
        TurSESimilarResult result = new TurSESimilarResult();
        result.setId("1");
        result.setTitle("Title");
        result.setType("article");
        result.setUrl("http://localhost/1");

        assertThat(result.getId()).isEqualTo("1");
        assertThat(result.getTitle()).isEqualTo("Title");
        assertThat(result.getType()).isEqualTo("article");
        assertThat(result.getUrl()).isEqualTo("http://localhost/1");
    }
}
