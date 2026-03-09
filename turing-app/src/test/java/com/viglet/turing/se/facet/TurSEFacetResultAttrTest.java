package com.viglet.turing.se.facet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class TurSEFacetResultAttrTest {

    @Test
    void shouldConstructAndExposeFields() {
        TurSEFacetResultAttr attr = new TurSEFacetResultAttr("news", 7);

        assertEquals("news", attr.getAttribute());
        assertEquals(7, attr.getCount());
    }

    @Test
    void shouldRenderMeaningfulToString() {
        TurSEFacetResultAttr attr = new TurSEFacetResultAttr("blog", 3);

        String value = attr.toString();
        assertTrue(value.contains("blog"));
        assertTrue(value.contains("3"));
    }
}
