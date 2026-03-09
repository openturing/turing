package com.viglet.turing.persistence.model.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class TurPrivilegeTest {

    @Test
    void shouldUseNameForEqualityHashCodeAndToString() {
        TurPrivilege p1 = new TurPrivilege("READ_PRIVILEGE");
        p1.setId("id-1");
        TurPrivilege p2 = new TurPrivilege("READ_PRIVILEGE");
        p2.setId("id-2");
        TurPrivilege p3 = new TurPrivilege("WRITE_PRIVILEGE");

        assertEquals(p1, p2);
        assertEquals(p1.hashCode(), p2.hashCode());
        assertNotEquals(p1, p3);
        assertTrue(p1.toString().contains("READ_PRIVILEGE"));
    }
}
