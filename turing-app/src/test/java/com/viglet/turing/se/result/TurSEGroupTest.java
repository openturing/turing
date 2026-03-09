package com.viglet.turing.se.result;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TurSEGroupTest {

    @Test
    void shouldBuildGroupAndExposeNameAndInheritedFields() {
        List<TurSEResult> results = List.of(TurSEResult.builder().fields(Map.of("id", "1")).build());

        TurSEGroup group = TurSEGroup.builder()
                .name("group-a")
                .numFound(2)
                .start(0)
                .limit(10)
                .pageCount(1)
                .currentPage(1)
                .results(results)
                .build();

        assertEquals("group-a", group.getName());
        assertEquals(2, group.getNumFound());
        assertEquals(1, group.getResults().size());
    }
}
