package com.viglet.turing.se.result;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.viglet.turing.se.facet.TurSEFacetResult;

class TurSEGroupAndResultsTest {

    @Test
    void shouldBuildGroupWithInheritedFields() {
        List<TurSEResult> results = List.of(TurSEResult.builder().fields(Map.of("title", "A")).build());

        TurSEGroup group = TurSEGroup.builder()
                .name("type:news")
                .numFound(3)
                .start(0)
                .limit(10)
                .pageCount(1)
                .currentPage(1)
                .results(results)
                .build();

        assertEquals("type:news", group.getName());
        assertEquals(3, group.getNumFound());
        assertEquals(1, group.getResults().size());
    }

    @Test
    void shouldBuildResultsAndRenderToStringWithKeyFields() {
        List<TurSEResult> results = List.of(TurSEResult.builder().fields(Map.of("title", "Doc")).build());
        List<TurSEFacetResult> facetResults = List.of(new TurSEFacetResult());
        List<TurSEGroup> groups = List.of(TurSEGroup.builder()
                .name("group-1")
                .numFound(1)
                .start(0)
                .limit(10)
                .pageCount(1)
                .currentPage(1)
                .results(results)
                .build());

        TurSEResults searchResults = TurSEResults.builder()
                .numFound(1)
                .start(0)
                .limit(10)
                .pageCount(1)
                .currentPage(1)
                .results(results)
                .qTime(12)
                .elapsedTime(18)
                .queryString("turing")
                .sort("score desc")
                .facetResults(facetResults)
                .groups(groups)
                .build();

        assertEquals("turing", searchResults.getQueryString());
        assertEquals(12, searchResults.getQTime());
        assertEquals(1, searchResults.getFacetResults().size());
        assertEquals(1, searchResults.getGroups().size());
        assertTrue(searchResults.toString().contains("queryString='turing'"));
        assertTrue(searchResults.toString().contains("qTime=12"));
    }
}
