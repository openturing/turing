package com.viglet.turing.commons.se.similar;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TurSESimilarResultTest {

    @Test
    void shouldBuildAndExposeFields() {
        TurSESimilarResult result = TurSESimilarResult.builder()
                .id("1")
                .title("Title")
                .type("article")
                .url("http://localhost/1")
                .build();

        assertThat(result.getId()).isEqualTo("1");
        assertThat(result.getTitle()).isEqualTo("Title");
        assertThat(result.getType()).isEqualTo("article");
        assertThat(result.getUrl()).isEqualTo("http://localhost/1");
        assertThat(result.toString()).contains("Title").contains("article");
    }
}
