package com.viglet.turing.se.facet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class TurSEFacetResultTest {

    @Test
    void shouldAddFacetAttributesInMap() {
        TurSEFacetResult facetResult = new TurSEFacetResult();
        facetResult.setFacet("category");
        facetResult.setFacetPosition(1);

        facetResult.add("news", new TurSEFacetResultAttr("news", 5));
        facetResult.add("blog", new TurSEFacetResultAttr("blog", 2));

        assertEquals(2, facetResult.getTurSEFacetResultAttr().size());
        assertEquals(5, facetResult.getTurSEFacetResultAttr().get("news").getCount());
        assertEquals("category", facetResult.getFacet());
        assertEquals(1, facetResult.getFacetPosition());
    }

    @Test
    void shouldRenderToStringWithFacetName() {
        TurSEFacetResult facetResult = new TurSEFacetResult();
        facetResult.setFacet("author");

        String value = facetResult.toString();
        assertTrue(value.contains("author"));
    }
}
