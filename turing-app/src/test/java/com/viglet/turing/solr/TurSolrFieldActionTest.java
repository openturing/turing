package com.viglet.turing.solr;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class TurSolrFieldActionTest {

    @Test
    void addShouldReturnCorrectAction() {
        assertEquals("add-field", TurSolrFieldAction.ADD.getSolrAction());
    }

    @Test
    void replaceShouldReturnCorrectAction() {
        assertEquals("replace-field", TurSolrFieldAction.REPLACE.getSolrAction());
    }

    @Test
    void deleteShouldReturnCorrectAction() {
        assertEquals("delete-field", TurSolrFieldAction.DELETE.getSolrAction());
    }

    @Test
    void addCopyShouldReturnCorrectAction() {
        assertEquals("add-copy-field", TurSolrFieldAction.ADD_COPY.getSolrAction());
    }

    @Test
    void deleteCopyShouldReturnCorrectAction() {
        assertEquals("delete-copy-field", TurSolrFieldAction.DELETE_COPY.getSolrAction());
    }

    @Test
    void allValuesShouldBePresent() {
        assertEquals(5, TurSolrFieldAction.values().length);
    }
}
