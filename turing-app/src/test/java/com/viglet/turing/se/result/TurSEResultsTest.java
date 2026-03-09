package com.viglet.turing.se.result;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TurSEResultsTest {

    @Test
    void shouldBuildAndExposeSearchMetrics() {
        List<TurSEResult> results = List.of(TurSEResult.builder().fields(Map.of("title", "Doc")).build());

        TurSEResults searchResults = TurSEResults.builder()
                .numFound(10)
                .start(0)
                .limit(10)
                .pageCount(1)
                .currentPage(1)
                .results(results)
                .qTime(33)
                .elapsedTime(40)
                .queryString("hello")
                .sort("score desc")
                .facetResults(List.of())
                .groups(List.of())
                .similarResults(List.of())
                .build();

        assertEquals(33, searchResults.getQTime());
        assertEquals(40, searchResults.getElapsedTime());
        assertEquals("hello", searchResults.getQueryString());
        assertEquals("score desc", searchResults.getSort());
        assertEquals(10, searchResults.getNumFound());
        assertTrue(searchResults.toString().contains("queryString='hello'"));
    }
}
