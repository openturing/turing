package com.viglet.turing.spring.security.auth;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

@ExtendWith(MockitoExtension.class)
class TurLogoutHandlerTest {

    private final TurLogoutHandler handler = new TurLogoutHandler();

    @Mock
    private HttpServletRequest request;
    @Mock
    private HttpServletResponse response;
    @Mock
    private Authentication authentication;
    @Mock
    private HttpSession session;

    @Test
    void shouldLogoutAndInvalidateSession() {
        when(request.getSession(false)).thenReturn(session);

        handler.logout(request, response, authentication);

        verify(request).getSession(false);
        verify(session).invalidate();
    }

    @Test
    void shouldNotThrowWhenSessionDoesNotExist() {
        when(request.getSession(false)).thenReturn(null);

        assertDoesNotThrow(() -> handler.logout(request, response, authentication));

        verify(request).getSession(false);
        verify(session, never()).invalidate();
    }
}
