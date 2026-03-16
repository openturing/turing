package com.viglet.turing.spring.security.auth;

import static org.mockito.Mockito.*;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.context.SecurityContextHolder;

import com.viglet.turing.persistence.repository.auth.TurUserRepository;
import com.viglet.turing.persistence.repository.dev.token.TurDevTokenRepository;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@ExtendWith(MockitoExtension.class)
class TurAuthTokenHeaderFilterTest {

    @Mock
    private TurUserRepository turUserRepository;
    @Mock
    private TurDevTokenRepository turDevTokenRepository;
    @InjectMocks
    private TurAuthTokenHeaderFilter filter;

    @Test
    void shouldContinueFilterChainWhenNoKeyHeader() throws Exception {
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);
        when(request.getHeader("Key")).thenReturn(null);

        filter.doFilterInternal(request, response, chain);

        verify(chain).doFilter(request, response);
        verifyNoInteractions(turDevTokenRepository);
    }

    @Test
    void shouldContinueFilterChainWhenKeyHeaderPresentButTokenNotFound() throws Exception {
        SecurityContextHolder.clearContext();
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);
        when(request.getHeader("Key")).thenReturn("invalid-token");
        when(turDevTokenRepository.findByToken("invalid-token")).thenReturn(Optional.empty());

        filter.doFilterInternal(request, response, chain);

        verify(chain).doFilter(request, response);
    }
}
