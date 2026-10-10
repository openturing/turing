package com.viglet.turing.spring.security.auth;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import com.viglet.turing.persistence.model.auth.TurUser;
import com.viglet.turing.persistence.model.dev.token.TurDevToken;
import com.viglet.turing.persistence.repository.auth.TurUserRepository;
import com.viglet.turing.persistence.repository.dev.token.TurDevTokenRepository;
import com.viglet.turing.spring.security.TurAuthorityResolver;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@ExtendWith(MockitoExtension.class)
class TurAuthTokenHeaderFilterTest {

    @Mock
    private TurUserRepository turUserRepository;
    @Mock
    private TurDevTokenRepository turDevTokenRepository;
    @Mock
    private TurAuthorityResolver turAuthorityResolver;
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

    @Test
    void shouldResolveAuthoritiesThroughTheTransactionalResolver() throws Exception {
        SecurityContextHolder.clearContext();
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);
        TurDevToken token = mock(TurDevToken.class);
        TurUser user = new TurUser();
        user.setUsername("admin");
        when(request.getHeader("Key")).thenReturn("valid-token");
        when(turDevTokenRepository.findByToken("valid-token")).thenReturn(Optional.of(token));
        when(token.isUsable()).thenReturn(true);
        when(token.getCreatedBy()).thenReturn("admin");
        when(turUserRepository.findByUsername("admin")).thenReturn(user);
        doAnswer(invocation -> {
            Set<GrantedAuthority> authorities = invocation.getArgument(1);
            authorities.add(new SimpleGrantedAuthority("SN_IMPORT"));
            return null;
        }).when(turAuthorityResolver).resolve(eq("admin"), anySet());

        try {
            filter.doFilterInternal(request, response, chain);

            var authentication = SecurityContextHolder.getContext().getAuthentication();
            assertSame(user, authentication.getPrincipal());
            assertTrue(authentication.getAuthorities().contains(new SimpleGrantedAuthority("SN_IMPORT")));
            verify(chain).doFilter(request, response);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }
}
