package com.viglet.turing.persistence.model.system;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class TurConfigVarTest {

    @Test
    void shouldStoreAndExposeIdPathAndValue() {
        TurConfigVar configVar = new TurConfigVar();
        configVar.setId("FIRST_TIME");
        configVar.setPath("/system");
        configVar.setValue("true");

        assertEquals("FIRST_TIME", configVar.getId());
        assertEquals("/system", configVar.getPath());
        assertEquals("true", configVar.getValue());
    }
}
