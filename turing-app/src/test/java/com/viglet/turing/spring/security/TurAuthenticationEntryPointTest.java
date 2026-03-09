package com.viglet.turing.spring.security;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.PrintWriter;
import java.io.StringWriter;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

class TurAuthenticationEntryPointTest {

    @Test
    void shouldReturnUnauthorizedJsonResponseOnCommence() throws Exception {
        TurAuthenticationEntryPoint entryPoint = new TurAuthenticationEntryPoint();
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        AuthenticationException authException = new BadCredentialsException("Bad credentials");
        StringWriter responseBody = new StringWriter();
        PrintWriter writer = new PrintWriter(responseBody);

        when(response.getWriter()).thenReturn(writer);

        entryPoint.commence(request, response, authException);
        writer.flush();

        verify(response).setStatus(HttpStatus.UNAUTHORIZED.value());
        verify(response).setContentType(MediaType.APPLICATION_JSON_VALUE);
        assertEquals("{\"error\":\"Unauthorized\"}", responseBody.toString());
    }

    @Test
    void shouldRemainStableWhenCommenceIsCalledMoreThanOnce() {
        TurAuthenticationEntryPoint entryPoint = new TurAuthenticationEntryPoint();
        AuthenticationException authException = new BadCredentialsException("Bad credentials");

        assertDoesNotThrow(() -> {
            HttpServletRequest request = mock(HttpServletRequest.class);
            HttpServletResponse response = mock(HttpServletResponse.class);
            StringWriter responseBody = new StringWriter();
            PrintWriter writer = new PrintWriter(responseBody);

            when(response.getWriter()).thenReturn(writer);
            entryPoint.commence(request, response, authException);
            writer.flush();
            assertEquals("{\"error\":\"Unauthorized\"}", responseBody.toString());
        });

        assertDoesNotThrow(() -> {
            HttpServletRequest request = mock(HttpServletRequest.class);
            HttpServletResponse response = mock(HttpServletResponse.class);
            StringWriter responseBody = new StringWriter();
            PrintWriter writer = new PrintWriter(responseBody);

            when(response.getWriter()).thenReturn(writer);
            entryPoint.commence(request, response, authException);
            writer.flush();
            assertEquals("{\"error\":\"Unauthorized\"}", responseBody.toString());
        });
    }
}
