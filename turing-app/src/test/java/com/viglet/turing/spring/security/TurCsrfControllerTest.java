package com.viglet.turing.spring.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.security.web.csrf.CsrfToken;

class TurCsrfControllerTest {

    private final TurCsrfController controller = new TurCsrfController();

    @Test
    void shouldReturnTokenHeaderAndParameterName() {
        CsrfToken csrfToken = mock(CsrfToken.class);
        when(csrfToken.getToken()).thenReturn("token-123");
        when(csrfToken.getHeaderName()).thenReturn("X-CSRF-TOKEN");
        when(csrfToken.getParameterName()).thenReturn("_csrf");

        Map<String, String> result = controller.csrf(csrfToken);

        assertNotNull(result);
        assertEquals("token-123", result.get("token"));
        assertEquals("X-CSRF-TOKEN", result.get("headerName"));
        assertEquals("_csrf", result.get("parameterName"));
    }
}
