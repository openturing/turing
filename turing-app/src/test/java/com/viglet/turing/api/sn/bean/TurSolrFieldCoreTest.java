package com.viglet.turing.api.sn.bean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class TurSolrFieldCoreTest {

    @Test
    void testBuilderAndGettersSetters() {
        TurSolrFieldCore core = TurSolrFieldCore.builder()
                .name("field_name")
                .exists(true)
                .type("text")
                .typeIsCorrect(true)
                .multiValued(false)
                .multiValuedIsCorrect(true)
                .correct(true)
                .build();

        assertEquals("field_name", core.getName());
        assertTrue(core.isExists());
        assertEquals("text", core.getType());
        assertTrue(core.isTypeIsCorrect());
        assertFalse(core.isMultiValued());
        assertTrue(core.isMultiValuedIsCorrect());
        assertTrue(core.isCorrect());

        core.setName("new_name");
        assertEquals("new_name", core.getName());
    }

    @Test
    void testNoArgsConstructor() {
        TurSolrFieldCore core = new TurSolrFieldCore();
        assertNull(core.getName());
    }
}
