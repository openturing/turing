package com.viglet.turing.persistence.model.sn.source;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class TurSNSourceTypeTest {

    @Test
    void shouldStoreAndExposeIdNameAndDescription() {
        TurSNSourceType sourceType = new TurSNSourceType();
        sourceType.setId("db");
        sourceType.setName("Database");
        sourceType.setDescription("Data source from RDBMS");

        assertEquals("db", sourceType.getId());
        assertEquals("Database", sourceType.getName());
        assertEquals("Data source from RDBMS", sourceType.getDescription());
    }
}
