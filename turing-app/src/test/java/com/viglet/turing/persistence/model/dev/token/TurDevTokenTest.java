package com.viglet.turing.persistence.model.dev.token;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class TurDevTokenTest {

    @Test
    void shouldStoreAndExposeAllFields() {
        TurDevToken token = new TurDevToken();
        token.setId("token-id");
        token.setTitle("My Dev Token");
        token.setDescription("Token for API tests");
        token.setToken("abc123");

        assertEquals("token-id", token.getId());
        assertEquals("My Dev Token", token.getTitle());
        assertEquals("Token for API tests", token.getDescription());
        assertEquals("abc123", token.getToken());
    }
}
