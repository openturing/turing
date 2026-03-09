package com.viglet.turing.api.sn.bean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Collections;

import org.junit.jupiter.api.Test;

class TurSolrFieldStatusTest {

    @Test
    void testBuilderAndGettersSetters() {
        TurSolrFieldCore core = new TurSolrFieldCore();
        core.setName("core1");

        TurSolrFieldStatus status = TurSolrFieldStatus.builder()
                .id("1")
                .externalId("ext1")
                .name("field_status")
                .facetIsCorrect(true)
                .cores(Collections.singletonList(core))
                .correct(true)
                .build();

        assertEquals("1", status.getId());
        assertEquals("ext1", status.getExternalId());
        assertEquals("field_status", status.getName());
        assertTrue(status.isFacetIsCorrect());
        assertEquals(1, status.getCores().size());
        assertEquals("core1", status.getCores().get(0).getName());
        assertTrue(status.isCorrect());

        status.setName("new_name");
        assertEquals("new_name", status.getName());
    }

    @Test
    void testNoArgsConstructor() {
        TurSolrFieldStatus status = new TurSolrFieldStatus();
        assertNull(status.getName());
    }
}
