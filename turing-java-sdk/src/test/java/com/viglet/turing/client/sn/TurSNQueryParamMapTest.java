package com.viglet.turing.client.sn;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

class TurSNQueryParamMapTest {

    @Test
    void shouldKeepInsertionOrderAndStoreMultipleValues() {
        TurSNQueryParamMap queryParamMap = new TurSNQueryParamMap();

        queryParamMap.put("q", List.of("cloud"));
        queryParamMap.put("fq", List.of("type:doc", "lang:en"));

        assertThat(queryParamMap)
                .containsEntry("q", List.of("cloud"))
                .containsEntry("fq", List.of("type:doc", "lang:en"));
        assertThat(queryParamMap.keySet()).containsExactly("q", "fq");
    }
}
