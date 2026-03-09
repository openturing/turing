package com.viglet.turing.api.sn.graphql;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.Collections;

import org.junit.jupiter.api.Test;

class TurSNSearchParamsInputTest {

    @Test
    void testGettersAndSetters() {
        TurSNSearchParamsInput params = new TurSNSearchParamsInput();

        // Check defaults
        assertEquals("*", params.getQ());
        assertEquals(1, params.getP());
        assertEquals("NONE", params.getFqOp());
        assertEquals("NONE", params.getFqiOp());
        assertEquals("relevance", params.getSort());
        assertEquals(-1, params.getRows());
        assertEquals(1, params.getNfpr());

        // Test setters
        params.setQ("testQuery");
        params.setP(2);
        params.setFq(Collections.singletonList("fq1"));
        params.setFqAnd(Collections.singletonList("fqAnd1"));
        params.setFqOr(Collections.singletonList("fqOr1"));
        params.setFqOp("AND");
        params.setFqiOp("OR");
        params.setSort("date");
        params.setRows(10);
        params.setLocale("en_US");
        params.setFl(Collections.singletonList("field1"));
        params.setGroup("group1");
        params.setNfpr(5);

        // Check assigned values
        assertEquals("testQuery", params.getQ());
        assertEquals(2, params.getP());
        assertEquals("fq1", params.getFq().get(0));
        assertEquals("fqAnd1", params.getFqAnd().get(0));
        assertEquals("fqOr1", params.getFqOr().get(0));
        assertEquals("AND", params.getFqOp());
        assertEquals("OR", params.getFqiOp());
        assertEquals("date", params.getSort());
        assertEquals(10, params.getRows());
        assertEquals("en_US", params.getLocale());
        assertEquals("field1", params.getFl().get(0));
        assertEquals("group1", params.getGroup());
        assertEquals(5, params.getNfpr());

        // Test toString
        assertNotNull(params.toString());
    }
}
