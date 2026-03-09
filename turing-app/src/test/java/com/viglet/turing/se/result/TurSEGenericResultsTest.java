package com.viglet.turing.se.result;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

class TurSEGenericResultsTest {

    @Test
    void shouldConstructAndExposePaginationFields() {
        List<TurSEResult> results = List.of(TurSEResult.builder().fields(Map.of("id", "1")).build());
        TurSEGenericResults genericResults = new TurSEGenericResults(50, 10, 10, 5, 2, results);

        assertEquals(50, genericResults.getNumFound());
        assertEquals(10, genericResults.getStart());
        assertEquals(10, genericResults.getLimit());
        assertEquals(5, genericResults.getPageCount());
        assertEquals(2, genericResults.getCurrentPage());
        assertEquals(1, genericResults.getResults().size());
    }
}
