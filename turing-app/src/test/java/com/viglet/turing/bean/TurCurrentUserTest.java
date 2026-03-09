package com.viglet.turing.bean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class TurCurrentUserTest {

    @Test
    void shouldStoreAndExposeAllUserFields() {
        TurCurrentUser user = new TurCurrentUser();
        user.setUsername("john");
        user.setFirstName("John");
        user.setLastName("Doe");
        user.setAdmin(true);
        user.setEmail("john@example.com");

        assertEquals("john", user.getUsername());
        assertEquals("John", user.getFirstName());
        assertEquals("Doe", user.getLastName());
        assertTrue(user.isAdmin());
        assertEquals("john@example.com", user.getEmail());
    }
}
