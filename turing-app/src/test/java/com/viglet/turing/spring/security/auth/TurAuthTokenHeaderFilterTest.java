package com.viglet.turing.spring.security.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import com.viglet.turing.persistence.model.auth.TurUser;
import com.viglet.turing.persistence.model.dev.token.TurDevToken;
import com.viglet.turing.persistence.repository.auth.TurUserRepository;
import com.viglet.turing.persistence.repository.dev.token.TurDevTokenRepository;

@ExtendWith(MockitoExtension.class)
class TurAuthTokenHeaderFilterTest {

    @Mock
    private TurUserRepository turUserRepository;
    @Mock
    private TurDevTokenRepository turDevTokenRepository;

    @InjectMocks
    private TurAuthTokenHeaderFilter filter;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void shouldAuthenticateWhenKeyHeaderIsValidAndNoCurrentAuthentication() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(TurAuthTokenHeaderFilter.KEY, "token-abc");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        TurDevToken token = org.mockito.Mockito.mock(TurDevToken.class);
        when(token.getCreatedBy()).thenReturn("john");
        when(turDevTokenRepository.findByToken("token-abc")).thenReturn(Optional.of(token));

        TurUser user = TurUser.builder().username("john").build();
        when(turUserRepository.findByUsername("john")).thenReturn(user);

        filter.doFilter(request, response, chain);

        var authentication = SecurityContextHolder.getContext().getAuthentication();
        assertEquals(user, authentication.getPrincipal());
        assertEquals(0, authentication.getAuthorities().size());
        verify(turDevTokenRepository).findByToken("token-abc");
        verify(turUserRepository).findByUsername("john");
    }

    @Test
    void shouldSkipWhenHeaderIsMissing() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verifyNoInteractions(turDevTokenRepository);
        verifyNoInteractions(turUserRepository);
    }

    @Test
    void shouldSkipWhenAuthenticationAlreadyExistsInContext() throws Exception {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("existing-user", null));

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(TurAuthTokenHeaderFilter.KEY, "token-abc");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertEquals("existing-user", SecurityContextHolder.getContext().getAuthentication().getPrincipal());
        verifyNoInteractions(turDevTokenRepository);
        verifyNoInteractions(turUserRepository);
    }

    @Test
    void shouldNotAuthenticateWhenTokenIsNotFound() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(TurAuthTokenHeaderFilter.KEY, "token-missing");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        when(turDevTokenRepository.findByToken("token-missing")).thenReturn(Optional.empty());

        filter.doFilter(request, response, chain);

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verify(turDevTokenRepository).findByToken("token-missing");
        verify(turUserRepository, never()).findByUsername(org.mockito.ArgumentMatchers.anyString());
    }
}
