package com.viglet.turing.spring.security.auth;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.jetbrains.annotations.NotNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.viglet.turing.persistence.model.auth.TurUser;
import com.viglet.turing.persistence.repository.auth.TurGroupRepository;
import com.viglet.turing.persistence.repository.auth.TurPrivilegeRepository;
import com.viglet.turing.persistence.repository.auth.TurRoleRepository;
import com.viglet.turing.persistence.repository.auth.TurUserRepository;
import com.viglet.turing.persistence.repository.dev.token.TurDevTokenRepository;
import com.viglet.turing.properties.TurConfigProperties;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class TurAuthTokenHeaderFilter extends OncePerRequestFilter {
    public static final String KEY = "Key";
    private static final String ROLE_ADMIN = "ROLE_ADMIN";

    private final TurUserRepository turUserRepository;
    private final TurDevTokenRepository turDevTokenRepository;
    private final TurGroupRepository turGroupRepository;
    private final TurRoleRepository turRoleRepository;
    private final TurPrivilegeRepository turPrivilegeRepository;
    private final TurConfigProperties turConfigProperties;

    public TurAuthTokenHeaderFilter(TurUserRepository turUserRepository,
            TurDevTokenRepository turDevTokenRepository,
            TurGroupRepository turGroupRepository,
            TurRoleRepository turRoleRepository,
            TurPrivilegeRepository turPrivilegeRepository,
            TurConfigProperties turConfigProperties) {
        this.turUserRepository = turUserRepository;
        this.turDevTokenRepository = turDevTokenRepository;
        this.turGroupRepository = turGroupRepository;
        this.turRoleRepository = turRoleRepository;
        this.turPrivilegeRepository = turPrivilegeRepository;
        this.turConfigProperties = turConfigProperties;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, @NotNull HttpServletResponse response,
            @NotNull FilterChain filterChain)
            throws ServletException, IOException {
        String appId = request.getHeader(KEY);
        if (appId != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            turDevTokenRepository.findByToken(appId).ifPresent(token -> {
                TurUser turUser = turUserRepository.findByUsername(token.getCreatedBy());
                Collection<GrantedAuthority> authorities = resolveAuthorities(turUser);
                UsernamePasswordAuthenticationToken authenticationToken = new UsernamePasswordAuthenticationToken(
                        turUser, null, authorities);
                authenticationToken.setDetails(
                        new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authenticationToken);
            });
        }
        filterChain.doFilter(request, response);
    }

    private Collection<GrantedAuthority> resolveAuthorities(TurUser turUser) {
        List<GrantedAuthority> authorities = new ArrayList<>();
        if (!turConfigProperties.isPermissions()) {
            authorities.add(new SimpleGrantedAuthority(ROLE_ADMIN));
            turPrivilegeRepository.findAll()
                    .forEach(p -> authorities.add(new SimpleGrantedAuthority(p.getName())));
        } else if (turUser != null) {
            var groups = turGroupRepository.findByTurUsersContaining(turUser);
            for (var group : groups) {
                for (var role : turRoleRepository.findByTurGroupsContaining(group)) {
                    authorities.add(new SimpleGrantedAuthority(role.getName()));
                    for (var privilege : role.getTurPrivileges()) {
                        authorities.add(new SimpleGrantedAuthority(privilege.getName()));
                    }
                }
            }
        }
        return authorities;
    }
}