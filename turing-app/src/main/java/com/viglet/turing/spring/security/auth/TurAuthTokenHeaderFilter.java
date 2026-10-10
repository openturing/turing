package com.viglet.turing.spring.security.auth;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

import org.jetbrains.annotations.NotNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.viglet.turing.persistence.model.auth.TurUser;
import com.viglet.turing.persistence.model.dev.token.TurDevToken;
import com.viglet.turing.persistence.repository.auth.TurUserRepository;
import com.viglet.turing.persistence.repository.dev.token.TurDevTokenRepository;
import com.viglet.turing.spring.security.TurAuthorityResolver;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class TurAuthTokenHeaderFilter extends OncePerRequestFilter {
    public static final String KEY = "Key";

    private final TurUserRepository turUserRepository;
    private final TurDevTokenRepository turDevTokenRepository;
    private final TurAuthorityResolver turAuthorityResolver;

    public TurAuthTokenHeaderFilter(TurUserRepository turUserRepository,
            TurDevTokenRepository turDevTokenRepository,
            TurAuthorityResolver turAuthorityResolver) {
        this.turUserRepository = turUserRepository;
        this.turDevTokenRepository = turDevTokenRepository;
        this.turAuthorityResolver = turAuthorityResolver;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, @NotNull HttpServletResponse response,
            @NotNull FilterChain filterChain)
            throws ServletException, IOException {
        String appId = request.getHeader(KEY);
        if (appId != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            // T646 / §XXXVII.8 — reject disabled/expired tokens.
            turDevTokenRepository.findByToken(appId).filter(TurDevToken::isUsable).ifPresent(token -> {
                TurUser turUser = turUserRepository.findByUsername(token.getCreatedBy());
                // T1061 — resolve through TurAuthorityResolver, whose transaction keeps
                // the lazy role → privileges collection loadable. Walking it here, with
                // no session open on a key-only request, threw LazyInitializationException.
                Set<GrantedAuthority> authorities = new HashSet<>();
                turAuthorityResolver.resolve(turUser != null ? turUser.getUsername() : null, authorities);
                UsernamePasswordAuthenticationToken authenticationToken = new UsernamePasswordAuthenticationToken(
                        turUser, null, authorities);
                authenticationToken.setDetails(
                        new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authenticationToken);
            });
        }
        filterChain.doFilter(request, response);
    }
}
