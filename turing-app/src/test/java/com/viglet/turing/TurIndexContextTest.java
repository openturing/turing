package com.viglet.turing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

import java.io.IOException;
import java.security.Principal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;

import jakarta.servlet.http.HttpServletResponse;

class TurIndexContextTest {

    private TurIndexContext turIndexContext;

    @BeforeEach
    void setUp() {
        turIndexContext = new TurIndexContext();
    }

    @Test
    void testIndexWithPrincipal() throws IOException {
        MockHttpServletResponse response = new MockHttpServletResponse();
        Principal principal = mock(Principal.class);

        turIndexContext.index(response, principal);

        assertEquals("/admin", response.getRedirectedUrl());
        assertEquals(HttpServletResponse.SC_MOVED_TEMPORARILY, response.getStatus());
    }

    @Test
    void testIndexWithoutPrincipal() throws IOException {
        MockHttpServletResponse response = new MockHttpServletResponse();

        turIndexContext.index(response, null);

        assertEquals("/login", response.getRedirectedUrl());
        assertEquals(HttpServletResponse.SC_MOVED_TEMPORARILY, response.getStatus());
    }
}
