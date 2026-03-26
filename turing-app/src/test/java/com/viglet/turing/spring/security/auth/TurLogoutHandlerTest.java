package com.viglet.turing.spring.security.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextImpl;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

/**
 * Tests for TurLogoutHandler.
 *
 * @author Alexandre Oliveira
 * @since 2026.1.14
 */
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

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void shouldInvalidateExistingSession() {
        when(request.getSession(false)).thenReturn(session);

        handler.logout(request, response, authentication);

        verify(session).invalidate();
    }

    @Test
    void shouldNotInvalidateWhenNoSession() {
        when(request.getSession(false)).thenReturn(null);

        assertThatNoException().isThrownBy(
                () -> handler.logout(request, response, authentication));

        verify(session, never()).invalidate();
    }

    @Test
    void shouldClearSecurityContext() {
        when(request.getSession(false)).thenReturn(null);
        SecurityContextHolder.setContext(new SecurityContextImpl(authentication));

        handler.logout(request, response, authentication);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void shouldClearSecurityContextEvenWhenSessionExists() {
        when(request.getSession(false)).thenReturn(session);
        SecurityContextHolder.setContext(new SecurityContextImpl(authentication));

        handler.logout(request, response, authentication);

        verify(session).invalidate();
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void shouldHandleNullAuthentication() {
        when(request.getSession(false)).thenReturn(null);

        assertThatNoException().isThrownBy(
                () -> handler.logout(request, response, null));
    }

    @Test
    void shouldRequestSessionWithoutCreating() {
        when(request.getSession(false)).thenReturn(null);

        handler.logout(request, response, authentication);

        verify(request).getSession(false);
    }
}
